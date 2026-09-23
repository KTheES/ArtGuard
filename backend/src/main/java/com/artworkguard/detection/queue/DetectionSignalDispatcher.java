package com.artworkguard.detection.queue;
import com.artworkguard.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Component @ConditionalOnProperty(name="artworkguard.detection.enabled",havingValue="true")
public class DetectionSignalDispatcher {
 private final JdbcTemplate jdbc;private final DetectionJobService jobs;
 public DetectionSignalDispatcher(JdbcTemplate jdbc,DetectionJobService jobs){this.jdbc=jdbc;this.jobs=jobs;}
 record Signal(UUID id,String type,UUID embedding,UUID cursor){}
 record Target(UUID id,int resultLimit,int priority){}
 @Scheduled(fixedDelayString="${artworkguard.detection.signal-delay-ms:2000}")
 @Transactional(timeout=30)
 public void dispatch(){
  var signals=jdbc.query("SELECT id,source_type,embedding_id,cursor_artwork_id FROM detection_signal WHERE completed=FALSE ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",
   (rs,n)->new Signal(rs.getObject("id",UUID.class),rs.getString("source_type"),rs.getObject("embedding_id",UUID.class),rs.getObject("cursor_artwork_id",UUID.class)));
  if(signals.isEmpty())return;var signal=signals.getFirst();
  String source="ARTWORK".equals(signal.type())?" AND e.id=?":" AND EXISTS(SELECT 1 FROM current_product_image_embedding p WHERE p.id=? AND p.model=e.model AND p.model_version=e.model_version AND p.preprocessing_version=e.preprocessing_version)";
  var args=new ArrayList<Object>(List.of(EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING,signal.embedding()));
  String cursor="";if(signal.cursor()!=null){cursor=" AND a.id>?";args.add(signal.cursor());}
  var artworks=jdbc.query("""
   SELECT a.id,p.detection_result_limit,p.queue_priority FROM artwork a JOIN app_user u ON u.id=a.user_id
   JOIN user_subscription s ON s.user_id=u.id JOIN subscription_plan p ON p.code=CASE WHEN s.status IN ('TRIAL','ACTIVE') THEN s.plan_code ELSE 'FREE' END JOIN artwork_embedding e ON e.artwork_id=a.id
   WHERE a.deleted=FALSE AND a.monitoring_enabled=TRUE AND u.status='ACTIVE'
   AND e.model=? AND e.model_version=? AND e.preprocessing_version=?
   AND NOT EXISTS(SELECT 1 FROM detection_job j WHERE j.artwork_id=a.id AND j.automatic=TRUE AND j.created_at>=now()-p.scan_interval)
   """+source+cursor+" ORDER BY a.id LIMIT 101",(rs,n)->new Target(rs.getObject(1,UUID.class),rs.getInt(2),rs.getInt(3)),args.toArray());
  var batch=artworks.stream().limit(100).toList();
  for(var target:batch)jobs.enqueue(target.id(),target.resultLimit(),true,"signal:"+signal.id()+":"+target.id(),target.priority());
  UUID last=batch.isEmpty()?signal.cursor():batch.getLast().id();
  jdbc.update("UPDATE detection_signal SET cursor_artwork_id=?,completed=? WHERE id=?",last,artworks.size()<=100,signal.id());
 }
}
