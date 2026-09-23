package com.artworkguard.product.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record Product(UUID id,UUID marketplaceId,UUID sellerId,String externalProductId,String title,String url,BigDecimal price,String currency,String status,Instant firstSeenAt,Instant lastSeenAt){}
