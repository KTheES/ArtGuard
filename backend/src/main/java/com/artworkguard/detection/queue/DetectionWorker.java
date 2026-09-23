package com.artworkguard.detection.queue;
import com.artworkguard.detection.DetectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @ConditionalOnProperty(name="artworkguard.detection.enabled",havingValue="true")
public class DetectionWorker {
 private final JdbcTemplate jdbc;private final DetectionService engine;
 public DetectionWorker(JdbcTemplate jdbc,DetectionService engine){this.jdbc=jdbc;this.engine=engine;}
 record Job(UUID artwork,String status,int generation,boolean automatic,int limit){}
 @Transactional(timeout=45)
 public void process(DetectionEvent event){
  var p=event.payload();
  var rows=jdbc.query("SELECT artwork_id,status,generation,automatic,result_limit FROM detection_job WHERE id=? FOR UPDATE",
   (rs,n)->new Job(rs.getObject("artwork_id",UUID.class),rs.getString("status"),rs.getInt("generation"),rs.getBoolean("automatic"),rs.getInt("result_limit")),p.jobId());
  if(rows.isEmpty())return;var job=rows.getFirst();
  if(!"QUEUED".equals(job.status())||job.generation()!=p.generation())return;
  if(!job.artwork().equals(p.artworkId()))throw new IllegalArgumentException("Detection artwork mismatch");
  var owners=jdbc.query("""
   SELECT a.user_id FROM artwork a JOIN app_user u ON u.id=a.user_id
   WHERE a.id=? AND a.deleted=FALSE AND u.status='ACTIVE' AND (?=FALSE OR a.monitoring_enabled=TRUE)
   FOR UPDATE OF a
   """,(rs,n)->rs.getObject(1,UUID.class),job.artwork(),job.automatic());
  if(owners.isEmpty()){jdbc.update("UPDATE detection_job SET status='CANCELED',updated_at=now() WHERE id=?",p.jobId());return;}
  var run=engine.detect(owners.getFirst(),job.artwork(),job.limit());
  jdbc.update("UPDATE detection_job SET status='COMPLETED',run_id=?,error_code=NULL,updated_at=now() WHERE id=?",run.runId(),p.jobId());
 }
}
