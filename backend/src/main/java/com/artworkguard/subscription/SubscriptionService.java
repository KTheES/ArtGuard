package com.artworkguard.subscription;

import com.artworkguard.auth.service.AuthException;
import com.artworkguard.subscription.SubscriptionDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SubscriptionService {
 private final JdbcTemplate jdbc;
 public SubscriptionService(JdbcTemplate jdbc){this.jdbc=jdbc;}

 @Transactional(readOnly=true)
 public Catalog plans(){return new Catalog(jdbc.query("SELECT *,EXTRACT(EPOCH FROM scan_interval)::bigint/3600 scan_interval_hours FROM subscription_plan ORDER BY sort_order",this::plan));}

 @Transactional(readOnly=true)
 public Current current(UUID owner){
  var rows=jdbc.query("""
   SELECT p.*,EXTRACT(EPOCH FROM p.scan_interval)::bigint/3600 scan_interval_hours,s.plan_code subscribed_plan_code,
    s.status,s.current_period_end,s.cancel_at_period_end,count(a.id) active_artworks
   FROM app_user u JOIN user_subscription s ON s.user_id=u.id
   JOIN subscription_plan p ON p.code=CASE WHEN s.status IN ('TRIAL','ACTIVE') THEN s.plan_code ELSE 'FREE' END
   LEFT JOIN artwork a ON a.user_id=u.id AND a.deleted=FALSE
   WHERE u.id=? AND u.status='ACTIVE' GROUP BY p.code,s.plan_code,s.status,s.current_period_end,s.cancel_at_period_end
   """,(r,n)->{var p=plan(r,n);long used=r.getLong("active_artworks");var end=r.getTimestamp("current_period_end");String status=r.getString("status");
    return new Current(p,r.getString("subscribed_plan_code"),status,List.of("TRIAL","ACTIVE").contains(status),end==null?null:end.toInstant(),r.getBoolean("cancel_at_period_end"),used,Math.max(0,p.artworkLimit()-used));},owner);
  return rows.stream().findFirst().orElseThrow(AuthException::unauthorized);
 }

 @Transactional
 public void requireArtworkCapacity(UUID owner){
  if(jdbc.query("SELECT id FROM app_user WHERE id=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getObject(1,UUID.class),owner).isEmpty())throw AuthException.unauthorized();
  if(current(owner).remainingArtworks()==0)throw SubscriptionException.artworkLimit();
 }

 @Transactional(readOnly=true)
 public int allowedDetectionLimit(UUID owner,int requested){return Math.min(requested,current(owner).plan().detectionResultLimit());}

 @Transactional(readOnly=true)
 public void requireEvidence(UUID owner){if(!current(owner).plan().evidence())throw SubscriptionException.evidence();}

 private Plan plan(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Plan(r.getString("code"),r.getString("display_name"),r.getInt("artwork_limit"),
  r.getLong("scan_interval_hours"),r.getInt("detection_result_limit"),r.getInt("queue_priority"),r.getBoolean("advanced_detection"),
  r.getBoolean("email_alert"),r.getBoolean("evidence"),r.getBoolean("reports"));}
}
