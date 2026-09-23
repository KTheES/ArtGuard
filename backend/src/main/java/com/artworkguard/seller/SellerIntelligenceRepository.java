package com.artworkguard.seller;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.util.*;
import static com.artworkguard.seller.SellerIntelligenceDtos.*;

@Repository
public class SellerIntelligenceRepository {
 private final NamedParameterJdbcTemplate jdbc;
 public SellerIntelligenceRepository(DataSource source){jdbc=new NamedParameterJdbcTemplate(source);}
 public Page list(UUID owner,String marketplace,int page,int size){
  var params=new MapSqlParameterSource("owner",owner).addValue("marketplace",marketplace)
   .addValue("limit",size).addValue("offset",(long)page*size);
  String where=" WHERE a.deleted=FALSE AND u.status='ACTIVE' AND m.code<>'MOCK' AND d.review_status IN ('NEW','CONFIRMED')";
  if(owner!=null)where+=" AND a.user_id=:owner";
  if(marketplace!=null)where+=" AND m.code=:marketplace";
  String aggregate="""
   SELECT s.id AS seller_id,s.seller_name,m.code,s.external_seller_id,
    count(*) AS detection_count,count(*) FILTER(WHERE d.review_status='CONFIRMED') AS confirmed_count,
    count(*) FILTER(WHERE d.review_status='NEW') AS new_count,
    count(DISTINCT p.id) AS product_count,count(DISTINCT a.id) AS artwork_count,
    count(DISTINCT a.user_id) AS creator_count,count(DISTINCT m.id) AS marketplace_count,
    LEAST(100,10*count(*) FILTER(WHERE d.review_status='CONFIRMED')+2*count(*) FILTER(WHERE d.review_status='NEW')) AS risk_score,
    min(d.first_detected_at) AS first_seen,max(d.last_detected_at) AS last_seen
   FROM detection d JOIN artwork a ON a.id=d.artwork_id JOIN app_user u ON u.id=a.user_id
    JOIN product p ON p.id=d.product_id JOIN seller s ON s.id=p.seller_id
    JOIN marketplace m ON m.id=s.marketplace_id
   """+where+" GROUP BY s.id,s.seller_name,m.code,s.external_seller_id";
  long count=jdbc.queryForObject("SELECT count(*) FROM ("+aggregate+") grouped",params,Long.class);
  var items=jdbc.query(aggregate+" ORDER BY risk_score DESC,last_seen DESC,s.id LIMIT :limit OFFSET :offset",params,(r,n)->
   new Item(r.getObject("seller_id",UUID.class),r.getString("seller_name"),r.getString("code"),r.getString("external_seller_id"),
    r.getLong("detection_count"),r.getLong("confirmed_count"),r.getLong("new_count"),r.getLong("product_count"),r.getLong("artwork_count"),
    r.getLong("creator_count"),r.getLong("marketplace_count"),r.getInt("risk_score"),
    r.getTimestamp("first_seen").toInstant(),r.getTimestamp("last_seen").toInstant()));
  return new Page(items,page,size,count,(count+size-1)/size,owner==null?"ADMIN_ALL_ACTIVE_CREATORS":"OWN_ARTWORKS",
   "review-priority-v1","min(100, 10*confirmedCount + 2*newCount); review priority only, not infringement probability");
 }
}
