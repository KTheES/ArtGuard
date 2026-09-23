package com.artworkguard.billing;

import com.artworkguard.billing.BillingDtos.*;
import com.artworkguard.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/billing")
public class BillingController {
 private final BillingService service;
 private final com.artworkguard.redis.RedisWorkGuard guard;
 public BillingController(BillingService service,com.artworkguard.redis.RedisWorkGuard guard){this.service=service;this.guard=guard;}
 @PostMapping("/checkout") @ResponseStatus(HttpStatus.CREATED) @SecurityRequirement(name="bearerAuth")
 public ApiResponse<CheckoutResponse> checkout(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CheckoutRequest request){
  UUID owner=UUID.fromString(jwt.getSubject());guard.rate("checkout",owner,5);
  return ApiResponse.ok(service.checkout(owner,request.planCode(),key));
 }
 @PostMapping("/webhooks/stripe")
 public ApiResponse<WebhookResponse> webhook(@RequestHeader("Stripe-Signature") String signature,@RequestBody byte[] payload){return ApiResponse.ok(service.webhook(payload,signature));}
}
