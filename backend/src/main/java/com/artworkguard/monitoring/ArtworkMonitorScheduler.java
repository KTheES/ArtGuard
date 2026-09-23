package com.artworkguard.monitoring;
import com.artworkguard.detection.queue.DetectionJobService;
import com.artworkguard.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Component
@ConditionalOnProperty(name="artworkguard.monitor.enabled",havingValue="true")
public class ArtworkMonitorScheduler {
 private final JdbcTemplate jdbc;private final DetectionJobService jobs;private final MonitoringSettings settings;private final Clock clock;
 public ArtworkMonitorScheduler(JdbcTemplate jdbc,DetectionJobService jobs,MonitoringSettings settings,Clock clock){this.jdbc=jdbc;this.jobs=jobs;this.settings=settings;this.clock=clock;}
 record Cycle(UUID id,UUID cursor){}
 record Target(UUID id,int resultLimit,int priority){}
 @Scheduled(fixedDelayString="${artworkguard.monitor.tick-delay-ms:5000}")
 @Transactional(timeout=30)
 public void scan(){
  long seconds=settings.interval().toSeconds();long bucket=Math.floorDiv(clock.instant().getEpochSecond(),seconds)*seconds;
  UUID proposed=UUID.randomUUID();
  jdbc.update("INSERT INTO monitoring_scan_cycle(id,window_start) VALUES(?,?) ON CONFLICT(window_start) DO NOTHING",proposed,java.sql.Timestamp.from(Instant.ofEpochSecond(bucket)));
  var cycles=jdbc.query("SELECT id,cursor_artwork_id FROM monitoring_scan_cycle WHERE status='RUNNING' ORDER BY window_start,id LIMIT 1 FOR UPDATE SKIP LOCKED",
   (r,n)->new Cycle(r.getObject("id",UUID.class),r.getObject("cursor_artwork_id",UUID.class)));
  if(cycles.isEmpty())return;var cycle=cycles.getFirst();
  var args=new ArrayList<Object>(List.of(EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING));String cursor="";
  if(cycle.cursor()!=null){cursor=" AND a.id>?";args.add(cycle.cursor());}args.add(settings.batchSize()+1);
  var artworks=jdbc.query("""
   SELECT a.id,LEAST(p.detection_result_limit,?) result_limit,p.queue_priority FROM artwork a JOIN app_user u ON u.id=a.user_id
   JOIN user_subscription s ON s.user_id=u.id JOIN subscription_plan p ON p.code=CASE WHEN s.status IN ('TRIAL','ACTIVE') THEN s.plan_code ELSE 'FREE' END
   JOIN artwork_embedding e ON e.artwork_id=a.id AND e.model=? AND e.model_version=? AND e.preprocessing_version=?
   WHERE a.deleted=FALSE AND a.monitoring_enabled=TRUE AND u.status='ACTIVE'
   AND NOT EXISTS(SELECT 1 FROM detection_job j WHERE j.artwork_id=a.id AND j.automatic=TRUE AND j.created_at>=now()-p.scan_interval)
   """+cursor+" ORDER BY a.id LIMIT ?",(r,n)->new Target(r.getObject(1,UUID.class),r.getInt(2),r.getInt(3)),prepend(settings.resultLimit(),args));
  var batch=artworks.stream().limit(settings.batchSize()).toList();
  for(var target:batch)jobs.enqueue(target.id(),target.resultLimit(),true,"schedule:"+cycle.id()+":"+target.id(),target.priority());
  UUID last=batch.isEmpty()?cycle.cursor():batch.getLast().id();boolean completed=artworks.size()<=settings.batchSize();
  jdbc.update("UPDATE monitoring_scan_cycle SET cursor_artwork_id=?,scanned_artworks=scanned_artworks+?,status=?,completed_at=CASE WHEN ? THEN now() ELSE NULL END WHERE id=?",
   last,batch.size(),completed?"COMPLETED":"RUNNING",completed,cycle.id());
 }
 private Object[] prepend(Object value,List<Object> rest){var all=new ArrayList<Object>();all.add(value);all.addAll(rest);return all.toArray();}
}
