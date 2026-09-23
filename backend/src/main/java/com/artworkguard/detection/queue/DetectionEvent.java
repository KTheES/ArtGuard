package com.artworkguard.detection.queue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
public record DetectionEvent(UUID eventId,String eventType,int eventVersion,Instant occurredAt,UUID traceId,Payload payload) {
 public record Payload(UUID jobId,UUID artworkId,int generation){}
 public static DetectionEvent create(UUID jobId,UUID artworkId,int generation) {
  return new DetectionEvent(UUID.randomUUID(),"DETECTION_REQUESTED",1,Instant.now(),UUID.randomUUID(),new Payload(jobId,artworkId,generation));
 }
 public static DetectionEvent parse(ObjectMapper mapper,String json) {
  try {
   var event=mapper.readValue(json,DetectionEvent.class);
   if(event.eventId()==null || event.traceId()==null || event.occurredAt()==null || event.eventVersion()!=1
    || !"DETECTION_REQUESTED".equals(event.eventType()) || event.payload()==null || event.payload().jobId()==null
    || event.payload().artworkId()==null || event.payload().generation()<1)throw new IllegalArgumentException();
   return event;
  } catch(Exception e){throw new IllegalArgumentException("Invalid embedding event");}
 }
}
