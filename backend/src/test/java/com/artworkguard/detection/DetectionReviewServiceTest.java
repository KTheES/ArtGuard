package com.artworkguard.detection;
import com.artworkguard.detection.DetectionReviewDtos.*;
import com.artworkguard.marketplace.service.CatalogAccess;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class DetectionReviewServiceTest {
 final CatalogAccess access=mock(CatalogAccess.class);final DetectionReviewRepository repository=mock(DetectionReviewRepository.class);
 final DetectionReviewService service=new DetectionReviewService(access,repository);
 final UUID owner=UUID.randomUUID(),id=UUID.randomUUID();
 @Test void inaccessibleDetailReturnsNotFound(){assertThrows(DetectionReviewException.class,()->service.detail(owner,id));verify(access).active(owner);}
 @Test void inaccessibleDetectionCannotBeChanged(){
  assertThrows(DetectionReviewException.class,()->service.update(owner,id,new UpdateRequest(ReviewStatus.CONFIRMED,0L)));
  verify(repository,never()).update(any(),any(),any());
 }
 @Test void staleVersionReturnsConflict(){
  when(repository.detail(owner,id)).thenReturn(Optional.of(mock(Detail.class)));
  var error=assertThrows(DetectionReviewException.class,()->service.update(owner,id,new UpdateRequest(ReviewStatus.CONFIRMED,0L)));
  assertEquals("DETECTION_CONFLICT",error.getCode());
 }
 @Test void successfulUpdateReturnsFreshDetail(){
  var detail=mock(Detail.class);var request=new UpdateRequest(ReviewStatus.DISMISSED,3L);
  when(repository.detail(owner,id)).thenReturn(Optional.of(detail));when(repository.update(owner,id,request)).thenReturn(true);
  assertSame(detail,service.update(owner,id,request));verify(access).active(owner);
 }
 @Test void listPassesOwnerAndFilters(){
  service.list(owner,id,ReviewStatus.NEW,DetectionPolicy.Severity.HIGH,2,10);
  verify(access).active(owner);verify(repository).list(owner,id,ReviewStatus.NEW,DetectionPolicy.Severity.HIGH,2,10);
 }
}
