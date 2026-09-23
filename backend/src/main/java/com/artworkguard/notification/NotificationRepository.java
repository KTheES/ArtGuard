package com.artworkguard.notification;
import com.artworkguard.detection.DetectionPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Repository
public class NotificationRepository {
 private final JdbcTemplate jdbc;private final NotificationSettings settings;
 public NotificationRepository(JdbcTemplate jdbc,NotificationSettings settings){this.jdbc=jdbc;this.settings=settings;}
 public record Delivery(UUID id,String recipient,String subject,String body,int attempts){}
 public void enqueue(UUID detection,UUID run,DetectionPolicy.Severity severity){
  if(severity==DetectionPolicy.Severity.MEDIUM)return;
  String link=settings.appUrl().resolve("/detections/"+detection).toString();
  jdbc.update("""
   INSERT INTO notification_delivery(id,detection_id,detection_run_id,evidence_id,severity,recipient,subject,body)
   SELECT ?,d.id,?,e.id,?,u.email,?,?
   FROM detection d JOIN artwork a ON a.id=d.artwork_id JOIN app_user u ON u.id=a.user_id
   JOIN user_subscription us ON us.user_id=u.id AND us.status IN ('TRIAL','ACTIVE') JOIN subscription_plan sp ON sp.code=us.plan_code AND sp.email_alert=TRUE
   JOIN evidence_snapshot e ON e.detection_id=d.id AND e.detection_run_id=?
   WHERE d.id=? AND u.status='ACTIVE'
   ON CONFLICT(detection_id,severity) DO NOTHING
   """,UUID.randomUUID(),run,severity.name(),"[ArtworkGuard] "+severity.name()+" 도용 의심 상품 발견",
   "새로운 도용 의심 상품이 발견되었습니다.\n\n"+evidenceValue(detection,run)+"\n심각도: "+severity.name()+"\n탐지 결과: "+link,run,detection);
 }
 // The body query below avoids accepting mutable request text; values come from the immutable evidence row.
 private String evidenceValue(UUID detection,UUID run){
  return jdbc.query("SELECT '작품: '||a.title||E'\\n마켓: '||e.marketplace_code||E'\\n유사도: '||round(d.similarity::numeric*100,2)||E'%\\n상품 URL: '||e.product_url FROM evidence_snapshot e JOIN detection d ON d.id=e.detection_id JOIN artwork a ON a.id=d.artwork_id WHERE e.detection_id=? AND e.detection_run_id=?",
   (r,n)->r.getString(1),detection,run).stream().findFirst().orElseThrow(()->new IllegalStateException("Notification evidence is unavailable"));
 }
 @Transactional
 public Optional<Delivery> claim(){
  jdbc.update("UPDATE notification_delivery SET status='FAILED',locked_until=NULL,error_code='DELIVERY_LEASE_EXPIRED' WHERE status='SENDING' AND locked_until<=now() AND attempts>=5");
  var rows=jdbc.query("""
   SELECT id,recipient,subject,body,attempts FROM notification_delivery
   WHERE attempts<5 AND ((status='PENDING' AND next_attempt_at<=now()) OR (status='SENDING' AND locked_until<=now()))
   ORDER BY next_attempt_at,created_at LIMIT 1 FOR UPDATE SKIP LOCKED
   """,(r,n)->new Delivery(r.getObject("id",UUID.class),r.getString("recipient"),r.getString("subject"),r.getString("body"),r.getInt("attempts")+1));
  if(rows.isEmpty())return Optional.empty();var item=rows.getFirst();
  jdbc.update("UPDATE notification_delivery SET status='SENDING',attempts=?,locked_until=now()+interval '2 minutes',error_code=NULL WHERE id=?",item.attempts(),item.id());
  return Optional.of(item);
 }
 @Transactional public void sent(UUID id){jdbc.update("UPDATE notification_delivery SET status='SENT',sent_at=now(),locked_until=NULL,error_code=NULL WHERE id=? AND status='SENDING'",id);}
 @Transactional public void failed(UUID id,int attempts){jdbc.update("""
  UPDATE notification_delivery SET status=CASE WHEN ? >= 5 THEN 'FAILED' ELSE 'PENDING' END,
   next_attempt_at=now()+make_interval(secs=>LEAST(300,power(2,?)::integer)),locked_until=NULL,error_code='SMTP_DELIVERY_FAILED'
  WHERE id=? AND status='SENDING'
  """,attempts,attempts,id);}
}
