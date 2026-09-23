package com.artworkguard.embedding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class EmbeddingJobService {
 public record JobResponse(UUID jobId,String status,int generation,String errorCode,int dimension){}
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 public EmbeddingJobService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 @Transactional
 public JobResponse enqueue(UUID artworkId) {
  int inserted=jdbc.update("""
   INSERT INTO embedding_job(id,artwork_id,model,model_version,preprocessing_version,status)
   VALUES(?,?,?,?,?,'QUEUED') ON CONFLICT(artwork_id,model,model_version,preprocessing_version) DO NOTHING
   """,UUID.randomUUID(),artworkId,EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING);
  var job=jdbc.queryForObject("""
   SELECT id,status,generation,error_code FROM embedding_job
   WHERE artwork_id=? AND model=? AND model_version=? AND preprocessing_version=? FOR UPDATE
   """,(rs,row)->new JobResponse(rs.getObject("id",UUID.class),rs.getString("status"),rs.getInt("generation"),rs.getString("error_code"),EmbeddingModel.DIMENSION),
   artworkId,EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING);
  if(inserted==1 || "FAILED".equals(job.status())) {
   int generation=inserted==1?job.generation():job.generation()+1;
   jdbc.update("UPDATE embedding_job SET status='QUEUED',generation=?,error_code=NULL,updated_at=now() WHERE id=?",generation,job.jobId());
   var event=EmbeddingEvent.create(job.jobId(),artworkId,generation);
   String json;
   try{json=mapper.writeValueAsString(event);}catch(Exception e){throw new IllegalStateException("Could not serialize embedding event");}
   jdbc.update("INSERT INTO embedding_outbox(event_id,job_id,generation,payload) VALUES(?,?,?,?)",event.eventId(),job.jobId(),generation,json);
   return new JobResponse(job.jobId(),"QUEUED",generation,null,EmbeddingModel.DIMENSION);
  }
  return job;
 }
 @Transactional(readOnly=true)
 public JobResponse status(UUID artworkId) {
  var jobs=jdbc.query("""
   SELECT id,status,generation,error_code FROM embedding_job
   WHERE artwork_id=? AND model=? AND model_version=? AND preprocessing_version=?
   """,(rs,row)->new JobResponse(rs.getObject("id",UUID.class),rs.getString("status"),rs.getInt("generation"),rs.getString("error_code"),EmbeddingModel.DIMENSION),
   artworkId,EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING);
  return jobs.isEmpty()?new JobResponse(null,"NOT_REQUESTED",0,null,EmbeddingModel.DIMENSION):jobs.getFirst();
 }
 @Transactional
 public void fail(UUID jobId,int generation,String code) {
  jdbc.update("UPDATE embedding_job SET status='FAILED',error_code=?,updated_at=now() WHERE id=? AND generation=? AND status='QUEUED'",code,jobId,generation);
 }
}
