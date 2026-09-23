package com.artworkguard.detection;
import com.artworkguard.embedding.EmbeddingModel;
import com.artworkguard.artwork.service.ArtworkException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class DetectionRepository {
 private final JdbcTemplate jdbc;
 public DetectionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Candidate(UUID productId,UUID productEmbeddingId,UUID productRegionEmbeddingId,double embeddingSimilarity,Double pHashSimilarity,Double dHashSimilarity,double similarity){
  public Candidate(UUID productId,UUID productEmbeddingId,double similarity){this(productId,productEmbeddingId,null,similarity,null,null,similarity);}
 }
 public void checkOwnedArtwork(UUID owner,UUID artwork){
  var ids=jdbc.query("SELECT a.id FROM artwork a JOIN app_user u ON u.id=a.user_id WHERE a.id=? AND a.user_id=? AND a.deleted=FALSE AND u.status='ACTIVE'",
   (rs,n)->rs.getObject(1,UUID.class),artwork,owner);
  if(ids.isEmpty())throw ArtworkException.notFound();
 }
 public void lockOwnedArtwork(UUID owner,UUID artwork){
  var ids=jdbc.query("""
   SELECT a.id FROM artwork a JOIN app_user u ON u.id=a.user_id
   WHERE a.id=? AND a.user_id=? AND a.deleted=FALSE AND u.status='ACTIVE' FOR UPDATE OF a
   """,(rs,n)->rs.getObject(1,UUID.class),artwork,owner);
  if(ids.isEmpty())throw ArtworkException.notFound();
 }
 public Optional<UUID> artworkEmbedding(UUID artwork){
  return jdbc.query("""
   SELECT id FROM artwork_embedding WHERE artwork_id=? AND model=? AND model_version=? AND preprocessing_version=?
   """,(rs,n)->rs.getObject(1,UUID.class),artwork,EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING).stream().findFirst();
 }
 public List<Candidate> candidates(UUID artworkEmbedding,double threshold,int limitPlusOne){
  // Exact cosine distance for V1. No ANN index or pre-limit before per-product deduplication.
  return jdbc.query("""
   WITH vectors AS (
    SELECT e.id AS product_embedding_id,NULL::uuid AS product_region_embedding_id,e.image_id,e.model,e.model_version,e.preprocessing_version,e.embedding,e.p_hash,e.d_hash,e.perceptual_hash_version
    FROM current_product_image_embedding e
    UNION ALL
    SELECT r.product_embedding_id,r.id,e.image_id,e.model,e.model_version,e.preprocessing_version,r.embedding,r.p_hash,r.d_hash,e.perceptual_hash_version
    FROM current_product_image_region_embedding r JOIN current_product_image_embedding e ON e.id=r.product_embedding_id
   ), raw AS (
    SELECT i.product_id,e.product_embedding_id,e.product_region_embedding_id,
     LEAST(1.0,GREATEST(-1.0,1-(e.embedding <=> a.embedding))) AS embedding_similarity,
     CASE WHEN e.p_hash IS NULL OR a.p_hash IS NULL OR e.perceptual_hash_version!=a.perceptual_hash_version THEN NULL ELSE 1.0::double precision-bit_count(e.p_hash # a.p_hash)::double precision/64.0::double precision END AS phash_similarity,
     CASE WHEN e.d_hash IS NULL OR a.d_hash IS NULL OR e.perceptual_hash_version!=a.perceptual_hash_version THEN NULL ELSE 1.0::double precision-bit_count(e.d_hash # a.d_hash)::double precision/64.0::double precision END AS dhash_similarity
    FROM vectors e JOIN product_image i ON i.id=e.image_id
    JOIN artwork_embedding a ON a.id=?
      AND e.model=a.model AND e.model_version=a.model_version AND e.preprocessing_version=a.preprocessing_version
   ), scored AS (
    SELECT *,GREATEST(embedding_similarity,0.8*GREATEST(0,embedding_similarity)+0.1*COALESCE(phash_similarity,embedding_similarity)+0.1*COALESCE(dhash_similarity,embedding_similarity)) AS similarity FROM raw
   ), best AS (
    SELECT DISTINCT ON(product_id) product_id,product_embedding_id,product_region_embedding_id,embedding_similarity,phash_similarity,dhash_similarity,similarity
    FROM scored WHERE similarity>=? AND similarity<=1.000001
    ORDER BY product_id,similarity DESC,product_embedding_id,product_region_embedding_id NULLS FIRST
   )
   SELECT product_id,product_embedding_id,product_region_embedding_id,embedding_similarity,phash_similarity,dhash_similarity,similarity FROM best
   ORDER BY similarity DESC,product_id LIMIT ?
   """,(rs,n)->new Candidate(rs.getObject("product_id",UUID.class),rs.getObject("product_embedding_id",UUID.class),rs.getObject("product_region_embedding_id",UUID.class),rs.getDouble("embedding_similarity"),rs.getObject("phash_similarity",Double.class),rs.getObject("dhash_similarity",Double.class),rs.getDouble("similarity")),
   artworkEmbedding,threshold,limitPlusOne);
 }
 public List<Candidate> basicCandidates(UUID artworkEmbedding,double threshold,int limitPlusOne){
  return jdbc.query("""
   WITH ranked AS (
    SELECT i.product_id,e.id AS product_embedding_id,LEAST(1.0,GREATEST(-1.0,1-(e.embedding <=> a.embedding))) AS similarity
    FROM current_product_image_embedding e JOIN product_image i ON i.id=e.image_id JOIN artwork_embedding a ON a.id=?
    WHERE e.model=a.model AND e.model_version=a.model_version AND e.preprocessing_version=a.preprocessing_version
   ), best AS (
    SELECT DISTINCT ON(product_id) product_id,product_embedding_id,similarity FROM ranked WHERE similarity>=? AND similarity<=1.000001
    ORDER BY product_id,similarity DESC,product_embedding_id
   ) SELECT product_id,product_embedding_id,similarity FROM best ORDER BY similarity DESC,product_id LIMIT ?
   """,(rs,n)->new Candidate(rs.getObject("product_id",UUID.class),rs.getObject("product_embedding_id",UUID.class),rs.getDouble("similarity")),
   artworkEmbedding,threshold,limitPlusOne);
 }
 public void saveRun(UUID run,UUID artwork,UUID embedding,DetectionPolicy policy,int limit,int count,boolean truncated){
  jdbc.update("""
   INSERT INTO detection_run(id,artwork_id,artwork_embedding_id,model,model_version,preprocessing_version,
    medium_threshold,high_threshold,critical_threshold,result_limit,matched_products,truncated)
   VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
   """,run,artwork,embedding,EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING,policy.medium(),policy.high(),policy.critical(),limit,count,truncated);
 }
 public UUID upsert(UUID run,UUID artwork,UUID embedding,Candidate candidate,DetectionPolicy.Severity severity){
  return jdbc.queryForObject("""
   INSERT INTO detection(id,artwork_id,product_id,artwork_embedding_id,product_embedding_id,product_region_embedding_id,last_run_id,
    model,model_version,preprocessing_version,embedding_similarity,phash_similarity,dhash_similarity,ensemble_version,similarity,severity)
   VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
   ON CONFLICT(artwork_id,product_id,model,model_version,preprocessing_version) DO UPDATE SET
    artwork_embedding_id=EXCLUDED.artwork_embedding_id,product_embedding_id=EXCLUDED.product_embedding_id,product_region_embedding_id=EXCLUDED.product_region_embedding_id,
    last_run_id=EXCLUDED.last_run_id,embedding_similarity=EXCLUDED.embedding_similarity,phash_similarity=EXCLUDED.phash_similarity,dhash_similarity=EXCLUDED.dhash_similarity,ensemble_version=EXCLUDED.ensemble_version,similarity=EXCLUDED.similarity,severity=EXCLUDED.severity,last_detected_at=now()
   RETURNING id
   """,(rs,n)->rs.getObject(1,UUID.class),UUID.randomUUID(),artwork,candidate.productId(),embedding,candidate.productEmbeddingId(),candidate.productRegionEmbeddingId(),run,
   EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING,candidate.embeddingSimilarity(),candidate.pHashSimilarity(),candidate.dHashSimilarity(),
   candidate.productRegionEmbeddingId()==null&&candidate.pHashSimilarity()==null&&candidate.dHashSimilarity()==null?"dinov2-v1":"dinov2-80-phash-10-dhash-10-v1",candidate.similarity(),severity.name());
 }
}
