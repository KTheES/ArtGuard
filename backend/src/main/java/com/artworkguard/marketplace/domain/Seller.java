package com.artworkguard.marketplace.domain;
import java.time.Instant;
import java.util.UUID;
public record Seller(UUID id,UUID marketplaceId,String externalSellerId,String name,String url,Instant firstSeenAt,Instant lastSeenAt){}
