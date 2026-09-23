package com.artworkguard.social;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import static com.artworkguard.social.SocialDtos.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") @Testcontainers
class SocialIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Test void bindingExpiryRevocationAndAudit()throws Exception{
  var jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));var repository=new SocialRepository(jdbc);
  jdbc.execute("CREATE TABLE app_user(id UUID PRIMARY KEY,status TEXT,role TEXT,email_verified_at TIMESTAMPTZ);CREATE TABLE operational_audit(subject_user_id UUID,resource_type TEXT,resource_id UUID,old_state JSONB,new_state JSONB)");
  try(var input=getClass().getResourceAsStream("/db/migration/V23__social_account_check.sql")){jdbc.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
  UUID owner=UUID.randomUUID(),admin=UUID.randomUUID(),id=UUID.randomUUID();String url="https://example.com/profile",code="ArtworkGuard-"+"a".repeat(32);
  jdbc.update("INSERT INTO app_user VALUES(?,'ACTIVE','ROLE_USER',now()),(?,'ACTIVE','ROLE_ADMIN',now())",owner,admin);
  assertTrue(repository.canStart(owner,url));repository.insert(id,owner,url,code);assertFalse(repository.canStart(owner,url));
  assertNull(repository.list(null,0,20).items().getFirst().challenge());assertEquals(0,repository.list(admin,0,20).totalElements());
  assertFalse(repository.review(id,admin,new Review(0L,true,"wrong",true,"checked")));
  assertTrue(repository.review(id,admin,new Review(0L,true,code,true,"checked")));
  assertFalse(repository.review(id,admin,new Review(0L,true,code,true,"again")));
  assertEquals(State.VERIFIED,repository.find(id).orElseThrow().state());assertFalse(repository.revoke(admin,id,1));assertTrue(repository.revoke(owner,id,1));
  assertTrue(repository.canStart(owner,url));assertEquals(State.REVOKED,repository.find(id).orElseThrow().state());
  UUID expired=UUID.randomUUID();jdbc.update("INSERT INTO social_account_check(id,owner_id,profile_url,challenge,created_at,expires_at) VALUES(?,?,?,'expired-code',now()-interval '25 hours',now()-interval '1 hour')",expired,owner,url);
  assertEquals(State.EXPIRED,repository.find(expired).orElseThrow().state());assertFalse(repository.review(expired,admin,new Review(0L,true,"expired-code",true,"checked")));
  assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE social_account_check SET profile_url='https://changed.example/' WHERE id=?",id));
  assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM operational_audit",Integer.class));
 }
}
