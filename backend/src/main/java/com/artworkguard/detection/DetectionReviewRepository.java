package com.artworkguard.detection;
import com.artworkguard.detection.DetectionReviewDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.*;
@Repository
public class DetectionReviewRepository {
 private final JdbcTemplate jdbc;private final NamedParameterJdbcTemplate named;
 public DetectionReviewRepository(JdbcTemplate jdbc){this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc);}
 private static final String FROM="""
  FROM detection d JOIN artwork a ON a.id=d.artwork_id JOIN app_user u ON u.id=a.user_id
  JOIN product p ON p.id=d.product_id JOIN marketplace m ON m.id=p.marketplace_id
  JOIN product_image_embedding e ON e.id=d.product_embedding_id
  LEFT JOIN product_image_region_embedding r ON r.id=d.product_region_embedding_id
  LEFT JOIN current_product_image_embedding c ON c.id=e.id
  """;
 private static final String SELECT="""
  SELECT d.*,a.title AS artwork_title,p.title AS product_title,p.product_url,m.code,
   e.image_id,e.image_hash,(c.id IS NOT NULL) AS current_evidence,
   r.region_key,r.region_scheme,r.x AS region_x,r.y AS region_y,r.width AS region_width,r.height AS region_height
  """;
 private static final String OWNED=" WHERE a.user_id=:owner AND a.deleted=FALSE AND u.status='ACTIVE'";
 private Detail map(ResultSet r,int n)throws SQLException{
  var reviewed=r.getTimestamp("reviewed_at");
  return new Detail(new Item(r.getObject("id",UUID.class),r.getObject("artwork_id",UUID.class),r.getString("artwork_title"),
   r.getObject("product_id",UUID.class),r.getString("product_title"),r.getString("product_url"),r.getDouble("similarity"),
   DetectionPolicy.Severity.valueOf(r.getString("severity")),ReviewStatus.valueOf(r.getString("review_status")),r.getLong("version"),
   r.getTimestamp("first_detected_at").toInstant(),r.getTimestamp("last_detected_at").toInstant(),r.getBoolean("current_evidence"),"MOCK".equals(r.getString("code"))),
   r.getObject("image_id",UUID.class),r.getObject("artwork_embedding_id",UUID.class),r.getObject("product_embedding_id",UUID.class),
   r.getObject("last_run_id",UUID.class),r.getString("model"),r.getString("model_version"),r.getString("preprocessing_version"),
   r.getString("image_hash"),reviewed==null?null:reviewed.toInstant(),r.getString("region_key")==null?null:
    new Region(r.getString("region_key"),r.getString("region_scheme"),r.getDouble("region_x"),r.getDouble("region_y"),r.getDouble("region_width"),r.getDouble("region_height")),
   new Scores(r.getDouble("embedding_similarity"),r.getObject("phash_similarity",Double.class),r.getObject("dhash_similarity",Double.class),r.getString("ensemble_version"),r.getDouble("similarity")));
 }
 public Page list(UUID owner,UUID artwork,ReviewStatus status,DetectionPolicy.Severity severity,int page,int size){
  var params=new MapSqlParameterSource("owner",owner);String where=OWNED;
  if(artwork!=null){where+=" AND d.artwork_id=:artwork";params.addValue("artwork",artwork);}
  if(status!=null){where+=" AND d.review_status=:status";params.addValue("status",status.name());}
  if(severity!=null){where+=" AND d.severity=:severity";params.addValue("severity",severity.name());}
  long count=named.queryForObject("SELECT count(*) "+FROM+where,params,Long.class);
  params.addValue("limit",size).addValue("offset",(long)page*size);
  var items=named.query(SELECT+FROM+where+" ORDER BY d.last_detected_at DESC,d.id LIMIT :limit OFFSET :offset",params,this::map)
   .stream().map(Detail::detection).toList();
  return new Page(items,page,size,count,(count+size-1)/size);
 }
 public Optional<Detail> detail(UUID owner,UUID id){
  return named.query(SELECT+FROM+OWNED+" AND d.id=:id",new MapSqlParameterSource("owner",owner).addValue("id",id),this::map).stream().findFirst();
 }
 public boolean update(UUID owner,UUID id,UpdateRequest request){
  return jdbc.update("""
   UPDATE detection d SET review_status=?,reviewed_at=now()
   WHERE d.id=? AND d.version=? AND EXISTS(
    SELECT 1 FROM artwork a JOIN app_user u ON u.id=a.user_id
    WHERE a.id=d.artwork_id AND a.user_id=? AND a.deleted=FALSE AND u.status='ACTIVE')
   """,request.status().name(),id,request.version(),owner)==1;
 }
}
