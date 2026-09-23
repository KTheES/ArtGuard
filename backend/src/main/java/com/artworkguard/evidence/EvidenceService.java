package com.artworkguard.evidence;
import com.artworkguard.detection.*;
import com.artworkguard.evidence.EvidenceDtos.Evidence;
import com.artworkguard.marketplace.service.CatalogAccess;
import com.artworkguard.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.util.*;

@Service
public class EvidenceService {
 private final CatalogAccess access;private final DetectionReviewRepository detections;private final EvidenceRepository evidence;private final ObjectStorage storage;private final com.artworkguard.subscription.SubscriptionService subscriptions;
 public EvidenceService(CatalogAccess access,DetectionReviewRepository detections,EvidenceRepository evidence,ObjectStorage storage,com.artworkguard.subscription.SubscriptionService subscriptions){this.access=access;this.detections=detections;this.evidence=evidence;this.storage=storage;this.subscriptions=subscriptions;}
 @Transactional(readOnly=true)
 public List<Evidence> list(UUID owner,UUID detection){owned(owner,detection);return evidence.list(detection);}
 @Transactional(readOnly=true)
 public byte[] screenshot(UUID owner,UUID detection,UUID id){owned(owner,detection);return evidence.screenshot(detection,id).orElseThrow(DetectionReviewException::missing).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
 @Transactional(readOnly=true)
 public byte[] image(UUID owner,UUID detection,UUID id){
  owned(owner,detection);var asset=evidence.asset(detection,id).orElseThrow(DetectionReviewException::missing);
  if(!asset.storageKey().equals("product-images/"+asset.imageId()+"/"+asset.sha256()+".png"))throw DetectionReviewException.missing();
  byte[] bytes=storage.readUpload(asset.storageKey(),"image/png",asset.sizeBytes());
  try{if(!asset.sha256().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))throw DetectionReviewException.missing();}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  return bytes;
 }
 private void owned(UUID owner,UUID detection){access.active(owner);if(detections.detail(owner,detection).isEmpty())throw DetectionReviewException.missing();subscriptions.requireEvidence(owner);}
}
