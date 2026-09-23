package com.artworkguard.billing;

import java.util.UUID;

public interface BillingGateway {
 record Checkout(String sessionId,String url) {}
 Checkout checkout(UUID owner,String email,String plan,String priceId,String idempotencyKey);
}
