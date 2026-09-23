package com.artworkguard.marketplace;
import com.artworkguard.marketplace.adapter.*;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import com.artworkguard.marketplace.service.CatalogImportService;
import com.artworkguard.product.repository.CatalogRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") @Testcontainers @SpringBootTest
class CatalogIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
 @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
  r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
  r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");
  String secret=UUID.randomUUID().toString()+UUID.randomUUID();
  r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(secret.getBytes()));
 }
 @Autowired CatalogImportService importer;@Autowired MockMarketplaceAdapter adapter;@Autowired CatalogRepository repository;@Autowired JdbcTemplate jdbc;
 @Test void repeatCollectionKeepsIdsAndFirstSeenAndUpdatesPrice(){
  var listings=adapter.searchProducts("",20);
  var first=importer.persist(MarketplaceCode.MOCK,listings);
  var before=repository.product(first.productIds().getFirst()).orElseThrow().product().firstSeenAt();
  var second=importer.persist(MarketplaceCode.MOCK,listings);
  assertEquals(first.productIds(),second.productIds());
  assertEquals(before,repository.product(first.productIds().getFirst()).orElseThrow().product().firstSeenAt());
  assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM product",Integer.class));
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM seller",Integer.class));
  assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM product_image",Integer.class));
  assertEquals(2,repository.products(MarketplaceCode.MOCK,"moon",0,20).total());
  assertEquals(0,repository.products(null,"%",0,20).total());
  var p=adapter.getProduct("forest-mug");
  var changed=new MarketplaceListing(p.externalProductId(),p.title(),p.productUrl(),new java.math.BigDecimal("22.00"),p.currency(),p.seller(),p.images());
  var id=importer.persist(MarketplaceCode.MOCK,List.of(changed)).productIds().getFirst();
  assertEquals(new java.math.BigDecimal("22.00"),repository.product(id).orElseThrow().product().price());
  assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM product",Integer.class));
  var image=repository.images(id).getFirst();
  assertTrue(repository.image(UUID.randomUUID(),image.id()).isEmpty());
 }
}
