package com.artworkguard.product.embedding;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
public record ProductEmbeddingEvent(UUID eventId,String eventType,int eventVersion,Instant occurredAt,UUID traceId,Payload payload) {
 public record Payload(UUID jobId,UUID imageId,int generation){}
 public static ProductEmbeddingEvent create(UUID jobId,UUID imageId,int generation) {
  return new ProductEmbeddingEvent(UUID.randomUUID(),"PRODUCT_EMBEDDING_REQUESTED",1,Instant.now(),UUID.randomUUID(),new Payload(jobId,imageId,generation));
 }
 public static ProductEmbeddingEvent parse(ObjectMapper mapper,String json) {
  try {
   var event=mapper.readValue(json,ProductEmbeddingEvent.class);
   if(event.eventId()==null || event.traceId()==null || event.occurredAt()==null || event.eventVersion()!=1
    || !"PRODUCT_EMBEDDING_REQUESTED".equals(event.eventType()) || event.payload()==null || event.payload().jobId()==null
    || event.payload().imageId()==null || event.payload().generation()<1)throw new IllegalArgumentException();
   return event;
  } catch(Exception e){throw new IllegalArgumentException("Invalid embedding event");}
 }
}
