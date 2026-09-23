package com.artworkguard.evidence;
import com.artworkguard.detection.DetectionRepository;
import com.artworkguard.evidence.EvidenceDtos.Evidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class EvidenceRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public EvidenceRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 record Source(UUID productId,UUID imageId,String marketplace,String externalProductId,String title,String productUrl,
  String sellerExternalId,String sellerName,String sellerUrl,BigDecimal price,String currency,String imageOriginalUrl,
  String imageStorageKey,int imageSizeBytes,String imageSha256,Instant capturedAt){}
 public record Asset(UUID imageId,String storageKey,int sizeBytes,String sha256){}

 public void capture(UUID detectionId,UUID runId,DetectionRepository.Candidate candidate){
  var rows=jdbc.query("""
   SELECT p.id,e.image_id,m.code,p.external_product_id,p.title,p.product_url,s.external_seller_id,s.seller_name,s.seller_url,
    p.price,p.currency,i.original_url,i.storage_key,i.storage_size_bytes,i.image_hash,current_timestamp AS captured_at
   FROM product p JOIN marketplace m ON m.id=p.marketplace_id JOIN seller s ON s.id=p.seller_id
   JOIN product_image_embedding e ON e.id=? JOIN product_image i ON i.id=e.image_id AND i.product_id=p.id
   WHERE p.id=? AND i.image_hash=e.image_hash AND i.active=TRUE AND p.status='ACTIVE' AND m.enabled=TRUE
    AND i.storage_key=('product-images/'||e.image_id::text||'/'||i.image_hash||'.png') AND i.storage_size_bytes>0
   FOR SHARE OF p,i,s,m
   """,(r,n)->new Source(r.getObject("id",UUID.class),r.getObject("image_id",UUID.class),r.getString("code"),r.getString("external_product_id"),
    r.getString("title"),r.getString("product_url"),r.getString("external_seller_id"),r.getString("seller_name"),r.getString("seller_url"),
    r.getBigDecimal("price"),r.getString("currency"),r.getString("original_url"),r.getString("storage_key"),r.getInt("storage_size_bytes"),
    r.getString("image_hash"),r.getTimestamp("captured_at").toInstant()),candidate.productEmbeddingId(),candidate.productId());
  if(rows.isEmpty())throw new IllegalStateException("Evidence source image is unavailable");
  Source source=rows.getFirst();String json=json(source,detectionId,runId);String svg=svg(source,detectionId);
  jdbc.update("""
   INSERT INTO evidence_snapshot(id,detection_id,detection_run_id,product_id,image_id,marketplace_code,external_product_id,
    product_title,product_url,seller_external_id,seller_name,seller_url,price,currency,image_original_url,image_storage_key,
    image_size_bytes,image_sha256,snapshot_json,snapshot_sha256,screenshot_svg,screenshot_sha256,captured_at)
   VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(detection_id,detection_run_id) DO NOTHING
   """,UUID.randomUUID(),detectionId,runId,source.productId(),source.imageId(),source.marketplace(),source.externalProductId(),source.title(),source.productUrl(),
   source.sellerExternalId(),source.sellerName(),source.sellerUrl(),source.price(),source.currency(),source.imageOriginalUrl(),source.imageStorageKey(),
   source.imageSizeBytes(),source.imageSha256(),json,sha(json),svg,sha(svg),Timestamp.from(source.capturedAt()));
 }
 private String json(Source s,UUID detection,UUID run){
  var root=mapper.createObjectNode();root.put("schema","artworkguard-evidence-v1");root.put("detectionId",detection.toString());root.put("detectionRunId",run.toString());
  root.put("capturedAt",s.capturedAt().toString());root.put("marketplace",s.marketplace());root.put("productId",s.productId().toString());
  root.put("externalProductId",s.externalProductId());root.put("productTitle",s.title());root.put("productUrl",s.productUrl());
  root.put("sellerExternalId",s.sellerExternalId());root.put("sellerName",s.sellerName());root.put("sellerUrl",s.sellerUrl());
  root.put("price",s.price().toPlainString());root.put("currency",s.currency());root.put("imageId",s.imageId().toString());
  root.put("imageOriginalUrl",s.imageOriginalUrl());root.put("imageSha256",s.imageSha256());
  try{return mapper.writeValueAsString(root);}catch(Exception e){throw new IllegalStateException("Evidence serialization failed");}
 }
 private String svg(Source s,UUID detection){return """
  <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="675" viewBox="0 0 1200 675">
  <rect width="1200" height="675" fill="#f8fafc"/><rect x="48" y="48" width="1104" height="579" rx="24" fill="white" stroke="#cbd5e1"/>
  <text x="88" y="112" font-family="sans-serif" font-size="34" font-weight="700">ArtworkGuard Evidence</text>
  <text x="88" y="172" font-family="sans-serif" font-size="24">Marketplace: %s</text>
  <text x="88" y="220" font-family="sans-serif" font-size="24">Product: %s</text>
  <text x="88" y="268" font-family="sans-serif" font-size="24">Seller: %s</text>
  <text x="88" y="316" font-family="sans-serif" font-size="24">Price: %s %s</text>
  <text x="88" y="364" font-family="sans-serif" font-size="20">URL: %s</text>
  <text x="88" y="412" font-family="monospace" font-size="18">Image SHA-256: %s</text>
  <text x="88" y="460" font-family="monospace" font-size="18">Detection: %s</text>
  <text x="88" y="508" font-family="sans-serif" font-size="20">Captured: %s</text>
  </svg>
  """.formatted(xml(s.marketplace()),xml(shorten(s.title(),90)),xml(shorten(s.sellerName(),80)),xml(s.price().toPlainString()),xml(s.currency()),
   xml(shorten(s.productUrl(),105)),xml(s.imageSha256()),detection,xml(s.capturedAt().toString()));}
 private String shorten(String value,int max){return value.length()<=max?value:value.substring(0,max-1)+"…";}
 private String xml(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
 private String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Evidence map(ResultSet r,int n)throws SQLException{return new Evidence(r.getObject("id",UUID.class),r.getObject("detection_run_id",UUID.class),r.getObject("product_id",UUID.class),r.getObject("image_id",UUID.class),
  r.getString("marketplace_code"),r.getString("external_product_id"),r.getString("product_title"),r.getString("product_url"),r.getString("seller_external_id"),r.getString("seller_name"),r.getString("seller_url"),
  r.getBigDecimal("price"),r.getString("currency"),r.getString("image_original_url"),r.getString("image_sha256"),r.getString("snapshot_sha256"),r.getString("screenshot_sha256"),r.getTimestamp("captured_at").toInstant());}
 public List<Evidence> list(UUID detectionId){return jdbc.query("SELECT * FROM evidence_snapshot WHERE detection_id=? ORDER BY captured_at DESC,id",this::map,detectionId);}
 public Optional<String> screenshot(UUID detectionId,UUID evidenceId){return jdbc.query("SELECT screenshot_svg FROM evidence_snapshot WHERE id=? AND detection_id=?",(r,n)->r.getString(1),evidenceId,detectionId).stream().findFirst();}
 public Optional<Asset> asset(UUID detectionId,UUID evidenceId){return jdbc.query("SELECT image_id,image_storage_key,image_size_bytes,image_sha256 FROM evidence_snapshot WHERE id=? AND detection_id=?",
  (r,n)->new Asset(r.getObject(1,UUID.class),r.getString(2),r.getInt(3),r.getString(4)),evidenceId,detectionId).stream().findFirst();}
}
