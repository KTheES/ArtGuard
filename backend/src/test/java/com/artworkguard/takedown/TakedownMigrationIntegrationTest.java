package com.artworkguard.takedown;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration") @Testcontainers
class TakedownMigrationIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Test void migrationGuardsSnapshotsAndAuditsTransitions()throws Exception{
  try(var connection=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());var sql=connection.createStatement()){
   sql.execute("CREATE TABLE app_user(id UUID PRIMARY KEY); CREATE TABLE artwork(id UUID PRIMARY KEY,user_id UUID); CREATE TABLE detection(id UUID PRIMARY KEY,artwork_id UUID,review_status TEXT); CREATE TABLE evidence_snapshot(id UUID PRIMARY KEY,detection_id UUID); CREATE TABLE operational_audit(subject_user_id UUID,resource_type TEXT,resource_id UUID,old_state JSONB,new_state JSONB)");
   try(var input=getClass().getResourceAsStream("/db/migration/V19__takedown_report.sql")){sql.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
   UUID owner=UUID.randomUUID(),art=UUID.randomUUID(),detection=UUID.randomUUID(),evidence=UUID.randomUUID(),report=UUID.randomUUID();
   sql.execute("INSERT INTO app_user VALUES('"+owner+"'); INSERT INTO artwork VALUES('"+art+"','"+owner+"'); INSERT INTO detection VALUES('"+detection+"','"+art+"','CONFIRMED'); INSERT INTO evidence_snapshot VALUES('"+evidence+"','"+detection+"')");
   sql.execute("INSERT INTO takedown_report(id,owner_id,detection_id,evidence_id,draft) VALUES('"+report+"','"+owner+"','"+detection+"','"+evidence+"','{}')");
   assertThrows(SQLException.class,()->sql.execute("UPDATE takedown_report SET draft='{\"changed\":true}',version=1,status='WITHDRAWN'"));
   assertThrows(SQLException.class,()->sql.execute("UPDATE takedown_report SET status='SUBMITTED',version=1"));
   sql.execute("UPDATE takedown_report SET status='SUBMITTED',version=1,external_reference='receipt'");
   sql.execute("UPDATE takedown_report SET status='RESOLVED',version=2");
   assertThrows(SQLException.class,()->sql.execute("UPDATE takedown_report SET status='DRAFT',version=3"));
   assertThrows(SQLException.class,()->sql.execute("DELETE FROM takedown_report"));
   try(var result=sql.executeQuery("SELECT count(*) FROM operational_audit")){result.next();assertEquals(3,result.getInt(1));}
  }
 }
}
