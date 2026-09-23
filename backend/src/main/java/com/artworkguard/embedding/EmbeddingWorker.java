package com.artworkguard.embedding;
import com.artworkguard.storage.ObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.UUID;
@Service
@ConditionalOnProperty(name="artworkguard.embedding.enabled",havingValue="true")
public class EmbeddingWorker {
 private final JdbcTemplate jdbc;
 private final ObjectStorage storage;
 private final AiEmbeddingClient ai;
 public EmbeddingWorker(JdbcTemplate jdbc,ObjectStorage storage,AiEmbeddingClient ai){this.jdbc=jdbc;this.storage=storage;this.ai=ai;}
 record Job(UUID artworkId,String status,int generation){}
 @Transactional(timeout=120)
 public void process(EmbeddingEvent event) {
  var payload=event.payload();
  var jobs=jdbc.query("SELECT artwork_id,status,generation FROM embedding_job WHERE id=? FOR UPDATE",
   (rs,row)->new Job(rs.getObject("artwork_id",UUID.class),rs.getString("status"),rs.getInt("generation")),payload.jobId());
  if(jobs.isEmpty())return;
  var job=jobs.getFirst();
  if(job.generation()!=payload.generation() || !"QUEUED".equals(job.status()))return;
  if(!job.artworkId().equals(payload.artworkId()))throw new IllegalArgumentException("Embedding event artwork mismatch");
  var keys=jdbc.query("""
   SELECT a.original_key FROM artwork a JOIN app_user u ON u.id=a.user_id
   WHERE a.id=? AND a.deleted=FALSE AND u.status='ACTIVE'
   """,(rs,row)->rs.getString(1),job.artworkId());
  if(keys.isEmpty()){cancel(payload.jobId());return;}
  var result=ai.embedWithHashes(storage.downloadUrl(keys.getFirst(),Duration.ofSeconds(60)));
  var values=result.embedding();
  var vector=new StringBuilder("[");
  for(int i=0;i<values.length;i++){if(i>0)vector.append(',');vector.append(Float.toString(values[i]));}
  vector.append(']');
  int inserted=jdbc.update("""
   INSERT INTO artwork_embedding(id,job_id,artwork_id,model,model_version,preprocessing_version,embedding,perceptual_hash_version,p_hash,d_hash)
   SELECT ?,?,a.id,?,?,?,CAST(? AS vector),?,CAST(? AS bit(64)),CAST(? AS bit(64))
   FROM artwork a JOIN app_user u ON u.id=a.user_id WHERE a.id=? AND a.deleted=FALSE AND u.status='ACTIVE'
   ON CONFLICT(job_id) DO UPDATE SET embedding=EXCLUDED.embedding,perceptual_hash_version=EXCLUDED.perceptual_hash_version,p_hash=EXCLUDED.p_hash,d_hash=EXCLUDED.d_hash,created_at=now()
   """,UUID.randomUUID(),payload.jobId(),EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING,vector.toString(),result.perceptualHashVersion(),bits(result.pHash()),bits(result.dHash()),job.artworkId());
  if(inserted==0){cancel(payload.jobId());return;}
  var ids=jdbc.query("SELECT id FROM artwork_embedding WHERE job_id=?",(rs,row)->rs.getObject(1,UUID.class),payload.jobId());
  jdbc.update("DELETE FROM detection_signal WHERE source_type='ARTWORK' AND embedding_id=?",ids.getFirst());
  jdbc.update("INSERT INTO detection_signal(source_type,embedding_id) VALUES('ARTWORK',?)",ids.getFirst());
  jdbc.update("UPDATE embedding_job SET status='COMPLETED',error_code=NULL,updated_at=now() WHERE id=?",payload.jobId());
 }
 private String bits(String hex){return String.format("%64s",new java.math.BigInteger(hex,16).toString(2)).replace(' ','0');}
 private void cancel(UUID jobId){jdbc.update("UPDATE embedding_job SET status='CANCELED',error_code=NULL,updated_at=now() WHERE id=?",jobId);}
}
