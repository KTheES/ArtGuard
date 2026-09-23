package com.artworkguard.detection.queue;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.util.*;
import static org.mockito.Mockito.*;
@SuppressWarnings("unchecked")
class DetectionSignalDispatcherTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final DetectionJobService jobs=mock(DetectionJobService.class);
 final DetectionSignalDispatcher dispatcher=new DetectionSignalDispatcher(jdbc,jobs);
 final UUID signal=UUID.randomUUID(),embedding=UUID.randomUUID();
 @Test void fansOutAtMost100AndKeepsCursor(){
  when(jdbc.query(anyString(),any(RowMapper.class))).thenReturn(List.of(new DetectionSignalDispatcher.Signal(signal,"PRODUCT",embedding,null)));
  var ids=new ArrayList<DetectionSignalDispatcher.Target>();for(int i=0;i<101;i++)ids.add(new DetectionSignalDispatcher.Target(new UUID(0,i+1),25,2));
  when(jdbc.query(contains("LIMIT 101"),any(RowMapper.class),any(Object[].class))).thenReturn(ids);
  dispatcher.dispatch();
  verify(jobs,times(100)).enqueue(any(),eq(25),eq(true),anyString(),eq(2));
  verify(jdbc).update(contains("cursor_artwork_id"),eq(ids.get(99).id()),eq(false),eq(signal));
 }
 @Test void emptyEligibleSetCompletesSignal(){
  when(jdbc.query(anyString(),any(RowMapper.class))).thenReturn(List.of(new DetectionSignalDispatcher.Signal(signal,"ARTWORK",embedding,null)));
  when(jdbc.query(contains("LIMIT 101"),any(RowMapper.class),any(Object[].class))).thenReturn(List.of());
  dispatcher.dispatch();verifyNoInteractions(jobs);
  verify(jdbc).update(contains("cursor_artwork_id"),isNull(),eq(true),eq(signal));
 }
}
