package com.artworkguard.detection;
import com.artworkguard.marketplace.service.CatalogAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import com.artworkguard.evidence.EvidenceRepository;
import com.artworkguard.notification.NotificationRepository;
@Service
public class DetectionService {
 public record RunResponse(UUID runId,UUID artworkId,int matchedProducts,boolean truncated,int limit,double mediumThreshold,double highThreshold,double criticalThreshold){}
 private final CatalogAccess access;private final DetectionRepository repository;private final DetectionPolicy policy;private final EvidenceRepository evidence;private final NotificationRepository notifications;private final com.artworkguard.subscription.SubscriptionService subscriptions;
 public DetectionService(CatalogAccess access,DetectionRepository repository,DetectionPolicy policy,EvidenceRepository evidence,NotificationRepository notifications,com.artworkguard.subscription.SubscriptionService subscriptions){this.access=access;this.repository=repository;this.policy=policy;this.evidence=evidence;this.notifications=notifications;this.subscriptions=subscriptions;}
 @Transactional(timeout=30)
 public RunResponse detect(UUID owner,UUID artwork,int limit){
  if(limit<1||limit>500)throw new IllegalArgumentException("Detection limit must be between 1 and 500");
  access.active(owner);repository.lockOwnedArtwork(owner,artwork);
  UUID embedding=repository.artworkEmbedding(artwork).orElseThrow(DetectionException::new);
  var entitlement=subscriptions.current(owner).plan();limit=Math.min(limit,entitlement.detectionResultLimit());
  boolean advanced=entitlement.advancedDetection();
  var candidates=advanced?repository.candidates(embedding,policy.medium(),limit+1):repository.basicCandidates(embedding,policy.medium(),limit+1);
  boolean truncated=candidates.size()>limit;var selected=candidates.stream().limit(limit).toList();
  UUID run=UUID.randomUUID();
  repository.saveRun(run,artwork,embedding,policy,limit,selected.size(),truncated);
  for(var candidate:selected){var severity=policy.severity(candidate.similarity());UUID detection=repository.upsert(run,artwork,embedding,candidate,severity);evidence.capture(detection,run,candidate);notifications.enqueue(detection,run,severity);}
  return new RunResponse(run,artwork,selected.size(),truncated,limit,policy.medium(),policy.high(),policy.critical());
 }
}
