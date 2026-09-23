package com.artworkguard.seller;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") @Testcontainers
class SellerIntelligenceIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Test void scopedCountsExclusionsRankingAndPaging(){
  var source=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
  var jdbc=new JdbcTemplate(source);var repository=new SellerIntelligenceRepository(source);
  jdbc.execute("CREATE TABLE app_user(id UUID PRIMARY KEY,status TEXT); CREATE TABLE artwork(id UUID PRIMARY KEY,user_id UUID,deleted BOOLEAN); CREATE TABLE marketplace(id UUID PRIMARY KEY,code TEXT); CREATE TABLE seller(id UUID PRIMARY KEY,marketplace_id UUID,seller_name TEXT,external_seller_id TEXT); CREATE TABLE product(id UUID PRIMARY KEY,seller_id UUID); CREATE TABLE detection(id UUID PRIMARY KEY,artwork_id UUID,product_id UUID,review_status TEXT,first_detected_at TIMESTAMPTZ,last_detected_at TIMESTAMPTZ)");
  UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),inactive=UUID.randomUUID();
  for(UUID user:new UUID[]{owner,other,inactive})jdbc.update("INSERT INTO app_user VALUES(?,?)",user,user.equals(inactive)?"SUSPENDED":"ACTIVE");
  UUID art=artwork(jdbc,owner,false),otherArt=artwork(jdbc,other,false),deleted=artwork(jdbc,owner,true),inactiveArt=artwork(jdbc,inactive,false);
  UUID ali=seller(jdbc,"ALIEXPRESS"),etsy=seller(jdbc,"ETSY"),mock=seller(jdbc,"MOCK");
  UUID p=product(jdbc,ali),p2=product(jdbc,ali),p3=product(jdbc,etsy),pm=product(jdbc,mock);
  detection(jdbc,art,p,"CONFIRMED");detection(jdbc,art,p2,"NEW");detection(jdbc,otherArt,p,"CONFIRMED");
  detection(jdbc,art,p3,"NEW");detection(jdbc,deleted,p,"CONFIRMED");detection(jdbc,inactiveArt,p,"CONFIRMED");
  detection(jdbc,art,p,"DISMISSED");detection(jdbc,art,pm,"CONFIRMED");
  var own=repository.list(owner,null,0,20);assertEquals(2,own.totalElements());
  var first=own.items().getFirst();assertEquals(ali,first.sellerId());assertEquals(2,first.detectionCount());
  assertEquals(1,first.creatorCount());assertEquals(1,first.marketplaceCount());assertEquals(12,first.riskScore());
  assertEquals(2,first.productCount());assertEquals(1,first.artworkCount());assertNotNull(first.firstSeen());assertNotNull(first.lastSeen());
  var global=repository.list(null,"ALIEXPRESS",0,20).items().getFirst();
  assertEquals(3,global.detectionCount());assertEquals(2,global.creatorCount());assertEquals(22,global.riskScore());
  assertEquals(etsy,repository.list(owner,null,1,1).items().getFirst().sellerId());
  assertTrue(repository.list(owner,null,2,1).items().isEmpty());assertEquals(0,repository.list(UUID.randomUUID(),null,0,20).totalElements());
  assertTrue(repository.list(owner,"MOCK",0,20).items().isEmpty());
  for(int i=0;i<12;i++)detection(jdbc,otherArt,product(jdbc,ali),"CONFIRMED");
  assertEquals(100,repository.list(null,"ALIEXPRESS",0,20).items().getFirst().riskScore());
  assertEquals(12,repository.list(owner,"ALIEXPRESS",0,20).items().getFirst().riskScore());
 }
 UUID artwork(JdbcTemplate jdbc,UUID user,boolean deleted){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO artwork VALUES(?,?,?)",id,user,deleted);return id;}
 UUID seller(JdbcTemplate jdbc,String code){UUID market=UUID.randomUUID(),id=UUID.randomUUID();jdbc.update("INSERT INTO marketplace VALUES(?,?)",market,code);jdbc.update("INSERT INTO seller VALUES(?,?,?,?)",id,market,"Same seller name","same-external-id");return id;}
 UUID product(JdbcTemplate jdbc,UUID seller){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO product VALUES(?,?)",id,seller);return id;}
 void detection(JdbcTemplate jdbc,UUID artwork,UUID product,String status){jdbc.update("INSERT INTO detection VALUES(?,?,?,?,now()-interval '1 day',now())",UUID.randomUUID(),artwork,product,status);}
}
