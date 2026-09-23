package com.artworkguard.embedding;
import com.artworkguard.artwork.domain.Artwork;
import com.artworkguard.artwork.repository.ArtworkRepository;
import com.artworkguard.user.domain.AppUser;
import com.artworkguard.user.repository.UserRepository;
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
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
@Tag("integration") @Testcontainers
@SpringBootTest(properties="artworkguard.embedding.enabled=true")
class EmbeddingIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
 @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
 @Container static KafkaContainer kafka=new KafkaContainer("apache/kafka:3.9.1");
 static HttpServer server;
 static HttpServer startServer(){
  try{
   var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   server.createContext("/v1/embeddings",exchange->{
    byte[] response=new EmbeddingContractTest().valid().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    exchange.getRequestBody().readAllBytes();exchange.getResponseHeaders().add("Content-Type","application/json");
    exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
   });server.start();return server;
  }catch(Exception e){throw new IllegalStateException(e);}
 }
 @AfterAll static void stop(){if(server!=null)server.stop(0);}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
  server=startServer();
  r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
  r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");
  r.add("spring.kafka.bootstrap-servers",kafka::getBootstrapServers);
  String secret=UUID.randomUUID().toString()+UUID.randomUUID();
  r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(secret.getBytes()));
  r.add("artworkguard.embedding.api-key",()->secret);
  r.add("artworkguard.embedding.ai-url",()->"http://127.0.0.1:"+server.getAddress().getPort());
 }
 @Autowired UserRepository users;@Autowired ArtworkRepository artworks;
 @Autowired EmbeddingJobService jobs;@Autowired EmbeddingWorker worker;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;
 @MockitoBean ObjectStorage storage;
 @Test void outboxKafkaAiAndPgvectorPersistExactlyOnce()throws Exception{
  var user=users.saveAndFlush(new AppUser(UUID.randomUUID()+"@example.com","test-hash","Creator"));
  var artwork=artworks.saveAndFlush(new Artwork(UUID.randomUUID(),user.getId(),"Title","",10,10,Instant.now()));
  when(storage.downloadUrl(anyString(),any())).thenReturn("https://private-storage.test/image?signature=not-logged");
  var job=jobs.enqueue(artwork.getId());
  assertThat(jobs.enqueue(artwork.getId()).jobId()).isEqualTo(job.jobId());
  await().atMost(Duration.ofSeconds(60)).untilAsserted(()->assertThat(jobs.status(artwork.getId()).status()).isEqualTo("COMPLETED"));
  assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM artwork_embedding WHERE job_id=?",Integer.class,job.jobId())).isEqualTo(768);
  assertThat(jdbc.queryForObject("SELECT vector_norm(embedding) FROM artwork_embedding WHERE job_id=?",Double.class,job.jobId())).isCloseTo(1.0,within(0.0001));
  String json=jdbc.queryForObject("SELECT payload FROM embedding_outbox WHERE job_id=?",String.class,job.jobId());
  worker.process(EmbeddingEvent.parse(mapper,json));
  assertThat(jdbc.queryForObject("SELECT count(*) FROM artwork_embedding WHERE job_id=?",Integer.class,job.jobId())).isEqualTo(1);
 }
}
