package com.artworkguard.social;
import com.artworkguard.auth.service.AuthException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.*;
import static com.artworkguard.social.SocialDtos.*;
@Repository
public class SocialRepository {
 private final JdbcTemplate jdbc;private final NamedParameterJdbcTemplate named;
 public SocialRepository(JdbcTemplate jdbc){this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc);}
 public void lockOwner(UUID owner){if(jdbc.query("SELECT id FROM app_user WHERE id=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getObject(1,UUID.class),owner).isEmpty())throw AuthException.unauthorized();}
 public boolean canStart(UUID owner,String url){
  return jdbc.queryForObject("""
   SELECT count(*) FILTER(WHERE status='PENDING' AND expires_at>clock_timestamp())<5
    AND count(*) FILTER(WHERE profile_url=? AND (status='PENDING' AND expires_at>clock_timestamp()
     OR status='VERIFIED' AND verified_until>clock_timestamp()))=0 FROM social_account_check WHERE owner_id=?
   """,Boolean.class,url,owner);
 }
 public void insert(UUID id,UUID owner,String url,String challenge){jdbc.update("INSERT INTO social_account_check(id,owner_id,profile_url,challenge) VALUES(?,?,?,?)",id,owner,url,challenge);}
 private static final String SELECT="""
  SELECT c.*,CASE WHEN c.status='PENDING' AND c.expires_at<=clock_timestamp()
   OR c.status='VERIFIED' AND c.verified_until<=clock_timestamp() THEN 'EXPIRED' ELSE c.status END AS effective_state
  FROM social_account_check c JOIN app_user u ON u.id=c.owner_id WHERE u.status='ACTIVE'
  """;
 private Check map(ResultSet r,int n)throws SQLException{
  var until=r.getTimestamp("verified_until");return new Check(r.getObject("id",UUID.class),r.getObject("owner_id",UUID.class),
   r.getString("profile_url"),r.getString("challenge"),State.valueOf(r.getString("effective_state")),r.getLong("version"),
   r.getTimestamp("created_at").toInstant(),r.getTimestamp("expires_at").toInstant(),until==null?null:until.toInstant(),r.getObject("reviewer_id",UUID.class),r.getString("reason"));
 }
 public Optional<Check> find(UUID id){return jdbc.query(SELECT+" AND c.id=?",this::map,id).stream().findFirst();}
 public Page list(UUID owner,int page,int size){
  var params=new MapSqlParameterSource("owner",owner).addValue("offset",(long)page*size).addValue("limit",size);
  String query=SELECT+(owner==null?" AND c.status='PENDING' AND c.expires_at>clock_timestamp()":" AND c.owner_id=:owner");
  long count=named.queryForObject("SELECT count(*) FROM ("+query+") grouped",params,Long.class);
  var items=named.query(query+" ORDER BY c.created_at DESC,c.id LIMIT :limit OFFSET :offset",params,this::map);
  if(owner==null)items=items.stream().map(Check::withoutChallenge).toList();
  return new Page(items,page,size,count,(count+size-1)/size);
 }
 public boolean review(UUID id,UUID admin,Review request){return jdbc.update("""
  UPDATE social_account_check SET status=?,reviewer_id=?,reason=?,version=version+1,
   verified_until=CASE WHEN ? THEN clock_timestamp()+interval '30 days' ELSE NULL END
  WHERE id=? AND version=? AND owner_id<>? AND status='PENDING' AND expires_at>clock_timestamp()
   AND (?=FALSE OR challenge=?)
  """,request.approved()?"VERIFIED":"REJECTED",admin,request.reason(),request.approved(),id,request.version(),admin,request.approved(),request.observedCode())==1;}
 public boolean revoke(UUID owner,UUID id,long version){return jdbc.update("UPDATE social_account_check SET status='REVOKED',version=version+1 WHERE id=? AND owner_id=? AND version=? AND status IN ('PENDING','VERIFIED')",id,owner,version)==1;}
}
