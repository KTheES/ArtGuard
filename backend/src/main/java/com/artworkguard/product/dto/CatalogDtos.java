package com.artworkguard.product.dto;
import com.artworkguard.marketplace.domain.*;
import com.artworkguard.product.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class CatalogDtos {
 private CatalogDtos(){}
 public record CollectRequest(@Size(max=100) String query,@Min(1) @Max(100) Integer limit){
  public CollectRequest{query=query==null?"":query.strip();limit=limit==null?20:limit;}
 }
 public record CollectResponse(MarketplaceCode marketplace,int upsertedProducts,int upsertedImages,List<UUID> productIds,boolean cached,int deduplicatedProducts){
  public CollectResponse(MarketplaceCode marketplace,int products,int images,List<UUID> ids){this(marketplace,products,images,ids,false,0);}
 }
 public record MarketplaceResponse(UUID id,MarketplaceCode code,String name,String baseUrl,boolean enabled,boolean collectionAvailable,boolean synthetic){}
 public record SellerResponse(UUID id,String externalSellerId,String name,String url){}
 public record ProductResponse(UUID id,String externalProductId,String title,String productUrl,BigDecimal price,String currency,String status,
                               MarketplaceCode marketplace,SellerResponse seller,Instant firstSeenAt,Instant lastSeenAt,boolean synthetic){}
 public record ImageResponse(UUID id,String originalUrl,int width,int height,String imageHash,String previewPath){}
 public record ProductDetail(ProductResponse product,List<ImageResponse> images){}
 public record ProductPage(List<ProductResponse> items,int page,int size,long totalElements,long totalPages){}
 public static ProductResponse response(Product p,MarketplaceCode code,Seller s){
  return new ProductResponse(p.id(),p.externalProductId(),p.title(),p.url(),p.price(),p.currency(),p.status(),code,
   new SellerResponse(s.id(),s.externalSellerId(),s.name(),s.url()),p.firstSeenAt(),p.lastSeenAt(),code==MarketplaceCode.MOCK);
 }
 public static ImageResponse response(ProductImage i){
  return new ImageResponse(i.id(),i.originalUrl(),i.width(),i.height(),i.imageHash(),"/api/v1/products/"+i.productId()+"/images/"+i.id()+"/preview");
 }
}
