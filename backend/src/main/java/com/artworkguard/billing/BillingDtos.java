package com.artworkguard.billing;

import jakarta.validation.constraints.Pattern;

public final class BillingDtos {
 private BillingDtos() {}
 public record CheckoutRequest(@jakarta.validation.constraints.NotNull @Pattern(regexp="CREATOR|PRO|BUSINESS") String planCode) {}
 public record CheckoutResponse(String sessionId,String url) {}
 public record WebhookResponse(String outcome) {}
}
