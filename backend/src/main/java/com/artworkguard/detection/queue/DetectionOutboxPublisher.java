package com.artworkguard.detection.queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
@Component
@ConditionalOnProperty(name="artworkguard.detection.enabled",havingValue="true")
public class DetectionOutboxPublisher {
 private final JdbcTemplate jdbc;
 private final KafkaTemplate<String,String> kafka;
 public DetectionOutboxPublisher(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka){this.jdbc=jdbc;this.kafka=kafka;}
 record Pending(UUID eventId,UUID jobId,int generation,int attempts,String payload){}
 @Scheduled(fixedDelayString="${artworkguard.detection.outbox-delay-ms:2000}")
 @Transactional(timeout=60)
 public void publish() {
  var events=jdbc.query("""
   SELECT o.event_id,o.job_id,o.generation,o.attempts,o.payload FROM detection_outbox o JOIN detection_job j ON j.id=o.job_id
   WHERE o.published_at IS NULL AND o.failed=FALSE AND o.next_attempt_at<=now()
   ORDER BY j.queue_priority DESC,o.created_at LIMIT 3 FOR UPDATE OF o SKIP LOCKED
   """,(rs,row)->new Pending(rs.getObject("event_id",UUID.class),rs.getObject("job_id",UUID.class),rs.getInt("generation"),rs.getInt("attempts"),rs.getString("payload")));
  for(var event:events){
   try{
    kafka.send("detection.requested",event.jobId().toString(),event.payload()).get(10,TimeUnit.SECONDS);
    jdbc.update("UPDATE detection_outbox SET published_at=now(),attempts=attempts+1 WHERE event_id=?",event.eventId());
   }catch(Exception e){
    if(e instanceof InterruptedException){Thread.currentThread().interrupt();throw new IllegalStateException("Outbox publisher interrupted");}
    jdbc.update("""
     UPDATE detection_outbox SET attempts=attempts+1,failed=(attempts+1>=10),
     next_attempt_at=now()+make_interval(secs=>LEAST(300,power(2,attempts)::integer)) WHERE event_id=?
     """,event.eventId());
    if(event.attempts()+1>=10)jdbc.update("""
     UPDATE detection_job SET status='FAILED',error_code='EVENT_PUBLISH_FAILED',updated_at=now()
     WHERE id=? AND generation=? AND status='QUEUED'
     """,event.jobId(),event.generation());
   }
  }
 }
}
