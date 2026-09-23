package com.artworkguard.takedown;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.artworkguard.takedown.TakedownDtos.*;

@Repository
public class TakedownRepository {
 private final JdbcTemplate jdbc; private final ObjectMapper mapper;
 public TakedownRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 public boolean lockConfirmed(UUID owner,UUID detection,long version){
  return !jdbc.query("""
   SELECT d.id FROM detection d JOIN artwork a ON a.id=d.artwork_id JOIN app_user u ON u.id=a.user_id
   WHERE d.id=? AND d.version=? AND d.review_status='CONFIRMED' AND a.user_id=?
    AND a.deleted=FALSE AND u.status='ACTIVE' FOR UPDATE OF d FOR SHARE OF a,u
   """,(r,n)->r.getObject(1,UUID.class),detection,version,owner).isEmpty();
 }
 public Optional<Report> find(UUID owner,UUID detection){
  return jdbc.query("SELECT * FROM takedown_report WHERE owner_id=? AND detection_id=?",(r,n)->{
   try{return new Report(r.getObject("id",UUID.class),r.getObject("detection_id",UUID.class),
    r.getObject("evidence_id",UUID.class),Status.valueOf(r.getString("status")),r.getLong("version"),
    mapper.readTree(r.getString("draft")),r.getString("external_reference"),
    r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());}
   catch(java.io.IOException e){throw new IllegalStateException("Invalid stored draft",e);}
  },owner,detection).stream().findFirst();
 }
 public void create(UUID owner,UUID detection,UUID evidence,String draft){
  jdbc.update("""
   INSERT INTO takedown_report(id,owner_id,detection_id,evidence_id,draft)
   VALUES(?,?,?,?,?::jsonb) ON CONFLICT(detection_id) DO NOTHING
   """,UUID.randomUUID(),owner,detection,evidence,draft);
 }
 public boolean update(UUID owner,UUID detection,Update request){
  return jdbc.update("""
   UPDATE takedown_report SET status=?,external_reference=COALESCE(?,external_reference),version=version+1,updated_at=now()
   WHERE owner_id=? AND detection_id=? AND version=?
   """,request.status().name(),request.externalReference(),owner,detection,request.version())==1;
 }
}
