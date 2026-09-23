package com.artworkguard.product.embedding;
import com.artworkguard.embedding.EmbeddingModel;
import com.artworkguard.marketplace.service.CatalogException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class ProductEmbeddingJobService {
 public record JobResponse(UUID jobId,String status,int generation,String errorCode,int dimension){}
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public ProductEmbeddingJobService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 @Transactional
 public void enqueueProduct(UUID productId){
  var images=jdbc.query("SELECT id FROM product_image WHERE product_id=? AND active=TRUE ORDER BY id",(rs,n)->rs.getObject(1,UUID.class),productId);
  for(var image:images)enqueue(image);
 }
 @Transactional
 public JobResponse enqueue(UUID imageId){
  // All writers lock image before job so import and worker use the same lock order.
  var hashes=jdbc.query("""
   SELECT i.image_hash FROM product_image i JOIN product p ON p.id=i.product_id JOIN marketplace m ON m.id=p.marketplace_id
   WHERE i.id=? AND i.active=TRUE AND p.status='ACTIVE' AND m.enabled=TRUE FOR UPDATE OF i
   """,(rs,n)->rs.getString(1),imageId);
  if(hashes.isEmpty())throw CatalogException.notFound();
  String hash=hashes.getFirst();
  int inserted=jdbc.update("""
   INSERT INTO product_embedding_job(id,image_id,image_hash,model,model_version,preprocessing_version,status)
   VALUES(?,?,?,?,?,?,'QUEUED') ON CONFLICT(image_id,image_hash,model,model_version,preprocessing_version) DO NOTHING
   """,UUID.randomUUID(),imageId,hash,EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING);
  var job=jdbc.queryForObject("""
   SELECT id,status,generation,error_code FROM product_embedding_job
   WHERE image_id=? AND image_hash=? AND model=? AND model_version=? AND preprocessing_version=? FOR UPDATE
   """,(rs,n)->new JobResponse(rs.getObject("id",UUID.class),rs.getString("status"),rs.getInt("generation"),rs.getString("error_code"),EmbeddingModel.DIMENSION),
   imageId,hash,EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING);
  if(inserted==1 || "FAILED".equals(job.status()) || "CANCELED".equals(job.status())){
   int generation=inserted==1?job.generation():job.generation()+1;
   jdbc.update("UPDATE product_embedding_job SET status='QUEUED',generation=?,error_code=NULL,updated_at=now() WHERE id=?",generation,job.jobId());
   var event=ProductEmbeddingEvent.create(job.jobId(),imageId,generation);
   String json;try{json=mapper.writeValueAsString(event);}catch(Exception e){throw new IllegalStateException("Could not serialize product event");}
   jdbc.update("INSERT INTO product_embedding_outbox(event_id,job_id,generation,payload) VALUES(?,?,?,?)",event.eventId(),job.jobId(),generation,json);
   return new JobResponse(job.jobId(),"QUEUED",generation,null,EmbeddingModel.DIMENSION);
  }
  return job;
 }
 @Transactional(readOnly=true)
 public JobResponse status(UUID imageId){
  var rows=jdbc.query("""
   SELECT j.id,j.status,j.generation,j.error_code FROM product_embedding_job j
   JOIN product_image i ON i.id=j.image_id AND i.image_hash=j.image_hash
   WHERE i.id=? AND j.model=? AND j.model_version=? AND j.preprocessing_version=?
   """,(rs,n)->new JobResponse(rs.getObject("id",UUID.class),rs.getString("status"),rs.getInt("generation"),rs.getString("error_code"),EmbeddingModel.DIMENSION),
   imageId,EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING);
  return rows.isEmpty()?new JobResponse(null,"NOT_REQUESTED",0,null,EmbeddingModel.DIMENSION):rows.getFirst();
 }
 @Transactional
 public void fail(UUID jobId,int generation,String code){
  jdbc.update("UPDATE product_embedding_job SET status='FAILED',error_code=?,updated_at=now() WHERE id=? AND generation=? AND status='QUEUED'",code,jobId,generation);
 }
}
