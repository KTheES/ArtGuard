package com.artworkguard.marketplace.adapter;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import com.artworkguard.marketplace.service.CatalogException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
@Component
public class MockMarketplaceAdapter implements MarketplaceAdapter {
 private final List<MarketplaceListing> listings;
 public MockMarketplaceAdapter(MockImageLibrary images){
  var studio=new MarketplaceListing.SellerListing("studio-one","MOCK Studio One","https://mock.artworkguard.invalid/sellers/studio-one");
  var forest=new MarketplaceListing.SellerListing("forest-shop","MOCK Forest Shop","https://mock.artworkguard.invalid/sellers/forest-shop");
  listings=List.of(
   listing("moon-cat-poster","MOCK Moon Cat Poster","19.90",studio,images),
   listing("moon-cat-shirt","MOCK Moon Cat T-shirt","29.90",studio,images),
   listing("forest-mug","MOCK Forest Mug","14.50",forest,images));
 }
 private MarketplaceListing listing(String id,String title,String price,MarketplaceListing.SellerListing seller,MockImageLibrary images){
  return new MarketplaceListing(id,title,"https://mock.artworkguard.invalid/products/"+id,new BigDecimal(price),"USD",seller,List.of(images.metadata(id)));
 }
 public MarketplaceCode getMarketplace(){return MarketplaceCode.MOCK;}
 public List<MarketplaceListing> searchProducts(String query,int limit){
  if(limit<1 || limit>100)throw new IllegalArgumentException("Invalid search limit");
  String needle=query==null?"":query.strip().toLowerCase(Locale.ROOT);
  return listings.stream().filter(p->p.title().toLowerCase(Locale.ROOT).contains(needle)).limit(limit).toList();
 }
 public MarketplaceListing getProduct(String id){return listings.stream().filter(p->p.externalProductId().equals(id)).findFirst().orElseThrow(CatalogException::notFound);}
}
