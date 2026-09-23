package com.artworkguard.monitoring;
import com.artworkguard.detection.queue.DetectionJobService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
@SuppressWarnings("unchecked")
class ArtworkMonitorSchedulerTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final DetectionJobService jobs=mock(DetectionJobService.class);
 final MonitoringSettings settings=new MonitoringSettings(true,Duration.ofHours(1),2,75);
 final Clock clock=Clock.fixed(Instant.parse("2026-09-09T04:37:42Z"),ZoneOffset.UTC);
 final ArtworkMonitorScheduler scheduler=new ArtworkMonitorScheduler(jdbc,jobs,settings,clock);
 @Test void createsStableTimeBucketAndStopsWhenAnotherNodeOwnsNoCycle(){
  when(jdbc.query(contains("monitoring_scan_cycle WHERE"),any(RowMapper.class))).thenReturn(List.of());scheduler.scan();
  verify(jdbc).update(contains("INSERT INTO monitoring_scan_cycle"),any(UUID.class),eq(java.sql.Timestamp.from(Instant.parse("2026-09-09T04:00:00Z"))));verifyNoInteractions(jobs);
 }
 @Test void processesBoundedBatchAndKeepsCursor(){
  UUID cycle=UUID.randomUUID();var ids=List.of(new ArtworkMonitorScheduler.Target(new UUID(0,1),25,0),new ArtworkMonitorScheduler.Target(new UUID(0,2),100,1),new ArtworkMonitorScheduler.Target(new UUID(0,3),250,2));
  when(jdbc.query(contains("monitoring_scan_cycle WHERE"),any(RowMapper.class))).thenReturn(List.of(new ArtworkMonitorScheduler.Cycle(cycle,null)));
  when(jdbc.query(contains("SELECT a.id,"),any(RowMapper.class),any(Object[].class))).thenReturn(ids);
  scheduler.scan();verify(jobs).enqueue(ids.get(0).id(),25,true,"schedule:"+cycle+":"+ids.get(0).id(),0);verify(jobs).enqueue(ids.get(1).id(),100,true,"schedule:"+cycle+":"+ids.get(1).id(),1);verifyNoMoreInteractions(jobs);
  verify(jdbc).update(contains("scanned_artworks"),eq(ids.get(1).id()),eq(2),eq("RUNNING"),eq(false),eq(cycle));
 }
 @Test void resumedEmptyCycleCompletesWithoutDuplicateJobs(){
  UUID cycle=UUID.randomUUID(),cursor=UUID.randomUUID();when(jdbc.query(contains("monitoring_scan_cycle WHERE"),any(RowMapper.class))).thenReturn(List.of(new ArtworkMonitorScheduler.Cycle(cycle,cursor)));
  when(jdbc.query(contains("SELECT a.id,"),any(RowMapper.class),any(Object[].class))).thenReturn(List.of());scheduler.scan();verifyNoInteractions(jobs);
  verify(jdbc).update(contains("scanned_artworks"),eq(cursor),eq(0),eq("COMPLETED"),eq(true),eq(cycle));
 }
}
