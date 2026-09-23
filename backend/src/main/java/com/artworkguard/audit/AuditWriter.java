package com.artworkguard.audit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class AuditWriter {
 public enum Action { LOGIN,ADMIN_AUDIT_READ,SOURCE_FILE_DOWNLOADED }
 private final JdbcTemplate jdbc;public AuditWriter(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void record(Action action,UUID actor,UUID subject,UUID resource){
  jdbc.update("""
   INSERT INTO operational_audit(subject_user_id,resource_type,resource_id,old_state,new_state)
   VALUES(?,?,?,'{}'::jsonb,jsonb_build_object('actorId',?::text))
   """,subject,action.name(),resource,actor.toString());
 }
}
