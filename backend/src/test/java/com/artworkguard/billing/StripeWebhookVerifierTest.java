package com.artworkguard.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class StripeWebhookVerifierTest {
 final String secret="whsec_unit_test";
 final BillingSettings settings=new BillingSettings(true,"sk_test_secret",secret,"pc","pp","pb",URI.create("http://localhost/s"),URI.create("http://localhost/c"));
 final StripeWebhookVerifier verifier=new StripeWebhookVerifier(settings,new ObjectMapper());
 @Test void verifiesRawBodyAndRejectsMutation()throws Exception{
  long timestamp=Instant.now().getEpochSecond();String json="{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"customer.subscription.updated\",\"created\":"+timestamp+",\"data\":{\"object\":{\"id\":\"sub_1\"}}}";
  String header="t="+timestamp+",v1="+signature(timestamp+"."+json);
  assertEquals("evt_1",verifier.verify(json.getBytes(StandardCharsets.UTF_8),header).id());
  assertThrows(BillingException.class,()->verifier.verify((json+" ").getBytes(StandardCharsets.UTF_8),header));
 }
 private String signature(String value)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
}
