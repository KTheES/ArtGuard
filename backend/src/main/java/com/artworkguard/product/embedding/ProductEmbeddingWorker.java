package com.artworkguard.product.embedding;
import com.artworkguard.embedding.*;
import com.artworkguard.marketplace.adapter.MockImageLibrary;
import com.artworkguard.storage.ObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.*;
@Service @ConditionalOnProperty(name="artworkguard.embedding.enabled",havingValue="true")
public class ProductEmbeddingWorker {
 private final JdbcTemplate jdbc;private final ObjectStorage storage;private final AiEmbeddingClient ai;private final MockImageLibrary images;
 public ProductEmbeddingWorker(JdbcTemplate jdbc,ObjectStorage storage,AiEmbeddingClient ai,MockImageLibrary images){this.jdbc=jdbc;this.storage=storage;this.ai=ai;this.images=images;}
 record Image(String hash,String source,String key,boolean eligible,int size){
  Image(String hash,String source,String key,boolean eligible){this(hash,source,key,eligible,0);}
 }
 record Job(UUID imageId,String hash,String status,int generation){}
 @Transactional(timeout=120)
 public void process(ProductEmbeddingEvent event){
  var p=event.payload();
  var sources=jdbc.query("""
   SELECT i.image_hash,i.source_type,i.source_key,i.size_bytes,(i.active AND p.status='ACTIVE' AND m.enabled) AS eligible
   FROM product_image i JOIN product p ON p.id=i.product_id JOIN marketplace m ON m.id=p.marketplace_id
   WHERE i.id=? FOR UPDATE OF i
   """,(rs,n)->new Image(rs.getString("image_hash"),rs.getString("source_type"),rs.getString("source_key"),rs.getBoolean("eligible"),rs.getInt("size_bytes")),p.imageId());
  var jobs=jdbc.query("SELECT image_id,image_hash,status,generation FROM product_embedding_job WHERE id=? FOR UPDATE",
   (rs,n)->new Job(rs.getObject("image_id",UUID.class),rs.getString("image_hash"),rs.getString("status"),rs.getInt("generation")),p.jobId());
  if(jobs.isEmpty())return;
  var job=jobs.getFirst();
  if(job.generation()!=p.generation() || !"QUEUED".equals(job.status()))return;
  if(!job.imageId().equals(p.imageId()))throw new IllegalArgumentException("Product event image mismatch");
  if(sources.isEmpty() || !sources.getFirst().eligible() || !job.hash().equals(sources.getFirst().hash())){cancel(p.jobId());return;}
  var source=sources.getFirst();
  byte[] bytes;
  if("MOCK_RESOURCE".equals(source.source()))bytes=images.read(source.key());
  else if("STORED_PNG".equals(source.source()) && source.key().equals("catalog-imports/aliexpress/"+job.hash()+".png") && source.size()>0)
   bytes=storage.readUpload(source.key(),"image/png",source.size());
  else throw new IllegalArgumentException("Unsupported product image source");
  try{
   if(!job.hash().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))throw new IllegalArgumentException("Product image hash mismatch");
  }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  String key="product-images/"+p.imageId()+"/"+job.hash()+".png";
  // Immutable content-addressed key makes retries safe; DB rollback can leave an unreferenced object.
  storage.putImage(key,bytes);
  var result=ai.embedRegions(storage.downloadUrl(key,Duration.ofSeconds(60)));
  var vector=vector(result.full());
  UUID productEmbeddingId=UUID.randomUUID();
  int inserted=jdbc.update("""
   INSERT INTO product_image_embedding(id,job_id,image_id,image_hash,model,model_version,preprocessing_version,embedding,perceptual_hash_version,p_hash,d_hash)
   SELECT ?,?,i.id,i.image_hash,?,?,?,CAST(? AS vector),?,CAST(? AS bit(64)),CAST(? AS bit(64))
   FROM product_image i JOIN product p ON p.id=i.product_id JOIN marketplace m ON m.id=p.marketplace_id
   WHERE i.id=? AND i.image_hash=? AND i.active=TRUE AND p.status='ACTIVE' AND m.enabled=TRUE
   ON CONFLICT(job_id) DO UPDATE SET embedding=EXCLUDED.embedding,perceptual_hash_version=EXCLUDED.perceptual_hash_version,p_hash=EXCLUDED.p_hash,d_hash=EXCLUDED.d_hash,created_at=now()
   """,productEmbeddingId,p.jobId(),EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING,vector,result.perceptualHashVersion(),bits(result.pHash()),bits(result.dHash()),p.imageId(),job.hash());
  if(inserted==0){cancel(p.jobId());return;}
  var stored=jdbc.query("SELECT id FROM product_image_embedding WHERE job_id=? AND image_id=? AND image_hash=?",
   (rs,n)->rs.getObject(1,UUID.class),p.jobId(),p.imageId(),job.hash());
  if(stored.isEmpty()){cancel(p.jobId());return;}
  productEmbeddingId=stored.getFirst();
  for(var region:result.regions())jdbc.update("""
   INSERT INTO product_image_region_embedding(id,product_embedding_id,region_scheme,region_key,x,y,width,height,embedding,p_hash,d_hash)
   VALUES(?,?,?,?,?,?,?,?,CAST(? AS vector),CAST(? AS bit(64)),CAST(? AS bit(64)))
   ON CONFLICT(product_embedding_id,region_key) DO UPDATE SET region_scheme=EXCLUDED.region_scheme,x=EXCLUDED.x,y=EXCLUDED.y,width=EXCLUDED.width,height=EXCLUDED.height,embedding=EXCLUDED.embedding,p_hash=EXCLUDED.p_hash,d_hash=EXCLUDED.d_hash,created_at=now()
   """,UUID.randomUUID(),productEmbeddingId,result.scheme(),region.key(),region.x(),region.y(),region.width(),region.height(),vector(region.embedding()),bits(region.pHash()),bits(region.dHash()));
  // Replace the full-image signal in this transaction. Existing embeddings then receive a new
  // signal id, so prior signal:artwork source keys cannot suppress region-aware detection jobs.
  jdbc.update("DELETE FROM detection_signal WHERE source_type='PRODUCT' AND embedding_id=?",productEmbeddingId);
  jdbc.update("INSERT INTO detection_signal(source_type,embedding_id) VALUES('PRODUCT',?)",productEmbeddingId);
  jdbc.update("UPDATE product_image SET storage_key=?,storage_size_bytes=? WHERE id=? AND image_hash=?",key,bytes.length,p.imageId(),job.hash());
  jdbc.update("UPDATE product_embedding_job SET status='COMPLETED',error_code=NULL,updated_at=now() WHERE id=?",p.jobId());
 }
 private String vector(float[] values){var value=new StringJoiner(",","[","]");for(float item:values)value.add(Float.toString(item));return value.toString();}
 private String bits(String hex){return String.format("%64s",new java.math.BigInteger(hex,16).toString(2)).replace(' ','0');}
 private void cancel(UUID id){jdbc.update("UPDATE product_embedding_job SET status='CANCELED',error_code=NULL,updated_at=now() WHERE id=?",id);}
}
