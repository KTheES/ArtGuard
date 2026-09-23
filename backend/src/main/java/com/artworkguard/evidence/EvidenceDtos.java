package com.artworkguard.evidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public final class EvidenceDtos {
 private EvidenceDtos(){}
 public record Evidence(UUID id,UUID detectionRunId,UUID productId,UUID imageId,String marketplace,String externalProductId,
  String productTitle,String productUrl,String sellerExternalId,String sellerName,String sellerUrl,BigDecimal price,String currency,
  String imageOriginalUrl,String imageSha256,String snapshotSha256,String screenshotSha256,Instant capturedAt){}
}
