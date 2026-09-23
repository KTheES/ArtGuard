package com.artworkguard.evidence;
import com.artworkguard.detection.*;
import com.artworkguard.marketplace.service.CatalogAccess;
import com.artworkguard.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceServiceTest {
 final CatalogAccess access=mock(CatalogAccess.class);final DetectionReviewRepository detections=mock(DetectionReviewRepository.class);
 final EvidenceRepository repository=mock(EvidenceRepository.class);final ObjectStorage storage=mock(ObjectStorage.class);
 final com.artworkguard.subscription.SubscriptionService subscriptions=mock(com.artworkguard.subscription.SubscriptionService.class);
 final EvidenceService service=new EvidenceService(access,detections,repository,storage,subscriptions);
 final UUID owner=UUID.randomUUID(),detection=UUID.randomUUID(),evidence=UUID.randomUUID(),image=UUID.randomUUID();
 void owned(){when(detections.detail(owner,detection)).thenReturn(Optional.of(mock(DetectionReviewDtos.Detail.class)));}
 @Test void foreignDetectionCannotReadEvidence(){assertThrows(DetectionReviewException.class,()->service.list(owner,detection));verifyNoInteractions(repository,storage);}
 @Test void screenshotRequiresOwnershipAndReturnsStoredBytes(){owned();when(repository.screenshot(detection,evidence)).thenReturn(Optional.of("<svg/>"));assertArrayEquals("<svg/>".getBytes(java.nio.charset.StandardCharsets.UTF_8),service.screenshot(owner,detection,evidence));}
 @Test void imageIsReadByImmutableKeyAndVerified()throws Exception{
  owned();byte[] bytes={1,2,3};String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));String key="product-images/"+image+"/"+hash+".png";
  when(repository.asset(detection,evidence)).thenReturn(Optional.of(new EvidenceRepository.Asset(image,key,bytes.length,hash)));when(storage.readUpload(key,"image/png",bytes.length)).thenReturn(bytes);
  assertArrayEquals(bytes,service.image(owner,detection,evidence));
 }
 @Test void changedEvidenceImageIsRejected()throws Exception{
  owned();byte[] expected={1},changed={2};String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected));String key="product-images/"+image+"/"+hash+".png";
  when(repository.asset(detection,evidence)).thenReturn(Optional.of(new EvidenceRepository.Asset(image,key,changed.length,hash)));when(storage.readUpload(key,"image/png",changed.length)).thenReturn(changed);
  assertThrows(DetectionReviewException.class,()->service.image(owner,detection,evidence));
 }
}
