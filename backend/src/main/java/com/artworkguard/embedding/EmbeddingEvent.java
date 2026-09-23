package com.artworkguard.embedding;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
public record EmbeddingEvent(UUID eventId,String eventType,int eventVersion,Instant occurredAt,UUID traceId,Payload payload) {
 public record Payload(UUID jobId,UUID artworkId,int generation){}
 public static EmbeddingEvent create(UUID jobId,UUID artworkId,int generation) {
  return new EmbeddingEvent(UUID.randomUUID(),"EMBEDDING_REQUESTED",1,Instant.now(),UUID.randomUUID(),new Payload(jobId,artworkId,generation));
 }
 public static EmbeddingEvent parse(ObjectMapper mapper,String json) {
  try {
   var event=mapper.readValue(json,EmbeddingEvent.class);
   if(event.eventId()==null || event.traceId()==null || event.occurredAt()==null || event.eventVersion()!=1
    || !"EMBEDDING_REQUESTED".equals(event.eventType()) || event.payload()==null || event.payload().jobId()==null
    || event.payload().artworkId()==null || event.payload().generation()<1)throw new IllegalArgumentException();
   return event;
  } catch(Exception e){throw new IllegalArgumentException("Invalid embedding event");}
 }
}
