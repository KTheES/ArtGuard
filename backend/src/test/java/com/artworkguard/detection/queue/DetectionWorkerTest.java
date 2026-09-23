package com.artworkguard.detection.queue;
import com.artworkguard.detection.DetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@SuppressWarnings("unchecked")
class DetectionWorkerTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final DetectionService engine=mock(DetectionService.class);
 final DetectionWorker worker=new DetectionWorker(jdbc,engine);
 final UUID artwork=UUID.randomUUID(),job=UUID.randomUUID(),owner=UUID.randomUUID();
 final DetectionEvent event=DetectionEvent.create(job,artwork,1);
 void setup(String status,int generation){
  when(jdbc.query(anyString(),any(RowMapper.class),eq(job))).thenReturn(List.of(new DetectionWorker.Job(artwork,status,generation,true,100)));
 }
 @Test void duplicateDoesNotRunEngine(){setup("COMPLETED",1);worker.process(event);verifyNoInteractions(engine);}
 @Test void oldGenerationDoesNotRunEngine(){setup("QUEUED",2);worker.process(event);verifyNoInteractions(engine);}
 @Test void disabledMonitoringCancelsJob(){
  setup("QUEUED",1);when(jdbc.query(anyString(),any(RowMapper.class),eq(artwork),eq(true))).thenReturn(List.of());
  worker.process(event);verifyNoInteractions(engine);verify(jdbc).update(contains("CANCELED"),eq(job));
 }
 @Test void successfulRunLinksResult(){
  setup("QUEUED",1);when(jdbc.query(anyString(),any(RowMapper.class),eq(artwork),eq(true))).thenReturn(List.of(owner));
  UUID run=UUID.randomUUID();when(engine.detect(owner,artwork,100)).thenReturn(new DetectionService.RunResponse(run,artwork,1,false,100,.75,.85,.92));
  worker.process(event);verify(jdbc).update(contains("COMPLETED"),eq(run),eq(job));
 }
 @Test void engineFailureIsNotMarkedComplete(){
  setup("QUEUED",1);when(jdbc.query(anyString(),any(RowMapper.class),eq(artwork),eq(true))).thenReturn(List.of(owner));
  when(engine.detect(owner,artwork,100)).thenThrow(new IllegalStateException());
  assertThrows(IllegalStateException.class,()->worker.process(event));verify(jdbc,never()).update(contains("COMPLETED"),any(),any());
 }
 @Test void mismatchedArtworkRejected(){
  when(jdbc.query(anyString(),any(RowMapper.class),eq(job))).thenReturn(List.of(new DetectionWorker.Job(UUID.randomUUID(),"QUEUED",1,true,100)));
  assertThrows(IllegalArgumentException.class,()->worker.process(event));verifyNoInteractions(engine);
 }
}
