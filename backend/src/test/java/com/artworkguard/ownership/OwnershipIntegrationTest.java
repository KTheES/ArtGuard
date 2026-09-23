package com.artworkguard.ownership;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import static com.artworkguard.ownership.OwnershipDtos.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") @Testcontainers
class OwnershipIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Test void pendingUniquenessImmutableReviewAndAudit()throws Exception{
  var jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));var repository=new OwnershipRepository(jdbc);
  jdbc.execute("CREATE TABLE app_user(id UUID PRIMARY KEY,status TEXT,role TEXT,email_verified_at TIMESTAMPTZ);CREATE TABLE artwork(id UUID PRIMARY KEY,user_id UUID,deleted BOOLEAN);CREATE TABLE operational_audit(subject_user_id UUID,resource_type TEXT,resource_id UUID,old_state JSONB,new_state JSONB)");
  try(var input=getClass().getResourceAsStream("/db/migration/V22__ownership_claim.sql")){jdbc.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
  UUID owner=UUID.randomUUID(),admin=UUID.randomUUID(),art=UUID.randomUUID(),id=UUID.randomUUID();
  jdbc.update("INSERT INTO app_user VALUES(?,'ACTIVE','ROLE_USER',now()),(?,'ACTIVE','ROLE_ADMIN',now())",owner,admin);jdbc.update("INSERT INTO artwork VALUES(?,?,FALSE)",art,owner);
  var request=new Submit("https://example.com/art","source",true);
  assertTrue(repository.insert(id,owner,art,request));assertFalse(repository.insert(UUID.randomUUID(),owner,art,request));
  assertEquals(0,repository.list(admin,art,null,0,20).totalElements());
  assertFalse(repository.review(id,owner,new Review(Status.ACCEPTED,"self",0L)));
  assertTrue(repository.review(id,admin,new Review(Status.REJECTED,"insufficient",0L)));
  assertFalse(repository.review(id,admin,new Review(Status.ACCEPTED,"retry",0L)));
  assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE ownership_claim SET statement='changed' WHERE id=?",id));
  assertTrue(repository.insert(UUID.randomUUID(),owner,art,request));assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM operational_audit",Integer.class));
  assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("DELETE FROM ownership_claim WHERE id=?",id));
 }
}
