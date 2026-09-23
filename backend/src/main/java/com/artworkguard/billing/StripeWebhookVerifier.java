package com.artworkguard.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.net.Webhook;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class StripeWebhookVerifier implements BillingWebhookVerifier {
 private final BillingSettings settings;private final ObjectMapper mapper;
 public StripeWebhookVerifier(BillingSettings settings,ObjectMapper mapper){this.settings=settings;this.mapper=mapper;}
 public VerifiedEvent verify(byte[] payload,String signature){
  try{
   String raw=new String(payload,StandardCharsets.UTF_8);
   var event=Webhook.constructEvent(raw,signature,settings.webhookSecret(),300);
   var root=mapper.readTree(raw);
   return new VerifiedEvent(event.getId(),event.getType(),Instant.ofEpochSecond(event.getCreated()),root.path("data").path("object"));
  }catch(BillingException e){throw e;}catch(Exception e){throw BillingException.invalidWebhook();}
 }
}
