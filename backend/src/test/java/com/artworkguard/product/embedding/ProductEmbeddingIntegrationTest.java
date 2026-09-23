package com.artworkguard.product.embedding;
import com.artworkguard.embedding.*;
import com.artworkguard.marketplace.adapter.MockMarketplaceAdapter;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import com.artworkguard.marketplace.service.CatalogImportService;
import com.artworkguard.storage.ObjectStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
@Tag("integration") @Testcontainers @SpringBootTest(properties="artworkguard.embedding.enabled=true")
class ProductEmbeddingIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
 @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
 @Container static KafkaContainer kafka=new KafkaContainer("apache/kafka:3.9.1");
 static HttpServer server;
 @AfterAll static void stop(){if(server!=null)server.stop(0);}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r)throws Exception{
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/embeddings",exchange->{
   var mapper=new ObjectMapper();var response=mapper.createObjectNode();
   response.put("model","dinov2").put("modelId",EmbeddingModel.ID).put("version",EmbeddingModel.VERSION)
    .put("preprocessingVersion",EmbeddingModel.PREPROCESSING).put("dimension",768).put("normalized",true);
   var vector=response.putArray("embedding");for(int i=0;i<768;i++)vector.add(i==0?1.0:0.0);
   exchange.getRequestBody().readAllBytes();byte[] bytes=mapper.writeValueAsBytes(response);
   exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
   exchange.getResponseBody().write(bytes);exchange.close();
  });server.start();
  r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
  r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");
  r.add("spring.kafka.bootstrap-servers",kafka::getBootstrapServers);
  String secret=UUID.randomUUID().toString()+UUID.randomUUID();
  r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(secret.getBytes()));
  r.add("artworkguard.embedding.api-key",()->secret);
  r.add("artworkguard.embedding.ai-url",()->"http://127.0.0.1:"+server.getAddress().getPort());
 }
 @Autowired CatalogImportService importer;@Autowired MockMarketplaceAdapter adapter;
 @Autowired ProductEmbeddingJobService jobs;@Autowired ProductEmbeddingWorker worker;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;
 @MockitoBean ObjectStorage storage;
 @Test void collectionOutboxKafkaAiAndVectorStorageAreIdempotent(){
  when(storage.downloadUrl(anyString(),any())).thenReturn("https://private-storage.test/mock.png");
  var listings=adapter.searchProducts("",20);
  importer.persist(MarketplaceCode.MOCK,listings);
  var ids=jdbc.query("SELECT id FROM product_image ORDER BY id",(rs,n)->rs.getObject(1,UUID.class));
  await().atMost(Duration.ofSeconds(60)).untilAsserted(()->{
   for(var id:ids)assertEquals("COMPLETED",jobs.status(id).status());
  });
  assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM current_product_image_embedding",Integer.class));
  assertEquals(768,jdbc.queryForObject("SELECT min(vector_dims(embedding)) FROM product_image_embedding",Integer.class));
  importer.persist(MarketplaceCode.MOCK,listings);
  assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM product_embedding_job",Integer.class));
  assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM product_embedding_outbox",Integer.class));
  String event=jdbc.queryForObject("SELECT payload FROM product_embedding_outbox ORDER BY created_at LIMIT 1",String.class);
  worker.process(ProductEmbeddingEvent.parse(mapper,event));
  assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM product_image_embedding",Integer.class));
  UUID id=ids.getFirst();
  jdbc.update("UPDATE product_image SET active=FALSE WHERE id=?",id);
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM current_product_image_embedding",Integer.class));
  jdbc.update("UPDATE product_image SET active=TRUE,image_hash=? WHERE id=?","a".repeat(64),id);
  assertEquals("NOT_REQUESTED",jobs.status(id).status());
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM current_product_image_embedding",Integer.class));
 }
}
