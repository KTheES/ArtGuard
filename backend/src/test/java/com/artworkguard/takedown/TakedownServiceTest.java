package com.artworkguard.takedown;
import com.artworkguard.detection.*;
import com.artworkguard.evidence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.artworkguard.takedown.TakedownDtos.*;

class TakedownServiceTest {
 final DetectionReviewService detections=mock(DetectionReviewService.class);
 final EvidenceService evidence=mock(EvidenceService.class);
 final TakedownRepository repository=mock(TakedownRepository.class);
 final com.artworkguard.auth.verification.EmailVerificationService verification=mock(com.artworkguard.auth.verification.EmailVerificationService.class);
 final TakedownService service=new TakedownService(detections,evidence,repository,new ObjectMapper().findAndRegisterModules(),verification);
 @Test void unverifiedAccountCannotCreateDraft(){
  source(false);doThrow(new IllegalStateException()).when(verification).requireVerified(owner);
  assertThrows(IllegalStateException.class,()->service.create(owner,id,request));verify(repository,never()).create(any(),any(),any(),any());
 }
 final UUID owner=UUID.randomUUID(),id=UUID.randomUUID(),eid=UUID.randomUUID();
 final Create request=new Create(eid,2L,true);
 void source(boolean synthetic){
  var detail=mock(DetectionReviewDtos.Detail.class);var item=mock(DetectionReviewDtos.Item.class);
  when(detections.detail(owner,id)).thenReturn(detail);when(detail.detection()).thenReturn(item);
  when(item.synthetic()).thenReturn(synthetic);when(item.version()).thenReturn(2L);when(item.artworkId()).thenReturn(UUID.randomUUID());
  when(repository.lockConfirmed(owner,id,2)).thenReturn(true);
 }
 Report report(Status status){return new Report(UUID.randomUUID(),id,eid,status,0,null,null,null,null);}
 @Test void ownershipFailureStopsDraft(){
  when(detections.detail(owner,id)).thenThrow(DetectionReviewException.missing());
  assertThrows(DetectionReviewException.class,()->service.create(owner,id,request));verifyNoInteractions(repository,evidence);
 }
 @Test void syntheticCannotCreate(){source(true);assertThrows(TakedownException.class,()->service.create(owner,id,request));verifyNoInteractions(evidence);}
 @Test void explicitRightsConfirmationRequired(){source(false);assertThrows(TakedownException.class,()->service.create(owner,id,new Create(eid,2L,false)));}
 @Test void staleOrUnconfirmedDetectionCannotCreate(){source(false);when(repository.lockConfirmed(owner,id,2)).thenReturn(false);assertThrows(TakedownException.class,()->service.create(owner,id,request));verifyNoInteractions(evidence);}
 @Test void foreignEvidenceCannotCreate(){source(false);when(evidence.list(owner,id)).thenReturn(List.of());assertThrows(TakedownException.class,()->service.create(owner,id,request));verify(repository,never()).create(any(),any(),any(),any());}
 @Test void createsSnapshotAndLinkWithoutSending(){
  source(false);var snapshot=mock(EvidenceDtos.Evidence.class);when(snapshot.id()).thenReturn(eid);when(snapshot.marketplace()).thenReturn("ALIEXPRESS");
  when(evidence.list(owner,id)).thenReturn(List.of(snapshot));var expected=report(Status.DRAFT);
  when(repository.find(owner,id)).thenReturn(Optional.empty(),Optional.of(expected));
  assertSame(expected,service.create(owner,id,request));
  var json=org.mockito.ArgumentCaptor.forClass(String.class);verify(repository).create(eq(owner),eq(id),eq(eid),json.capture());
  assertTrue(json.getValue().contains("https://ipp.alibabagroup.com/"));assertTrue(json.getValue().contains("\"automaticallySubmitted\":false"));
 }
 @Test void repeatedCreationReturnsSameReport(){
  source(false);var snapshot=mock(EvidenceDtos.Evidence.class);when(snapshot.id()).thenReturn(eid);
  when(evidence.list(owner,id)).thenReturn(List.of(snapshot));var expected=report(Status.DRAFT);when(repository.find(owner,id)).thenReturn(Optional.of(expected));
  assertSame(expected,service.create(owner,id,request));verify(repository,never()).create(any(),any(),any(),any());
 }
 @Test void submissionRequiresReceipt(){source(false);when(repository.find(owner,id)).thenReturn(Optional.of(report(Status.DRAFT)));assertThrows(TakedownException.class,()->service.update(owner,id,new Update(Status.SUBMITTED,0L," ")));verify(repository,never()).update(any(),any(),any());}
 @Test void staleTrackingUpdateRejected(){source(false);when(repository.find(owner,id)).thenReturn(Optional.of(report(Status.DRAFT)));assertThrows(TakedownException.class,()->service.update(owner,id,new Update(Status.SUBMITTED,0L,"receipt")));}
 @Test void successfulSubmissionIsManualRecord(){
  source(false);var submitted=report(Status.SUBMITTED);when(repository.find(owner,id)).thenReturn(Optional.of(report(Status.DRAFT)),Optional.of(submitted));
  var update=new Update(Status.SUBMITTED,0L,"receipt");when(repository.update(owner,id,update)).thenReturn(true);
  assertSame(submitted,service.update(owner,id,update));
 }
 @Test void transitionMatrixRejectsReopeningAndSkippingSubmission(){
  int allowed=0;for(var from:Status.values())for(var to:Status.values())if(TakedownService.allowed(from,to))allowed++;
  assertEquals(5,allowed);assertFalse(TakedownService.allowed(Status.DRAFT,Status.RESOLVED));
  assertFalse(TakedownService.allowed(Status.RESOLVED,Status.SUBMITTED));
 }
}
