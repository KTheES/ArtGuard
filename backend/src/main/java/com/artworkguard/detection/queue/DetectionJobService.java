package com.artworkguard.detection.queue;
import com.artworkguard.detection.*;
import com.artworkguard.marketplace.service.CatalogAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class DetectionJobService {
 public record JobResponse(UUID jobId,String status,int generation,String errorCode,UUID runId){}
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final CatalogAccess access;private final DetectionRepository repository;private final com.artworkguard.redis.RedisWorkGuard guard;private final com.artworkguard.subscription.SubscriptionService subscriptions;
 public DetectionJobService(JdbcTemplate jdbc,ObjectMapper mapper,CatalogAccess access,DetectionRepository repository,com.artworkguard.redis.RedisWorkGuard guard,com.artworkguard.subscription.SubscriptionService subscriptions){this.jdbc=jdbc;this.mapper=mapper;this.access=access;this.repository=repository;this.guard=guard;this.subscriptions=subscriptions;}
 @Transactional
 public JobResponse request(UUID owner,UUID artwork,int limit){
  if(limit<1||limit>500)throw new IllegalArgumentException("Invalid detection limit");
  access.active(owner);repository.checkOwnedArtwork(owner,artwork);
  repository.artworkEmbedding(artwork).orElseThrow(DetectionException::new);
  guard.rate("detect",owner,30);
  return enqueue(artwork,subscriptions.allowedDetectionLimit(owner,limit),false,"manual:"+UUID.randomUUID());
 }
 @Transactional
 public JobResponse enqueue(UUID artwork,int limit,boolean automatic,String source){
  return enqueue(artwork,limit,automatic,source,0);
 }
 @Transactional
 public JobResponse enqueue(UUID artwork,int limit,boolean automatic,String source,int priority){
  UUID id=UUID.randomUUID();
  int inserted=jdbc.update("""
   INSERT INTO detection_job(id,artwork_id,source_key,automatic,result_limit,queue_priority,status) VALUES(?,?,?,?,?,?,'QUEUED')
   ON CONFLICT(source_key) DO NOTHING
   """,id,artwork,source,automatic,limit,priority);
  if(inserted==1){outbox(id,artwork,1);return new JobResponse(id,"QUEUED",1,null,null);}
  return jdbc.queryForObject("SELECT id,status,generation,error_code,run_id FROM detection_job WHERE source_key=?",this::map,source);
 }
 private JobResponse map(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new JobResponse(rs.getObject("id",UUID.class),rs.getString("status"),rs.getInt("generation"),rs.getString("error_code"),rs.getObject("run_id",UUID.class));}
 private void outbox(UUID id,UUID artwork,int generation){
  var event=DetectionEvent.create(id,artwork,generation);String json;
  try{json=mapper.writeValueAsString(event);}catch(Exception e){throw new IllegalStateException("Could not serialize detection event");}
  jdbc.update("INSERT INTO detection_outbox(event_id,job_id,generation,payload) VALUES(?,?,?,?)",event.eventId(),id,generation,json);
 }
 @Transactional(readOnly=true)
 public JobResponse status(UUID owner,UUID artwork,UUID id){access.active(owner);return owned(owner,artwork,id,false);}
 private JobResponse owned(UUID owner,UUID artwork,UUID id,boolean lock){
  var rows=jdbc.query("""
   SELECT j.id,j.status,j.generation,j.error_code,j.run_id FROM detection_job j
   JOIN artwork a ON a.id=j.artwork_id JOIN app_user u ON u.id=a.user_id
   WHERE j.id=? AND a.id=? AND a.user_id=? AND a.deleted=FALSE AND u.status='ACTIVE'
   """+(lock?" FOR UPDATE OF j":""),this::map,id,artwork,owner);
  return rows.stream().findFirst().orElseThrow(DetectionReviewException::missing);
 }
 @Transactional
 public JobResponse retry(UUID owner,UUID artwork,UUID id){
  access.active(owner);owned(owner,artwork,id,false);guard.rate("detect",owner,30);var job=owned(owner,artwork,id,true);
  if(!"FAILED".equals(job.status()))return job;
  int generation=job.generation()+1;
  jdbc.update("UPDATE detection_job SET status='QUEUED',generation=?,error_code=NULL,updated_at=now() WHERE id=?",generation,id);
  outbox(id,artwork,generation);return new JobResponse(id,"QUEUED",generation,null,null);
 }
 @Transactional public void fail(UUID id,int generation,String code){
  jdbc.update("UPDATE detection_job SET status='FAILED',error_code=?,updated_at=now() WHERE id=? AND generation=? AND status='QUEUED'",code,id,generation);
 }
}
