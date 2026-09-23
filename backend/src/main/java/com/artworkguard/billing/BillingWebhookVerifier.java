package com.artworkguard.billing;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public interface BillingWebhookVerifier {
 record VerifiedEvent(String id,String type,Instant createdAt,JsonNode object) {}
 VerifiedEvent verify(byte[] payload,String signature);
}
