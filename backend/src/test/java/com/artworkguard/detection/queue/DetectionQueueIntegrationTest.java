package com.artworkguard.detection.queue;
import com.artworkguard.artwork.domain.Artwork;
import com.artworkguard.artwork.repository.ArtworkRepository;
import com.artworkguard.user.domain.AppUser;
import com.artworkguard.user.repository.UserRepository;
import com.artworkguard.embedding.*;
import com.artworkguard.product.embedding.ProductEmbeddingJobService;
import com.artworkguard.marketplace.adapter.MockMarketplaceAdapter;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import com.artworkguard.marketplace.service.CatalogImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
@Tag("integration") @Testcontainers @SpringBootTest(properties="artworkguard.detection.enabled=true")
class DetectionQueueIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
 @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
 @Container static KafkaContainer kafka=new KafkaContainer("apache/kafka:3.9.1");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
  r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
  r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");
  r.add("spring.kafka.bootstrap-servers",kafka::getBootstrapServers);
  String secret=UUID.randomUUID().toString()+UUID.randomUUID();r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(secret.getBytes()));
 }
 @Autowired JdbcTemplate jdbc;@Autowired UserRepository users;@Autowired ArtworkRepository artworks;
 @Autowired EmbeddingJobService artworkJobs;@Autowired ProductEmbeddingJobService productJobs;
 @Autowired CatalogImportService importer;@Autowired MockMarketplaceAdapter adapter;
 @Autowired DetectionJobService jobs;@Autowired DetectionWorker worker;@Autowired ObjectMapper mapper;
 String vector(){var v=new StringJoiner(",","[","]");v.add("1");for(int i=1;i<768;i++)v.add("0");return v.toString();}
 void productVector(UUID image){
  UUID job=productJobs.enqueue(image).jobId();
  jdbc.update("""
   INSERT INTO product_image_embedding(id,job_id,image_id,image_hash,model,model_version,preprocessing_version,embedding)
   SELECT ?,?,id,image_hash,?,?,?,CAST(? AS vector) FROM product_image WHERE id=?
   """,UUID.randomUUID(),job,EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING,vector(),image);
  String hash=jdbc.queryForObject("SELECT image_hash FROM product_image WHERE id=?",String.class,image);
  jdbc.update("UPDATE product_image SET storage_key=?,storage_size_bytes=123 WHERE id=?","product-images/"+image+"/"+hash+".png",image);
 }
 @Test void manualAndCompletionSignalsReachKafkaAndCreateDetection(){
  var user=users.saveAndFlush(new AppUser(UUID.randomUUID()+"@example.com","test-hash","Owner"));
  var artwork=artworks.saveAndFlush(new Artwork(UUID.randomUUID(),user.getId(),"Source","",512,512,Instant.now()));
  UUID embeddingJob=artworkJobs.enqueue(artwork.getId()).jobId();
  jdbc.update("""
   INSERT INTO artwork_embedding(id,job_id,artwork_id,model,model_version,preprocessing_version,embedding)
   VALUES(?,?,?,?,?,?,CAST(? AS vector))
   """,UUID.randomUUID(),embeddingJob,artwork.getId(),EmbeddingModel.ID,EmbeddingModel.VERSION,EmbeddingModel.PREPROCESSING,vector());
  importer.persist(MarketplaceCode.MOCK,adapter.searchProducts("forest",1));
  UUID image=jdbc.queryForObject("SELECT id FROM product_image",UUID.class);
  productVector(image);
  var queued=jobs.request(user.getId(),artwork.getId(),100);
  await().atMost(Duration.ofSeconds(60)).untilAsserted(()->assertEquals("COMPLETED",jobs.status(user.getId(),artwork.getId(),queued.jobId()).status()));
  assertNotNull(jobs.status(user.getId(),artwork.getId(),queued.jobId()).runId());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM detection",Integer.class));
  String json=jdbc.queryForObject("SELECT payload FROM detection_outbox WHERE job_id=?",String.class,queued.jobId());
  worker.process(DetectionEvent.parse(mapper,json));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM detection_run",Integer.class));
  await().atMost(Duration.ofSeconds(30)).untilAsserted(()->assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM detection_signal WHERE completed=FALSE",Integer.class)));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM detection_job WHERE automatic=TRUE",Integer.class));
  jdbc.update("UPDATE artwork SET monitoring_enabled=TRUE WHERE id=?",artwork.getId());
  UUID extra=UUID.randomUUID();
  jdbc.update("""
   INSERT INTO product_image(id,product_id,external_image_id,original_url,source_type,source_key,width,height,image_hash)
   SELECT ?,product_id,'extra',original_url,source_type,source_key,width,height,image_hash FROM product_image WHERE id=?
   """,extra,image);
  productVector(extra);
  await().atMost(Duration.ofSeconds(60)).untilAsserted(()->assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM detection_job WHERE automatic=TRUE AND status='COMPLETED'",Integer.class)));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM detection",Integer.class));
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM detection_run",Integer.class));
 }
}
