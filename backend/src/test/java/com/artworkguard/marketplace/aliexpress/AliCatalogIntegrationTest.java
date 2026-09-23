package com.artworkguard.marketplace.aliexpress;
import com.artworkguard.marketplace.adapter.MockImageLibrary;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import com.artworkguard.product.repository.CatalogRepository;
import com.artworkguard.product.embedding.*;
import com.artworkguard.embedding.AiEmbeddingClient;
import com.artworkguard.storage.ObjectStorage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@Tag("integration") @Testcontainers @SpringBootTest @Import(AliCatalogIntegrationTest.WorkerConfig.class)
class AliCatalogIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
 @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
  r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
  r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");
  String secret=UUID.randomUUID().toString()+UUID.randomUUID();r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(secret.getBytes()));
 }
 @TestConfiguration static class WorkerConfig {
  @Bean ProductEmbeddingWorker testProductWorker(JdbcTemplate jdbc,ObjectStorage storage,AiEmbeddingClient ai,MockImageLibrary images){return new ProductEmbeddingWorker(jdbc,storage,ai,images);}
 }
 @MockitoBean ObjectStorage storage;@MockitoBean AiEmbeddingClient ai;
 @Autowired AliImporter importer;@Autowired CatalogRepository catalog;@Autowired ProductEmbeddingJobService jobs;
 @Autowired ProductEmbeddingWorker worker;@Autowired JdbcTemplate jdbc;@Autowired MockImageLibrary images;
 @Test void realCatalogImagesAreDeduplicatedAndReachVectorStorage(){
  var fixture=images.metadata("forest-mug");byte[] bytes=images.read(fixture.sourceKey());
  String key="catalog-imports/aliexpress/"+fixture.imageHash()+".png";
  var listing=new AliDtos.Listing("100001","Test art","100",new java.math.BigDecimal("12.50"),"USD","https://ae01.alicdn.com/kf/test.png");
  var prepared=new AliDtos.Prepared(listing,key,fixture.imageHash(),fixture.width(),fixture.height(),bytes.length);
  var first=importer.persist(List.of(prepared));var second=importer.persist(List.of(prepared));
  assertEquals(first.productIds(),second.productIds());assertEquals(1,catalog.products(MarketplaceCode.ALIEXPRESS,"",0,20).total());
  UUID product=first.productIds().getFirst();var image=catalog.images(product).getFirst();
  assertEquals("STORED_PNG",image.sourceType());assertEquals(bytes.length,image.sizeBytes());
  when(storage.readUpload(key,"image/png",bytes.length)).thenReturn(bytes);
  when(storage.downloadUrl(anyString(),any())).thenReturn("https://storage.test/image.png");
  float[] values=new float[768];values[0]=1;String p="0123456789abcdef",d="fedcba9876543210";when(ai.embedRegions(anyString())).thenReturn(new AiEmbeddingClient.RegionSet("fixed-overlap-5-v1",values,"phash32-dhash9-luma-v1",p,d,List.of(
   new AiEmbeddingClient.RegionVector("CENTER",.175,.175,.65,.65,values,p,d),new AiEmbeddingClient.RegionVector("TOP_LEFT",0,0,.65,.65,values,p,d),
   new AiEmbeddingClient.RegionVector("TOP_RIGHT",.35,0,.65,.65,values,p,d),new AiEmbeddingClient.RegionVector("BOTTOM_LEFT",0,.35,.65,.65,values,p,d),
   new AiEmbeddingClient.RegionVector("BOTTOM_RIGHT",.35,.35,.65,.65,values,p,d))));
  var job=jobs.status(image.id());
  worker.process(ProductEmbeddingEvent.create(job.jobId(),image.id(),job.generation()));
  assertEquals("COMPLETED",jobs.status(image.id()).status());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM current_product_image_embedding",Integer.class));
  verify(storage).readUpload(key,"image/png",bytes.length);
 }
}
