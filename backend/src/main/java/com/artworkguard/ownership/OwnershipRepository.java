package com.artworkguard.ownership;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.*;
import static com.artworkguard.ownership.OwnershipDtos.*;
@Repository
public class OwnershipRepository {
 private final JdbcTemplate jdbc;private final NamedParameterJdbcTemplate named;
 public OwnershipRepository(JdbcTemplate jdbc){this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc);}
 public boolean owned(UUID owner,UUID artwork,boolean lock){
  return !jdbc.query("SELECT a.id FROM artwork a JOIN app_user u ON u.id=a.user_id WHERE a.id=? AND a.user_id=? AND a.deleted=FALSE AND u.status='ACTIVE'"+(lock?" FOR UPDATE OF a FOR SHARE OF u":""),
   (r,n)->r.getObject(1,UUID.class),artwork,owner).isEmpty();
 }
 private Claim map(ResultSet r,int n)throws SQLException{
  var reviewed=r.getTimestamp("reviewed_at");return new Claim(r.getObject("id",UUID.class),r.getObject("artwork_id",UUID.class),r.getObject("owner_id",UUID.class),
   r.getString("publication_url"),r.getString("statement"),Status.valueOf(r.getString("status")),r.getString("review_reason"),
   r.getObject("reviewer_id",UUID.class),r.getLong("version"),r.getTimestamp("submitted_at").toInstant(),reviewed==null?null:reviewed.toInstant());
 }
 private static final String FROM=" FROM ownership_claim c JOIN artwork a ON a.id=c.artwork_id JOIN app_user u ON u.id=c.owner_id WHERE a.deleted=FALSE AND u.status='ACTIVE'";
 public Optional<Claim> find(UUID id){return jdbc.query("SELECT c.*"+FROM+" AND c.id=?",this::map,id).stream().findFirst();}
 public boolean insert(UUID id,UUID owner,UUID artwork,Submit request){
  return jdbc.update("INSERT INTO ownership_claim(id,owner_id,artwork_id,publication_url,statement) VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",id,owner,artwork,request.publicationUrl(),request.statement())==1;
 }
 public Page list(UUID owner,UUID artwork,Status status,int page,int size){
  var p=new MapSqlParameterSource("owner",owner).addValue("artwork",artwork).addValue("status",status==null?null:status.name()).addValue("limit",size).addValue("offset",(long)page*size);
  String where=FROM+(owner==null?"":" AND c.owner_id=:owner")+(artwork==null?"":" AND c.artwork_id=:artwork")+(status==null?"":" AND c.status=:status");
  long count=named.queryForObject("SELECT count(*)"+where,p,Long.class);
  var rows=named.query("SELECT c.*"+where+" ORDER BY c.submitted_at DESC,c.id LIMIT :limit OFFSET :offset",p,this::map);
  return new Page(rows,page,size,count,(count+size-1)/size);
 }
 public boolean review(UUID id,UUID admin,Review request){
  return jdbc.update("""
   UPDATE ownership_claim c SET status=?,review_reason=?,reviewer_id=?,reviewed_at=now(),version=version+1
   WHERE c.id=? AND c.version=? AND c.status='PENDING' AND c.owner_id<>?
   AND EXISTS(SELECT 1 FROM artwork a JOIN app_user u ON u.id=a.user_id WHERE a.id=c.artwork_id AND a.deleted=FALSE AND u.status='ACTIVE')
   """,request.status().name(),request.reason(),admin,id,request.version(),admin)==1;
 }
}
