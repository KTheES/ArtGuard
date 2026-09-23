package com.artworkguard.product.repository;
import com.artworkguard.marketplace.adapter.MarketplaceListing;
import com.artworkguard.marketplace.domain.*;
import com.artworkguard.product.domain.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
@Repository
public class CatalogRepository {
 private final JdbcTemplate jdbc;
 private final NamedParameterJdbcTemplate named;
 public CatalogRepository(JdbcTemplate jdbc){this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc);}
 public record CatalogRow(Product product,MarketplaceCode marketplace,Seller seller){}
 public record CatalogPage(List<CatalogRow> rows,long total){}
 private static final String SELECT_PRODUCT="""
  SELECT p.*,m.code,s.external_seller_id,s.seller_name,s.seller_url,
   s.first_seen_at AS seller_first_seen_at,s.last_seen_at AS seller_last_seen_at
  FROM product p JOIN marketplace m ON m.id=p.marketplace_id JOIN seller s ON s.id=p.seller_id
  """;
 private Marketplace marketplace(ResultSet rs,int row)throws SQLException{
  return new Marketplace(rs.getObject("id",UUID.class),MarketplaceCode.valueOf(rs.getString("code")),rs.getString("name"),rs.getString("base_url"),rs.getBoolean("enabled"));
 }
 private CatalogRow product(ResultSet rs,int row)throws SQLException{
  UUID marketplace=rs.getObject("marketplace_id",UUID.class),seller=rs.getObject("seller_id",UUID.class);
  var p=new Product(rs.getObject("id",UUID.class),marketplace,seller,rs.getString("external_product_id"),rs.getString("title"),rs.getString("product_url"),
   rs.getBigDecimal("price"),rs.getString("currency"),rs.getString("status"),rs.getTimestamp("first_seen_at").toInstant(),rs.getTimestamp("last_seen_at").toInstant());
  var s=new Seller(seller,marketplace,rs.getString("external_seller_id"),rs.getString("seller_name"),rs.getString("seller_url"),
   rs.getTimestamp("seller_first_seen_at").toInstant(),rs.getTimestamp("seller_last_seen_at").toInstant());
  return new CatalogRow(p,MarketplaceCode.valueOf(rs.getString("code")),s);
 }
 private ProductImage image(ResultSet rs,int row)throws SQLException{
  return new ProductImage(rs.getObject("id",UUID.class),rs.getObject("product_id",UUID.class),rs.getString("external_image_id"),
   rs.getString("original_url"),rs.getString("source_type"),rs.getString("source_key"),rs.getInt("width"),rs.getInt("height"),rs.getString("image_hash"),rs.getInt("size_bytes"));
 }
 public List<Marketplace> marketplaces(){return jdbc.query("SELECT * FROM marketplace ORDER BY code",(rs, n) -> marketplace(rs, n));}
 public Optional<Marketplace> marketplace(MarketplaceCode code){
  return jdbc.query("SELECT * FROM marketplace WHERE code=?",(rs, n) -> marketplace(rs, n),code.name()).stream().findFirst();
 }
 public UUID upsertSeller(UUID marketplace,MarketplaceListing.SellerListing seller){
  return jdbc.queryForObject("""
   INSERT INTO seller(id,marketplace_id,external_seller_id,seller_name,seller_url) VALUES(?,?,?,?,?)
   ON CONFLICT(marketplace_id,external_seller_id) DO UPDATE SET seller_name=EXCLUDED.seller_name,seller_url=EXCLUDED.seller_url,last_seen_at=now()
   RETURNING id
   """,UUID.class,UUID.randomUUID(),marketplace,seller.externalSellerId(),seller.name(),seller.url());
 }
 public UUID upsertProduct(UUID marketplace,UUID seller,MarketplaceListing p){
  return jdbc.queryForObject("""
   INSERT INTO product(id,marketplace_id,seller_id,external_product_id,title,product_url,price,currency,status)
   VALUES(?,?,?,?,?,?,?,?,'ACTIVE')
   ON CONFLICT(marketplace_id,external_product_id) DO UPDATE SET seller_id=EXCLUDED.seller_id,title=EXCLUDED.title,
    product_url=EXCLUDED.product_url,price=EXCLUDED.price,currency=EXCLUDED.currency,status='ACTIVE',last_seen_at=now()
   RETURNING id
   """,UUID.class,UUID.randomUUID(),marketplace,seller,p.externalProductId(),p.title(),p.productUrl(),p.price(),p.currency());
 }
 public void upsertImage(UUID product,MarketplaceListing.ImageListing image){
  jdbc.update("""
   INSERT INTO product_image(id,product_id,external_image_id,original_url,source_type,source_key,width,height,image_hash)
   VALUES(?,?,?,?,'MOCK_RESOURCE',?,?,?,?)
   ON CONFLICT(product_id,external_image_id) DO UPDATE SET original_url=EXCLUDED.original_url,source_key=EXCLUDED.source_key,
    width=EXCLUDED.width,height=EXCLUDED.height,image_hash=EXCLUDED.image_hash,active=TRUE,last_seen_at=now(),
    storage_key=CASE WHEN product_image.image_hash=EXCLUDED.image_hash THEN product_image.storage_key ELSE NULL END
   """,UUID.randomUUID(),product,image.externalImageId(),image.originalUrl(),image.sourceKey(),image.width(),image.height(),image.imageHash());
 }
 public void deactivateMissingImages(UUID product,List<String> currentIds){
  named.update("UPDATE product_image SET active=FALSE WHERE product_id=:product AND external_image_id NOT IN (:ids)",
   new MapSqlParameterSource("product",product).addValue("ids",currentIds));
 }
 public CatalogPage products(MarketplaceCode marketplace,String query,int page,int size){
  String where=" WHERE p.status='ACTIVE'";
  var params=new MapSqlParameterSource();
  if(marketplace!=null){where+=" AND m.code=:marketplace";params.addValue("marketplace",marketplace.name());}
  if(query!=null&&!query.isBlank()){
   where+=" AND p.title ILIKE :query ESCAPE '!'";
   params.addValue("query","%"+escapeSearch(query.strip())+"%");
  }
  Long count=named.queryForObject("SELECT count(*) FROM product p JOIN marketplace m ON m.id=p.marketplace_id"+where,params,Long.class);
  params.addValue("limit",size).addValue("offset",(long)page*size);
  var rows=named.query(SELECT_PRODUCT+where+" ORDER BY p.last_seen_at DESC,p.id LIMIT :limit OFFSET :offset",params,(rs, n) -> product(rs, n));
  return new CatalogPage(rows,count==null?0:count);
 }
 public Optional<CatalogRow> product(UUID id){return jdbc.query(SELECT_PRODUCT+" WHERE p.id=?",(rs, n) -> product(rs, n),id).stream().findFirst();}
 public List<ProductImage> images(UUID product){return jdbc.query("SELECT * FROM product_image WHERE product_id=? AND active=TRUE ORDER BY external_image_id",this::image,product);}
 public Optional<ProductImage> image(UUID product,UUID image){
  return jdbc.query("SELECT * FROM product_image WHERE product_id=? AND id=? AND active=TRUE",this::image,product,image).stream().findFirst();
 }
 public static String escapeSearch(String query){return query.replace("!","!!").replace("%","!%").replace("_","!_");}
}
