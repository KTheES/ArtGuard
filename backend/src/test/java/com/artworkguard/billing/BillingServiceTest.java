package com.artworkguard.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class BillingServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final BillingSettings settings=mock(BillingSettings.class);final BillingGateway gateway=mock(BillingGateway.class);final BillingWebhookVerifier verifier=mock(BillingWebhookVerifier.class);
 final BillingService service=new BillingService(jdbc,settings,gateway,verifier);final UUID owner=UUID.randomUUID();
 @Test void checkoutPersistsProviderResultAndUsesStableProviderKey(){
  when(settings.price("PRO")).thenReturn("price_pro");when(jdbc.query(contains("SELECT email"),any(RowMapper.class),eq(owner))).thenReturn(List.of("owner@example.com"));
  UUID rowId=UUID.randomUUID();when(jdbc.queryForObject(contains("billing_checkout_request WHERE"),any(RowMapper.class),eq(owner),eq("request-123"))).thenReturn(new BillingService.CheckoutRow(rowId,"PRO",null,null));
  when(gateway.checkout(owner,"owner@example.com","PRO","price_pro","artworkguard:"+owner+":request-123")).thenReturn(new BillingGateway.Checkout("cs_1","https://checkout.stripe.com/c/pay/cs_1"));
  var result=service.checkout(owner,"PRO","request-123");assertEquals("cs_1",result.sessionId());verify(jdbc).update(contains("completed_at=now()"),eq("cs_1"),contains("checkout.stripe.com"),eq(rowId));
 }
 @Test void completedCheckoutRetryDoesNotCallProvider(){
  when(settings.price("PRO")).thenReturn("price_pro");when(jdbc.query(contains("SELECT email"),any(RowMapper.class),eq(owner))).thenReturn(List.of("owner@example.com"));
  when(jdbc.queryForObject(contains("billing_checkout_request WHERE"),any(RowMapper.class),eq(owner),eq("request-123"))).thenReturn(new BillingService.CheckoutRow(UUID.randomUUID(),"PRO","cs_1","https://checkout.stripe.com/c/pay/cs_1"));
  assertEquals("cs_1",service.checkout(owner,"PRO","request-123").sessionId());verifyNoInteractions(gateway);
 }
 @Test void duplicateWebhookIsAcknowledgedWithoutReprocessing()throws Exception{
  byte[] payload="{}".getBytes(StandardCharsets.UTF_8);var event=new BillingWebhookVerifier.VerifiedEvent("evt_1","ignored.event",Instant.now(),new ObjectMapper().createObjectNode());when(verifier.verify(payload,"sig")).thenReturn(event);when(jdbc.update(startsWith("INSERT INTO billing_webhook_event"),eq("evt_1"),eq("ignored.event"),anyString())).thenReturn(0);
  when(jdbc.queryForObject(contains("payload_sha256"),eq(String.class),eq("evt_1"))).thenReturn(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)));
  assertEquals("DUPLICATE",service.webhook(payload,"sig").outcome());verify(jdbc,never()).update(startsWith("UPDATE user_subscription"),any(Object[].class));
 }
 @Test void subscriptionEventUpdatesPlanAndStateOnce()throws Exception{
  var object=new ObjectMapper().readTree("{\"id\":\"sub_1\",\"customer\":\"cus_1\",\"status\":\"trialing\",\"current_period_end\":1800000000,\"cancel_at_period_end\":false,\"metadata\":{\"user_id\":\""+owner+"\",\"plan_code\":\"PRO\"}}");
  byte[] payload="event".getBytes();var event=new BillingWebhookVerifier.VerifiedEvent("evt_2","customer.subscription.updated",Instant.parse("2026-09-09T00:00:00Z"),object);when(verifier.verify(payload,"sig")).thenReturn(event);when(jdbc.update(startsWith("INSERT INTO billing_webhook_event"),any(),any(),any())).thenReturn(1);
  when(jdbc.query(contains("FROM user_subscription WHERE"),any(RowMapper.class),eq(owner))).thenReturn(List.of(new BillingService.SubscriptionRow(owner,"FREE",null,null,null)));when(jdbc.queryForObject(contains("SELECT EXISTS"),eq(Boolean.class),eq("PRO"))).thenReturn(true);
  assertEquals("PROCESSED",service.webhook(payload,"sig").outcome());verify(jdbc).update(startsWith("UPDATE user_subscription SET plan_code"),eq("PRO"),eq("TRIAL"),eq("cus_1"),eq("sub_1"),any(),eq(false),any(),eq(owner));
 }
 @Test void olderSubscriptionEventCannotRollBackState()throws Exception{
  var object=new ObjectMapper().readTree("{\"id\":\"sub_1\",\"customer\":\"cus_1\",\"status\":\"past_due\",\"metadata\":{\"user_id\":\""+owner+"\",\"plan_code\":\"PRO\"}}");
  byte[] payload="older".getBytes();var event=new BillingWebhookVerifier.VerifiedEvent("evt_old","customer.subscription.updated",Instant.parse("2026-09-09T00:00:00Z"),object);when(verifier.verify(payload,"sig")).thenReturn(event);when(jdbc.update(startsWith("INSERT INTO billing_webhook_event"),any(),any(),any())).thenReturn(1);
  when(jdbc.query(contains("FROM user_subscription WHERE"),any(RowMapper.class),eq(owner))).thenReturn(List.of(new BillingService.SubscriptionRow(owner,"PRO","cus_1","sub_1",Instant.parse("2026-09-10T00:00:00Z"))));
  assertEquals("IGNORED",service.webhook(payload,"sig").outcome());verify(jdbc,never()).update(startsWith("UPDATE user_subscription"),any(Object[].class));
 }
}
