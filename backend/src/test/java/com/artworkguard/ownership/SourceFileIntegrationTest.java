package com.artworkguard.ownership;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") @Testcontainers
class SourceFileIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Test void immutableAttachmentRequiresPendingVerifiedOwner()throws Exception{
  var jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));var repository=new SourceFileRepository(jdbc);
  jdbc.execute("CREATE TABLE app_user(id UUID PRIMARY KEY,status TEXT,email_verified_at TIMESTAMPTZ);CREATE TABLE artwork(id UUID PRIMARY KEY,deleted BOOLEAN);CREATE TABLE ownership_claim(id UUID PRIMARY KEY,artwork_id UUID,owner_id UUID,status TEXT);CREATE TABLE operational_audit(subject_user_id UUID,resource_type TEXT,resource_id UUID,old_state JSONB,new_state JSONB);CREATE FUNCTION reject_evidence_mutation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'immutable'; END; $$");
  try(var input=getClass().getResourceAsStream("/db/migration/V24__ownership_source_file.sql")){jdbc.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
  UUID owner=UUID.randomUUID(),art=UUID.randomUUID(),claim=UUID.randomUUID(),closed=UUID.randomUUID();
  jdbc.update("INSERT INTO app_user VALUES(?,'ACTIVE',now())",owner);jdbc.update("INSERT INTO artwork VALUES(?,FALSE)",art);
  jdbc.update("INSERT INTO ownership_claim VALUES(?,?,?,'PENDING'),(?,?,?,'ACCEPTED')",claim,art,owner,closed,art,owner);
  repository.insert(claim,"PSD","a".repeat(64),41,"HEADER_ONLY","test-key");assertEquals(41,repository.find(claim).orElseThrow().sizeBytes());
  assertThrows(org.springframework.dao.DataAccessException.class,()->repository.insert(claim,"PSD","a".repeat(64),41,"HEADER_ONLY","another-key"));
  assertThrows(org.springframework.dao.DataAccessException.class,()->repository.insert(closed,"PSD","a".repeat(64),41,"HEADER_ONLY","closed-key"));
  assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE ownership_source_file SET sha256=? WHERE claim_id=?","b".repeat(64),claim));
  assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("DELETE FROM ownership_source_file WHERE claim_id=?",claim));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM operational_audit",Integer.class));
 }
}
