package com.artworkguard.detection.queue;
import com.artworkguard.detection.*;
import com.artworkguard.marketplace.service.CatalogAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@SuppressWarnings("unchecked")
class DetectionJobServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final CatalogAccess access=mock(CatalogAccess.class);final DetectionRepository repository=mock(DetectionRepository.class);
 final com.artworkguard.subscription.SubscriptionService subscriptions=mock(com.artworkguard.subscription.SubscriptionService.class);
 final DetectionJobService service=new DetectionJobService(jdbc,new ObjectMapper().findAndRegisterModules(),access,repository,mock(com.artworkguard.redis.RedisWorkGuard.class),subscriptions);
 final UUID owner=UUID.randomUUID(),artwork=UUID.randomUUID(),job=UUID.randomUUID();
 @Test void missingEmbeddingDoesNotQueue(){
  assertThrows(DetectionException.class,()->service.request(owner,artwork,100));verifyNoInteractions(jdbc);
 }
 @Test void foreignJobCannotBeRead(){assertThrows(DetectionReviewException.class,()->service.status(owner,artwork,job));}
 @Test void manualResultLimitIsCappedByPlan(){
  when(repository.artworkEmbedding(artwork)).thenReturn(Optional.of(UUID.randomUUID()));when(subscriptions.allowedDetectionLimit(owner,500)).thenReturn(25);
  when(jdbc.update(contains("INSERT INTO detection_job"),any(),eq(artwork),anyString(),eq(false),eq(25),eq(0))).thenReturn(1);
  assertEquals(25,service.request(owner,artwork,500).status().equals("QUEUED")?25:0);
  verify(jdbc).update(contains("INSERT INTO detection_job"),any(),eq(artwork),anyString(),eq(false),eq(25),eq(0));
 }
 @Test void failedRetryCreatesNewGeneration(){
  when(jdbc.query(anyString(),any(RowMapper.class),eq(job),eq(artwork),eq(owner))).thenReturn(List.of(new DetectionJobService.JobResponse(job,"FAILED",2,"FAILED",null)));
  var result=service.retry(owner,artwork,job);assertEquals(3,result.generation());assertEquals("QUEUED",result.status());
  verify(jdbc).update(contains("INSERT INTO detection_outbox"),any(UUID.class),eq(job),eq(3),contains("DETECTION_REQUESTED"));
 }
 @Test void completedRetryDoesNotQueue(){
  when(jdbc.query(anyString(),any(RowMapper.class),eq(job),eq(artwork),eq(owner))).thenReturn(List.of(new DetectionJobService.JobResponse(job,"COMPLETED",1,null,UUID.randomUUID())));
  assertEquals("COMPLETED",service.retry(owner,artwork,job).status());
  verify(jdbc,never()).update(contains("INSERT INTO detection_outbox"),any(),any(),any(),any());
 }
}
