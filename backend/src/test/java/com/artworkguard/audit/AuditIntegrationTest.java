package com.artworkguard.audit;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") @Testcontainers
class AuditIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Test void triggersCursorFiltersAndImmutability()throws Exception{
  var jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
  jdbc.execute("CREATE TABLE operational_audit(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,subject_user_id UUID,resource_type VARCHAR(30),resource_id UUID,old_state JSONB,new_state JSONB,recorded_at TIMESTAMPTZ DEFAULT clock_timestamp());CREATE TABLE app_user(id UUID PRIMARY KEY,email_verified_at TIMESTAMPTZ);CREATE TABLE artwork(id UUID PRIMARY KEY,user_id UUID,title TEXT,description TEXT,monitoring_enabled BOOLEAN,deleted BOOLEAN,version BIGINT);CREATE FUNCTION reject_evidence_mutation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'immutable'; END; $$;CREATE TRIGGER immutable BEFORE UPDATE OR DELETE ON operational_audit FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation()");
  try(var input=getClass().getResourceAsStream("/db/migration/V25__artwork_and_email_audit.sql")){jdbc.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
  UUID owner=UUID.randomUUID(),art=UUID.randomUUID();jdbc.update("INSERT INTO app_user VALUES(?,NULL)",owner);jdbc.update("INSERT INTO artwork VALUES(?,?,?, ?,FALSE,FALSE,0)",art,owner,"private title","private description");
  jdbc.update("UPDATE artwork SET title='changed',version=1 WHERE id=?",art);jdbc.update("UPDATE artwork SET deleted=TRUE,version=2 WHERE id=?",art);jdbc.update("UPDATE app_user SET email_verified_at=now() WHERE id=?",owner);
  var repository=new AuditRepository(jdbc,new ObjectMapper());var first=repository.list(null,owner,null,2);assertEquals(2,first.items().size());assertNotNull(first.nextBeforeId());
  new AuditWriter(jdbc).record(AuditWriter.Action.LOGIN,owner,owner,owner);
  var second=repository.list(first.nextBeforeId(),owner,null,2);assertEquals(2,second.items().size());assertNull(second.nextBeforeId());
  assertTrue(second.items().stream().allMatch(e->e.id()<first.nextBeforeId()));assertFalse(second.items().getLast().newState().toString().contains("private"));
  assertEquals(1,repository.list(null,owner,AuditRepository.Type.LOGIN,10).items().size());assertTrue(repository.list(null,UUID.randomUUID(),null,10).items().isEmpty());
  assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("DELETE FROM operational_audit"));
 }
}
