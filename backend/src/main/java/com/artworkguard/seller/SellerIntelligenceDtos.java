package com.artworkguard.seller;
import java.time.Instant;
import java.util.*;
public final class SellerIntelligenceDtos {
 private SellerIntelligenceDtos(){}
 public record Item(UUID sellerId,String sellerName,String marketplace,String externalSellerId,
                    long detectionCount,long confirmedCount,long newCount,long productCount,long artworkCount,
                    long creatorCount,long marketplaceCount,int riskScore,Instant firstSeen,Instant lastSeen){}
 public record Page(List<Item> items,int page,int size,long totalElements,long totalPages,
                    String scope,String scoreVersion,String scoreMeaning){}
}
