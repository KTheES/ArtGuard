package com.artworkguard.product.embedding;
import com.artworkguard.embedding.EmbeddingModel;
import com.artworkguard.marketplace.service.CatalogException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SuppressWarnings("unchecked")
class ProductEmbeddingJobServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 final ProductEmbeddingJobService jobs=new ProductEmbeddingJobService(jdbc,new ObjectMapper().findAndRegisterModules());
 final UUID image=UUID.randomUUID(),job=UUID.randomUUID();final String hash="a".repeat(64);
 void setup(String status){
  when(jdbc.query(anyString(),any(RowMapper.class),eq(image))).thenReturn(List.of(hash));
  when(jdbc.queryForObject(anyString(),any(RowMapper.class),eq(image),eq(hash),eq(EmbeddingModel.ID),eq(EmbeddingModel.VERSION),eq(EmbeddingModel.PREPROCESSING)))
   .thenReturn(new ProductEmbeddingJobService.JobResponse(job,status,2,"FAILED".equals(status)?"AI_PROCESSING_FAILED":null,768));
 }
 @Test void completedImageDoesNotCreateAnotherOutboxEvent(){
  setup("COMPLETED");assertEquals("COMPLETED",jobs.enqueue(image).status());
  verify(jdbc,never()).update(contains("INSERT INTO product_embedding_outbox"),any(),any(),any(),any());
 }
 @Test void failedJobIncrementsGenerationAndQueuesNewEvent(){
  setup("FAILED");var result=jobs.enqueue(image);
  assertEquals(3,result.generation());assertEquals("QUEUED",result.status());assertNull(result.errorCode());
  verify(jdbc).update(contains("INSERT INTO product_embedding_outbox"),any(UUID.class),eq(job),eq(3),contains("PRODUCT_EMBEDDING_REQUESTED"));
 }
 @Test void reactivatedCanceledJobCanBeQueuedAgain(){
  setup("CANCELED");assertEquals(3,jobs.enqueue(image).generation());
 }
 @Test void inactiveOrMissingImageDoesNotWriteJob(){
  when(jdbc.query(anyString(),any(RowMapper.class),eq(image))).thenReturn(List.of());
  assertThrows(CatalogException.class,()->jobs.enqueue(image));
  verify(jdbc,never()).update(contains("INSERT INTO product_embedding_job"),any(),any(),any(),any(),any(),any());
 }
}
