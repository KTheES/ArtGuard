package com.artworkguard.subscription;

import java.util.List;
import java.time.Instant;

public final class SubscriptionDtos {
 private SubscriptionDtos() {}
 public record Plan(String code,String name,int artworkLimit,long scanIntervalHours,int detectionResultLimit,
  int queuePriority,boolean advancedDetection,boolean emailAlert,boolean evidence,boolean reports) {}
 public record Current(Plan plan,String subscribedPlanCode,String status,boolean entitlementsActive,Instant currentPeriodEnd,
  boolean cancelAtPeriodEnd,long activeArtworks,long remainingArtworks) {}
 public record Catalog(List<Plan> plans) {}
}
