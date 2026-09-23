package com.artworkguard.audit;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;
@Repository
public class AuditRepository {
 public enum Type { LOGIN,ARTWORK_CREATED,ARTWORK_UPDATED,ARTWORK_DELETED,EMAIL_VERIFICATION,
  SUBSCRIPTION,DETECTION_REVIEW,TAKEDOWN_REPORT,OWNERSHIP_CLAIM,OWNERSHIP_SOURCE_FILE,SOCIAL_ACCOUNT_CHECK,ADMIN_AUDIT_READ,SOURCE_FILE_DOWNLOADED }
 public record Event(long id,UUID subjectUserId,String resourceType,UUID resourceId,JsonNode oldState,JsonNode newState,Instant recordedAt){}
 public record Page(List<Event> items,Long nextBeforeId){}
 private final NamedParameterJdbcTemplate jdbc;private final ObjectMapper mapper;
 public AuditRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=new NamedParameterJdbcTemplate(jdbc);this.mapper=mapper;}
 public Page list(Long beforeId,UUID subject,Type type,int limit){
  var p=new MapSqlParameterSource("before",beforeId).addValue("subject",subject).addValue("type",type==null?null:type.name()).addValue("limit",limit+1);
  String where=" WHERE 1=1"+(beforeId==null?"":" AND id<:before")+(subject==null?"":" AND subject_user_id=:subject")+(type==null?"":" AND resource_type=:type");
  var rows=jdbc.query("SELECT * FROM operational_audit"+where+" ORDER BY id DESC LIMIT :limit",p,(r,n)->{
   try{String old=r.getString("old_state");return new Event(r.getLong("id"),r.getObject("subject_user_id",UUID.class),r.getString("resource_type"),r.getObject("resource_id",UUID.class),old==null?null:mapper.readTree(old),mapper.readTree(r.getString("new_state")),r.getTimestamp("recorded_at").toInstant());}
   catch(java.io.IOException e){throw new IllegalStateException("Invalid audit state",e);}
  });
  boolean more=rows.size()>limit;var items=more?List.copyOf(rows.subList(0,limit)):List.copyOf(rows);
  return new Page(items,more?items.getLast().id():null);
 }
}
