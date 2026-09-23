package com.artworkguard.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class BillingSettings {
 private final boolean enabled;private final String secretKey,webhookSecret,creatorPrice,proPrice,businessPrice;private final URI successUrl,cancelUrl;
 public BillingSettings(@Value("${artworkguard.billing.enabled:false}") boolean enabled,@Value("${artworkguard.billing.stripe-secret-key:}") String secretKey,
  @Value("${artworkguard.billing.stripe-webhook-secret:}") String webhookSecret,@Value("${artworkguard.billing.creator-price-id:}") String creatorPrice,
  @Value("${artworkguard.billing.pro-price-id:}") String proPrice,@Value("${artworkguard.billing.business-price-id:}") String businessPrice,
  @Value("${artworkguard.billing.success-url:http://127.0.0.1:3000/billing/success}") URI successUrl,
  @Value("${artworkguard.billing.cancel-url:http://127.0.0.1:3000/billing/cancel}") URI cancelUrl){this.enabled=enabled;this.secretKey=secretKey;this.webhookSecret=webhookSecret;this.creatorPrice=creatorPrice;this.proPrice=proPrice;this.businessPrice=businessPrice;this.successUrl=successUrl;this.cancelUrl=cancelUrl;}
 public String secretKey(){if(!enabled||!secretKey.startsWith("sk_"))throw BillingException.unavailable();return secretKey;}
 public String webhookSecret(){if(!enabled||!webhookSecret.startsWith("whsec_"))throw BillingException.unavailable();return webhookSecret;}
 public URI successUrl(){return successUrl;}public URI cancelUrl(){return cancelUrl;}
 public String price(String plan){String value=switch(plan){case "CREATOR"->creatorPrice;case "PRO"->proPrice;case "BUSINESS"->businessPrice;default->throw BillingException.invalidPlan();};if(value.isBlank())throw BillingException.unavailable();return value;}
}
