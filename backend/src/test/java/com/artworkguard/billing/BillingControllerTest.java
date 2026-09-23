package com.artworkguard.billing;

import com.artworkguard.auth.jwt.JwtConfig;
import com.artworkguard.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillingController.class) @Import({SecurityConfig.class,JwtConfig.class})
class BillingControllerTest {
 @Autowired MockMvc mvc;@MockitoBean BillingService service;
 @MockitoBean com.artworkguard.redis.RedisWorkGuard guard;
 @Test void missingPlanIsRejected()throws Exception{
  mvc.perform(post("/api/v1/billing/checkout").with(jwt()).header("Idempotency-Key","request-123")
   .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
  verifyNoInteractions(service,guard);
 }
 @Test void rateLimitPreventsCheckout()throws Exception{
  UUID owner=UUID.randomUUID();
  doThrow(new com.artworkguard.redis.RedisGuardException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED",30))
   .when(guard).rate("checkout",owner,5);
  mvc.perform(post("/api/v1/billing/checkout").with(jwt().jwt(b->b.subject(owner.toString())))
   .header("Idempotency-Key","request-123").contentType("application/json").content("{\"planCode\":\"PRO\"}"))
   .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","30"));
  verifyNoInteractions(service);
 }
 @DynamicPropertySource static void secret(DynamicPropertyRegistry r){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(b));}
 @Test void checkoutRequiresAuthentication()throws Exception{mvc.perform(post("/api/v1/billing/checkout").header("Idempotency-Key","request-123").contentType("application/json").content("{\"planCode\":\"PRO\"}")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @Test void checkoutUsesJwtOwnerAndIdempotencyKey()throws Exception{UUID owner=UUID.randomUUID();when(service.checkout(owner,"PRO","request-123")).thenReturn(new BillingDtos.CheckoutResponse("cs_1","https://checkout.stripe.com/c/pay/cs_1"));mvc.perform(post("/api/v1/billing/checkout").with(jwt().jwt(b->b.subject(owner.toString()))).header("Idempotency-Key","request-123").contentType("application/json").content("{\"planCode\":\"PRO\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.data.sessionId").value("cs_1"));}
 @Test void stripeWebhookIsPublicButRequiresSignature()throws Exception{when(service.webhook(any(),eq("signature"))).thenReturn(new BillingDtos.WebhookResponse("PROCESSED"));mvc.perform(post("/api/v1/billing/webhooks/stripe").header("Stripe-Signature","signature").contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.outcome").value("PROCESSED"));}
}
