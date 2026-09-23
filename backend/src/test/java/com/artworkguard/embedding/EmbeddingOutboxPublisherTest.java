package com.artworkguard.embedding;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
@SuppressWarnings("unchecked")
class EmbeddingOutboxPublisherTest {
 JdbcTemplate jdbc=mock(JdbcTemplate.class);
 KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
 UUID eventId=UUID.randomUUID(),jobId=UUID.randomUUID();
 EmbeddingOutboxPublisher publisher=new EmbeddingOutboxPublisher(jdbc,kafka);
 @Test void marksPublishedOnlyAfterKafkaAcknowledges() {
  when(jdbc.query(anyString(),any(RowMapper.class))).thenReturn(List.of(new EmbeddingOutboxPublisher.Pending(eventId,jobId,1,0,"event")));
  when(kafka.send("embedding.requested",jobId.toString(),"event")).thenReturn(CompletableFuture.completedFuture(null));
  publisher.publish();
  var order=inOrder(kafka,jdbc);
  order.verify(kafka).send("embedding.requested",jobId.toString(),"event");
  order.verify(jdbc).update(contains("published_at=now()"),eq(eventId));
 }
 @Test void brokerFailureSchedulesBoundedRetryAndStopsAtTenAttempts() {
  when(jdbc.query(anyString(),any(RowMapper.class))).thenReturn(List.of(new EmbeddingOutboxPublisher.Pending(eventId,jobId,2,9,"event")));
  when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
  publisher.publish();
  verify(jdbc,never()).update(contains("published_at=now()"),eq(eventId));
  verify(jdbc).update(contains("failed=(attempts+1>=10)"),eq(eventId));
  verify(jdbc).update(contains("EVENT_PUBLISH_FAILED"),eq(jobId),eq(2));
 }
}
