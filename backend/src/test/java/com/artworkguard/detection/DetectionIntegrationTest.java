package com.artworkguard.detection;
import com.artworkguard.artwork.domain.Artwork;
import com.artworkguard.artwork.repository.ArtworkRepository;
import com.artworkguard.artwork.service.ArtworkException;
import com.artworkguard.user.domain.AppUser;
import com.artworkguard.user.repository.UserRepository;
import com.artworkguard.embedding.*;
import com.artworkguard.product.embedding.ProductEmbeddingJobService;
import com.artworkguard.marketplace.adapter.MockMarketplaceAdapter;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import com.artworkguard.marketplace.service.CatalogImportService;
import com.artworkguard.evidence.EvidenceService;
import com.artworkguard.monitoring.*;
import com.artworkguard.detection.queue.DetectionJobService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") @Testcontainers @SpringBootTest
class DetectionIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
 @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
  r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
  r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");
  String secret=UUID.randomUUID().toString()+UUID.randomUUID();r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(secret.getBytes()));
 }
 @Autowired DetectionReviewService reviews;
 @Autowired EvidenceService evidence;
 @Autowired DetectionService service;@Autowired JdbcTemplate jdbc;@Autowired UserRepository users;
 @Autowired ArtworkRepository artworks;@Autowired EmbeddingJobService artworkJobs;@Autowired ProductEmbeddingJobService productJobs;
 @Autowired CatalogImportService importer;@Autowired MockMarketplaceAdapter adapter;
 @Autowired DetectionJobService monitorJobs;
 String vector(double cosine){
  var values=new StringJoiner(",","[","]");values.add(Double.toString(cosine));values.add(Double.toString(Math.sqrt(1-cosine*cosine)));
  for(int i=2;i<768;i++)values.add("0");return values.toString();
 }
 void productVector(UUID image,double score){
  UUID job=productJobs.enqueue(image).jobId();
  jdbc.update("""
   INSERT INTO product_image_embedding(id,job_id,image_id,image_hash,model,model_version,preprocessing_version,embedding)
   SELECT ?,?,id,image_hash,?,?,?,CAST(? AS vector) FROM product_image WHERE id=?
   """,UUID.randomUUID(),job,EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING,vector(score),image);
  jdbc.update("UPDATE product_embedding_job SET status='COMPLETED' WHERE id=?",job);
  String hash=jdbc.queryForObject("SELECT image_hash FROM product_image WHERE id=?",String.class,image);
  jdbc.update("UPDATE product_image SET storage_key=?,storage_size_bytes=123 WHERE id=?","product-images/"+image+"/"+hash+".png",image);
 }
 @Test void cosineRankingDeduplicationFilteringAndReviewPreservation(){
  var owner=users.saveAndFlush(new AppUser(UUID.randomUUID()+"@example.com","test-hash","Owner"));
  jdbc.update("UPDATE user_subscription SET plan_code='CREATOR',updated_at=now(),version=version+1 WHERE user_id=?",owner.getId());
  var other=users.saveAndFlush(new AppUser(UUID.randomUUID()+"@example.com","test-hash","Other"));
  var artwork=artworks.saveAndFlush(new Artwork(UUID.randomUUID(),owner.getId(),"Source","",512,512,Instant.now()));
  assertThrows(ArtworkException.class,()->service.detect(other.getId(),artwork.getId(),100));
  assertThrows(DetectionException.class,()->service.detect(owner.getId(),artwork.getId(),100));
  UUID job=artworkJobs.enqueue(artwork.getId()).jobId();
  jdbc.update("""
   INSERT INTO artwork_embedding(id,job_id,artwork_id,model,model_version,preprocessing_version,embedding)
   VALUES(?,?,?,?,?,?,CAST(? AS vector))
   """,UUID.randomUUID(),job,artwork.getId(),EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING,vector(1));
  jdbc.update("UPDATE embedding_job SET status='COMPLETED' WHERE id=?",job);
  jdbc.update("UPDATE artwork SET monitoring_enabled=TRUE WHERE id=?",artwork.getId());
  var scheduler=new ArtworkMonitorScheduler(jdbc,monitorJobs,new MonitoringSettings(true,java.time.Duration.ofDays(1),100,100),
   java.time.Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"),java.time.ZoneOffset.UTC));
  scheduler.scan();scheduler.scan();
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM monitoring_scan_cycle WHERE status='COMPLETED'",Integer.class));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM detection_job WHERE automatic=TRUE AND source_key LIKE 'schedule:%'",Integer.class));
  importer.persist(MarketplaceCode.MOCK,adapter.searchProducts("",20));
  var images=jdbc.query("SELECT id FROM product_image ORDER BY external_image_id",(rs,n)->rs.getObject(1,UUID.class));
  productVector(images.get(0),.99);productVector(images.get(1),.88);productVector(images.get(2),.78);
  var limited=service.detect(owner.getId(),artwork.getId(),2);
  assertTrue(limited.truncated());assertEquals(2,limited.matchedProducts());
  var full=service.detect(owner.getId(),artwork.getId(),100);
  assertFalse(full.truncated());assertEquals(3,full.matchedProducts());
  assertEquals(List.of("CRITICAL","HIGH","MEDIUM"),jdbc.queryForList("SELECT severity FROM detection ORDER BY similarity DESC",String.class));
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM notification_delivery",Integer.class));
  UUID detection=jdbc.queryForObject("SELECT id FROM detection ORDER BY similarity DESC LIMIT 1",UUID.class);
  assertFalse(evidence.list(owner.getId(),detection).isEmpty());
  Instant first=jdbc.queryForObject("SELECT first_detected_at FROM detection WHERE id=?",(rs,n)->rs.getTimestamp(1).toInstant(),detection);
  jdbc.update("UPDATE detection SET review_status='DISMISSED' WHERE id=?",detection);
  service.detect(owner.getId(),artwork.getId(),100);
  assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM detection",Integer.class));
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM notification_delivery",Integer.class));
  assertEquals("DISMISSED",jdbc.queryForObject("SELECT review_status FROM detection WHERE id=?",String.class,detection));
  assertEquals(first,jdbc.queryForObject("SELECT first_detected_at FROM detection WHERE id=?",(rs,n)->rs.getTimestamp(1).toInstant(),detection));

  // A second, stronger image of the same product updates its evidence, not product count.
  UUID extra=UUID.randomUUID();
  jdbc.update("""
   INSERT INTO product_image(id,product_id,external_image_id,original_url,source_type,source_key,width,height,image_hash)
   SELECT ?,product_id,'extra-image',original_url,source_type,source_key,width,height,image_hash FROM product_image WHERE id=?
   """,extra,images.get(0));
  productVector(extra,1);
  assertEquals(3,service.detect(owner.getId(),artwork.getId(),100).matchedProducts());
  assertEquals(1,jdbc.queryForObject("SELECT max(similarity) FROM detection",Double.class),1e-6);

  jdbc.update("UPDATE product_image SET active=FALSE WHERE id IN (?,?)",images.get(0),extra);
  assertEquals(2,service.detect(owner.getId(),artwork.getId(),100).matchedProducts());
  jdbc.update("UPDATE product_image SET image_hash=? WHERE id=?","a".repeat(64),images.get(1));
  assertEquals(1,service.detect(owner.getId(),artwork.getId(),100).matchedProducts());
  jdbc.update("UPDATE product_image_embedding SET preprocessing_version='other-version' WHERE image_id=?",images.get(2));
  assertEquals(0,service.detect(owner.getId(),artwork.getId(),100).matchedProducts());
  var page=reviews.list(owner.getId(),null,null,null,0,20);
  assertEquals(3,page.totalElements());
  assertTrue(page.items().stream().noneMatch(DetectionReviewDtos.Item::currentEvidence));
  assertEquals(0,reviews.list(other.getId(),null,null,null,0,20).totalElements());
  assertThrows(DetectionReviewException.class,()->reviews.detail(other.getId(),detection));
  var detail=reviews.detail(owner.getId(),detection);
  var changed=reviews.update(owner.getId(),detection,new DetectionReviewDtos.UpdateRequest(DetectionReviewDtos.ReviewStatus.CONFIRMED,detail.detection().version()));
  assertEquals(detail.detection().version()+1,changed.detection().version());
  assertNotNull(changed.reviewedAt());
  assertThrows(DetectionReviewException.class,()->reviews.update(owner.getId(),detection,new DetectionReviewDtos.UpdateRequest(DetectionReviewDtos.ReviewStatus.NEW,detail.detection().version())));
  assertEquals(1,reviews.list(owner.getId(),artwork.getId(),DetectionReviewDtos.ReviewStatus.CONFIRMED,null,0,20).totalElements());
  UUID detectedProduct=jdbc.queryForObject("SELECT product_id FROM detection WHERE id=?",UUID.class,detection);
  jdbc.update("UPDATE product SET status='REMOVED' WHERE id=?",detectedProduct);
  assertFalse(evidence.list(owner.getId(),detection).isEmpty());
  // Evidence updates also invalidate a previously fetched review version.
  jdbc.update("UPDATE detection SET similarity=similarity WHERE id=?",detection);
  assertThrows(DetectionReviewException.class,()->reviews.update(owner.getId(),detection,new DetectionReviewDtos.UpdateRequest(DetectionReviewDtos.ReviewStatus.NEW,changed.detection().version())));
  jdbc.update("UPDATE artwork SET deleted=TRUE WHERE id=?",artwork.getId());
  assertEquals(0,reviews.list(owner.getId(),null,null,null,0,20).totalElements());
  assertThrows(DetectionReviewException.class,()->reviews.detail(owner.getId(),detection));
  // Historical detections are retained; a zero-match run is not a dismissal.
  assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM detection",Integer.class));
 }
}
