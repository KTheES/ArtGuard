package com.artworkguard.embedding;
import com.artworkguard.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@SuppressWarnings("unchecked")
class EmbeddingWorkerTest {
 JdbcTemplate jdbc=mock(JdbcTemplate.class);
 ObjectStorage storage=mock(ObjectStorage.class);
 AiEmbeddingClient ai=mock(AiEmbeddingClient.class);
 EmbeddingWorker worker=new EmbeddingWorker(jdbc,storage,ai);
 UUID jobId=UUID.randomUUID(),artworkId=UUID.randomUUID();
 EmbeddingEvent event=EmbeddingEvent.create(jobId,artworkId,1);
 AiEmbeddingClient.EmbeddingResult result(){float[] vector=new float[768];vector[0]=1;return new AiEmbeddingClient.EmbeddingResult(vector,"phash32-dhash9-luma-v1","0123456789abcdef","fedcba9876543210");}
 @Test void completedDuplicateNeverCallsAi() {
  when(jdbc.query(anyString(),any(RowMapper.class),eq(jobId)))
   .thenReturn(List.of(new EmbeddingWorker.Job(artworkId,"COMPLETED",1)));
  worker.process(event);verifyNoInteractions(storage,ai);
 }
 @Test void oldGenerationNeverOverwritesRetry() {
  when(jdbc.query(anyString(),any(RowMapper.class),eq(jobId)))
   .thenReturn(List.of(new EmbeddingWorker.Job(artworkId,"QUEUED",2)));
  worker.process(event);verifyNoInteractions(storage,ai);
 }
 @Test void deletedArtworkIsCanceledWithoutDownload() {
  when(jdbc.query(anyString(),any(RowMapper.class),eq(jobId)))
   .thenReturn(List.of(new EmbeddingWorker.Job(artworkId,"QUEUED",1)));
  when(jdbc.query(anyString(),any(RowMapper.class),eq(artworkId))).thenReturn(List.of());
  worker.process(event);verifyNoInteractions(storage,ai);
  verify(jdbc).update(contains("CANCELED"),eq(jobId));
 }
 @Test void mismatchedArtworkIsRejected() {
  when(jdbc.query(anyString(),any(RowMapper.class),eq(jobId)))
   .thenReturn(List.of(new EmbeddingWorker.Job(UUID.randomUUID(),"QUEUED",1)));
  assertThatThrownBy(()->worker.process(event)).isInstanceOf(IllegalArgumentException.class);
  verifyNoInteractions(storage,ai);
 }
 @Test void successfulProcessingStoresHashesAndRefreshesDetectionSignal(){
  UUID embeddingId=UUID.randomUUID();
  when(jdbc.query(anyString(),any(RowMapper.class),eq(jobId))).thenReturn((List)List.of(new EmbeddingWorker.Job(artworkId,"QUEUED",1)),(List)List.of(embeddingId));
  when(jdbc.query(anyString(),any(RowMapper.class),eq(artworkId))).thenReturn(List.of("artworks/original.png"));
  when(storage.downloadUrl(anyString(),any())).thenReturn("https://storage.test/private");when(ai.embedWithHashes(anyString())).thenReturn(result());
  when(jdbc.update(contains("INSERT INTO artwork_embedding"),any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
  worker.process(event);
  verify(jdbc).update(contains("DELETE FROM detection_signal"),eq(embeddingId));
  verify(jdbc).update(contains("INSERT INTO detection_signal"),eq(embeddingId));
  verify(jdbc).update(contains("COMPLETED"),eq(jobId));
 }
}
