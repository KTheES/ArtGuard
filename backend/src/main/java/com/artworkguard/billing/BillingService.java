package com.artworkguard.billing;

import com.artworkguard.auth.service.AuthException;
import com.artworkguard.billing.BillingDtos.*;
import com.artworkguard.billing.BillingWebhookVerifier.VerifiedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class BillingService {
 private final JdbcTemplate jdbc;private final BillingSettings settings;private final BillingGateway gateway;private final BillingWebhookVerifier verifier;
 public BillingService(JdbcTemplate jdbc,BillingSettings settings,BillingGateway gateway,BillingWebhookVerifier verifier){this.jdbc=jdbc;this.settings=settings;this.gateway=gateway;this.verifier=verifier;}
 record CheckoutRow(UUID id,String plan,String sessionId,String url) {}
 record SubscriptionRow(UUID user,String plan,String customer,String subscription,Instant lastEvent) {}

 @Transactional(timeout=30)
 public CheckoutResponse checkout(UUID owner,String plan,String requestKey){
  if(requestKey==null||!requestKey.matches("[A-Za-z0-9:_-]{8,100}"))throw BillingException.invalidKey();
  String price=settings.price(plan);
  String email=jdbc.query("SELECT email FROM app_user WHERE id=? AND status='ACTIVE'",(r,n)->r.getString(1),owner).stream().findFirst().orElseThrow(AuthException::unauthorized);
  UUID id=UUID.randomUUID();
  jdbc.update("INSERT INTO billing_checkout_request(id,user_id,plan_code,idempotency_key,provider) VALUES(?,?,?,?,'STRIPE') ON CONFLICT(user_id,idempotency_key) DO NOTHING",id,owner,plan,requestKey);
  var row=jdbc.queryForObject("SELECT id,plan_code,provider_session_id,checkout_url FROM billing_checkout_request WHERE user_id=? AND idempotency_key=? FOR UPDATE",
   (r,n)->new CheckoutRow(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4)),owner,requestKey);
  if(!row.plan().equals(plan))throw BillingException.conflict();
  if(row.sessionId()!=null)return new CheckoutResponse(row.sessionId(),row.url());
  var session=gateway.checkout(owner,email,plan,price,"artworkguard:"+owner+":"+requestKey);
  jdbc.update("UPDATE billing_checkout_request SET provider_session_id=?,checkout_url=?,completed_at=now() WHERE id=?",session.sessionId(),session.url(),row.id());
  return new CheckoutResponse(session.sessionId(),session.url());
 }

 @Transactional(timeout=15)
 public WebhookResponse webhook(byte[] payload,String signature){
  VerifiedEvent event=verifier.verify(payload,signature);String hash=sha(payload);
  int inserted=jdbc.update("INSERT INTO billing_webhook_event(provider,external_event_id,event_type,payload_sha256,outcome) VALUES('STRIPE',?,?,?,'PROCESSING') ON CONFLICT DO NOTHING",event.id(),event.type(),hash);
  if(inserted==0){String existing=jdbc.queryForObject("SELECT payload_sha256 FROM billing_webhook_event WHERE provider='STRIPE' AND external_event_id=?",String.class,event.id());if(!hash.equals(existing))throw BillingException.invalidWebhook();return new WebhookResponse("DUPLICATE");}
  boolean processed=switch(event.type()){
   case "checkout.session.completed"->{bindCheckout(event.object());yield true;}
   case "customer.subscription.created","customer.subscription.updated","customer.subscription.deleted"->applySubscription(event);
   default->false;
  };
  String outcome=processed?"PROCESSED":"IGNORED";
  jdbc.update("UPDATE billing_webhook_event SET outcome=?,processed_at=now() WHERE provider='STRIPE' AND external_event_id=?",outcome,event.id());
  return new WebhookResponse(outcome);
 }

 private void bindCheckout(JsonNode object){
  UUID user=user(object);String customer=text(object,"customer"),subscription=text(object,"subscription");
  SubscriptionRow row=lock(user);verifyIdentity(row,customer,subscription);
  jdbc.update("UPDATE user_subscription SET provider='STRIPE',provider_customer_id=COALESCE(?,provider_customer_id),provider_subscription_id=COALESCE(?,provider_subscription_id),updated_at=now(),version=version+1 WHERE user_id=?",customer,subscription,user);
 }

 private boolean applySubscription(VerifiedEvent event){
  JsonNode object=event.object();UUID user=user(object);String customer=text(object,"customer"),subscription=text(object,"id");
  SubscriptionRow row=lock(user);verifyIdentity(row,customer,subscription);
  if(row.lastEvent()!=null&&event.createdAt().isBefore(row.lastEvent()))return false;
  String plan=text(object.path("metadata"),"plan_code");if(plan==null)plan=row.plan();
  if(!List.of("CREATOR","PRO","BUSINESS").contains(plan)||Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM subscription_plan WHERE code=?)",Boolean.class,plan)))throw BillingException.invalidWebhook();
  String status="customer.subscription.deleted".equals(event.type())?"CANCELED":status(text(object,"status"));
  Long period=object.path("current_period_end").canConvertToLong()?object.path("current_period_end").longValue():null;
  boolean cancel=object.path("cancel_at_period_end").asBoolean(false);
  jdbc.update("""
   UPDATE user_subscription SET plan_code=?,status=?,provider='STRIPE',provider_customer_id=?,provider_subscription_id=?,
    current_period_end=?,cancel_at_period_end=?,provider_event_created_at=?,updated_at=now(),version=version+1 WHERE user_id=?
   """,plan,status,customer,subscription,period==null?null:Timestamp.from(Instant.ofEpochSecond(period)),cancel,Timestamp.from(event.createdAt()),user);
  return true;
 }

 private SubscriptionRow lock(UUID user){return jdbc.query("SELECT user_id,plan_code,provider_customer_id,provider_subscription_id,provider_event_created_at FROM user_subscription WHERE user_id=? FOR UPDATE",
  (r,n)->new SubscriptionRow(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant()),user).stream().findFirst().orElseThrow(BillingException::invalidWebhook);}
 private void verifyIdentity(SubscriptionRow row,String customer,String subscription){if(customer==null||subscription==null||(row.customer()!=null&&!row.customer().equals(customer))||(row.subscription()!=null&&!row.subscription().equals(subscription)))throw BillingException.invalidWebhook();}
 private UUID user(JsonNode object){try{return UUID.fromString(text(object.path("metadata"),"user_id"));}catch(Exception e){throw BillingException.invalidWebhook();}}
 private String status(String stripe){return switch(stripe==null?"":stripe){case "trialing"->"TRIAL";case "active"->"ACTIVE";case "past_due","incomplete"->"PAST_DUE";case "canceled","unpaid","incomplete_expired","paused"->"CANCELED";default->throw BillingException.invalidWebhook();};}
 private String text(JsonNode node,String field){JsonNode value=node.path(field);return value.isTextual()&&!value.textValue().isBlank()?value.textValue():null;}
 private String sha(byte[] payload){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));}catch(Exception e){throw new IllegalStateException(e);}}
}
