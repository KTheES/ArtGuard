package com.artworkguard.billing;

import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class StripeBillingGateway implements BillingGateway {
 private final BillingSettings settings;
 private final com.artworkguard.common.resilience.CallCircuit circuit=new com.artworkguard.common.resilience.CallCircuit(5,java.time.Duration.ofSeconds(30),java.time.Clock.systemUTC());
 public StripeBillingGateway(BillingSettings settings){this.settings=settings;}
 public Checkout checkout(UUID owner,String email,String plan,String priceId,String idempotencyKey){
  try{
   var metadata=java.util.Map.of("user_id",owner.toString(),"plan_code",plan);
   var params=SessionCreateParams.builder().setMode(SessionCreateParams.Mode.SUBSCRIPTION).setClientReferenceId(owner.toString()).setCustomerEmail(email)
    .setSuccessUrl(settings.successUrl()+"?session_id={CHECKOUT_SESSION_ID}").setCancelUrl(settings.cancelUrl().toString())
    .putAllMetadata(metadata).setSubscriptionData(SessionCreateParams.SubscriptionData.builder().putAllMetadata(metadata).build())
    .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build()).build();
   var options=RequestOptions.builder().setApiKey(settings.secretKey()).setIdempotencyKey(idempotencyKey)
    .setConnectTimeout(3000).setReadTimeout(10000).setMaxNetworkRetries(0).build();
   Session session=circuit.call(()->{
    try{return Session.create(params,options);}catch(com.stripe.exception.StripeException e){throw BillingException.unavailable();}
   });
   if(session.getId()==null||session.getUrl()==null)throw BillingException.unavailable();
   return new Checkout(session.getId(),session.getUrl());
  }catch(BillingException e){throw e;}catch(Exception e){throw BillingException.unavailable();}
 }
}
