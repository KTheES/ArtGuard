package com.artworkguard.marketplace.aliexpress;
import com.artworkguard.marketplace.adapter.MarketplaceListing;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import com.artworkguard.marketplace.service.CatalogException;
import com.artworkguard.product.dto.CatalogDtos.CollectResponse;
import com.artworkguard.product.repository.CatalogRepository;
import com.artworkguard.product.embedding.ProductEmbeddingJobService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class AliImporter {
 private final CatalogRepository catalog;private final JdbcTemplate jdbc;private final ProductEmbeddingJobService jobs;
 public AliImporter(CatalogRepository catalog,JdbcTemplate jdbc,ProductEmbeddingJobService jobs){this.catalog=catalog;this.jdbc=jdbc;this.jobs=jobs;}
 @Transactional(timeout=30)
 public CollectResponse persist(List<AliDtos.Prepared> products){
  var market=catalog.marketplace(MarketplaceCode.ALIEXPRESS).filter(m->m.enabled()).orElseThrow(CatalogException::notFound);
  var ids=new ArrayList<UUID>();
  for(var prepared:products.stream().sorted(Comparator.comparing((AliDtos.Prepared p)->p.listing().shopId()).thenComparing(p->p.listing().id())).toList()){
   var p=prepared.listing();
   var seller=new MarketplaceListing.SellerListing(p.shopId(),"AliExpress shop "+p.shopId(),"https://www.aliexpress.com/store/"+p.shopId());
   UUID sellerId=catalog.upsertSeller(market.id(),seller);
   // Mock-only bean validation is not reused: AliClient validates this provider's response.
   var listing=new MarketplaceListing(p.id(),p.title(),"https://www.aliexpress.com/item/"+p.id()+".html",p.price(),p.currency(),seller,List.of());
   UUID product=catalog.upsertProduct(market.id(),sellerId,listing);
   jdbc.update("""
    INSERT INTO product_image(id,product_id,external_image_id,original_url,source_type,source_key,storage_key,width,height,image_hash,size_bytes)
    VALUES(?,?,'main',?,'STORED_PNG',?,?,?,?,?,?)
    ON CONFLICT(product_id,external_image_id) DO UPDATE SET original_url=EXCLUDED.original_url,source_type=EXCLUDED.source_type,
     source_key=EXCLUDED.source_key,storage_key=EXCLUDED.storage_key,width=EXCLUDED.width,height=EXCLUDED.height,
     image_hash=EXCLUDED.image_hash,size_bytes=EXCLUDED.size_bytes,active=TRUE,last_seen_at=now()
    """,UUID.randomUUID(),product,p.imageUrl(),prepared.key(),prepared.key(),prepared.width(),prepared.height(),prepared.hash(),prepared.size());
   catalog.deactivateMissingImages(product,List.of("main"));jobs.enqueueProduct(product);ids.add(product);
  }
  return new CollectResponse(MarketplaceCode.ALIEXPRESS,ids.size(),ids.size(),List.copyOf(ids));
 }
}
