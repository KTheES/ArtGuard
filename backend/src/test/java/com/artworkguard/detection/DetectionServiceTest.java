package com.artworkguard.detection;
import com.artworkguard.marketplace.service.CatalogAccess;
import com.artworkguard.artwork.service.ArtworkException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import com.artworkguard.evidence.EvidenceRepository;
import com.artworkguard.notification.NotificationRepository;
class DetectionServiceTest {
 final CatalogAccess access=mock(CatalogAccess.class);final DetectionRepository repository=mock(DetectionRepository.class);
 final EvidenceRepository evidence=mock(EvidenceRepository.class);
 final NotificationRepository notifications=mock(NotificationRepository.class);
 final com.artworkguard.subscription.SubscriptionService subscriptions=mock(com.artworkguard.subscription.SubscriptionService.class);
 final DetectionPolicy policy=new DetectionPolicy(.75,.85,.92);
 final DetectionService service=new DetectionService(access,repository,policy,evidence,notifications,subscriptions);
 final UUID owner=UUID.randomUUID(),artwork=UUID.randomUUID(),embedding=UUID.randomUUID();
 void setup(){when(repository.artworkEmbedding(artwork)).thenReturn(Optional.of(embedding));when(subscriptions.current(owner)).thenReturn(current(true));}
 com.artworkguard.subscription.SubscriptionDtos.Current current(boolean advanced){var p=new com.artworkguard.subscription.SubscriptionDtos.Plan("PRO","Pro",500,6,250,1,advanced,true,true,true);return new com.artworkguard.subscription.SubscriptionDtos.Current(p,"PRO","ACTIVE",true,null,false,1,499);}
 @Test void missingEmbeddingDoesNotWriteRun(){
  assertThrows(DetectionException.class,()->service.detect(owner,artwork,100));
  verify(repository,never()).saveRun(any(),any(),any(),any(),anyInt(),anyInt(),anyBoolean());
 }
 @Test void ownershipIsCheckedBeforeReadingVectors(){
  doThrow(ArtworkException.notFound()).when(repository).lockOwnedArtwork(owner,artwork);
  assertThrows(ArtworkException.class,()->service.detect(owner,artwork,100));
  verify(repository,never()).artworkEmbedding(any());
 }
 @Test void overfetchMarksTruncationAndPersistsOnlyLimit(){
  setup();var first=new DetectionRepository.Candidate(UUID.randomUUID(),UUID.randomUUID(),.99);UUID detectionId=UUID.randomUUID();when(repository.upsert(any(),any(),any(),any(),any())).thenReturn(detectionId);
  when(repository.candidates(embedding,.75,2)).thenReturn(List.of(first,new DetectionRepository.Candidate(UUID.randomUUID(),UUID.randomUUID(),.9)));
  var result=service.detect(owner,artwork,1);
  assertTrue(result.truncated());assertEquals(1,result.matchedProducts());
  verify(repository).upsert(result.runId(),artwork,embedding,first,DetectionPolicy.Severity.CRITICAL);verify(evidence).capture(detectionId,result.runId(),first);verify(notifications).enqueue(detectionId,result.runId(),DetectionPolicy.Severity.CRITICAL);
  verify(repository,times(1)).upsert(any(),any(),any(),any(),any());
 }
 @Test void emptyCatalogRecordsSuccessfulZeroMatchRun(){
  setup();when(repository.candidates(embedding,.75,101)).thenReturn(List.of());
  var result=service.detect(owner,artwork,100);assertEquals(0,result.matchedProducts());assertFalse(result.truncated());
  verify(repository).saveRun(result.runId(),artwork,embedding,policy,100,0,false);
  verifyNoInteractions(evidence,notifications);
 }
 @Test void basicPlanUsesWholeImageDetection(){
  setup();when(subscriptions.current(owner)).thenReturn(current(false));when(repository.basicCandidates(embedding,.75,26)).thenReturn(List.of());
  service.detect(owner,artwork,25);verify(repository).basicCandidates(embedding,.75,26);verify(repository,never()).candidates(any(),anyDouble(),anyInt());
 }
 @Test void invalidLimitDoesNotReadPrivateData(){assertThrows(IllegalArgumentException.class,()->service.detect(owner,artwork,501));verifyNoInteractions(access,repository);}
}
