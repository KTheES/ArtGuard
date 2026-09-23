package com.artworkguard.marketplace.service;
import com.artworkguard.redis.RedisWorkGuard;
import com.artworkguard.marketplace.adapter.*;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import com.artworkguard.product.dto.CatalogDtos.*;
import com.artworkguard.product.repository.CatalogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
@Service
public class RedisCollectionCoordinator {
 private final RedisWorkGuard guard;private final MockMarketplaceAdapter adapter;private final CatalogImportService importer;
 private final CatalogRepository catalog;private final ObjectMapper mapper;
 public RedisCollectionCoordinator(RedisWorkGuard guard,MockMarketplaceAdapter adapter,CatalogImportService importer,CatalogRepository catalog,ObjectMapper mapper){
  this.guard=guard;this.adapter=adapter;this.importer=importer;this.catalog=catalog;this.mapper=mapper;
 }
 public record Marker(String fingerprint,UUID productId){}
 public CollectResponse collect(UUID actor,CollectRequest request){
  guard.rate("collect",actor,10);
  var market=catalog.marketplace(MarketplaceCode.MOCK).orElseThrow(CatalogException::notFound);
  if(!market.enabled())throw new CatalogException(org.springframework.http.HttpStatus.CONFLICT,"MARKETPLACE_DISABLED","비활성화된 마켓입니다.");
  String query=request.query().strip().toLowerCase(Locale.ROOT);
  String root="ag:v1:collect:{MOCK}:",cache=root+"search:"+hash(query+"\n"+request.limit()),lock=root+"lock";
  var hit=decode(guard.read(cache),CollectResponse.class);
  if(valid(hit))return cached(hit);
  String token=guard.acquire(lock);
  try{
   hit=decode(guard.read(cache),CollectResponse.class);if(valid(hit))return cached(hit);
   var listings=adapter.searchProducts(query,request.limit()).stream()
    .sorted(Comparator.comparing((MarketplaceListing p)->p.seller().externalSellerId()).thenComparing(MarketplaceListing::externalProductId)).toList();
   var changed=new ArrayList<MarketplaceListing>();var ids=new ArrayList<UUID>();var markers=new LinkedHashMap<String,String>();
   for(var listing:listings){
    String key=root+"product:"+hash(listing.externalProductId()),fingerprint=hash(encode(listing));
    var marker=decode(guard.read(key),Marker.class);
    if(marker!=null && fingerprint.equals(marker.fingerprint()) && marker.productId()!=null){
     var existing=catalog.product(marker.productId());
     if(existing.isPresent() && existing.get().marketplace()==MarketplaceCode.MOCK
      && existing.get().product().externalProductId().equals(listing.externalProductId()) && "ACTIVE".equals(existing.get().product().status())){
      ids.add(marker.productId());continue;
     }
    }
    changed.add(listing);
   }
   guard.renew(lock,token);
   // persist is a separate Spring transactional bean; it returns only after the DB commit.
   var result=importer.persist(MarketplaceCode.MOCK,changed);
   for(int i=0;i<changed.size();i++){
    var listing=changed.get(i);UUID id=result.productIds().get(i);ids.add(id);
    markers.put(root+"product:"+hash(listing.externalProductId()),encode(new Marker(hash(encode(listing)),id)));
   }
   var response=new CollectResponse(MarketplaceCode.MOCK,result.upsertedProducts(),result.upsertedImages(),List.copyOf(ids),false,listings.size()-changed.size());
   guard.publish(lock,token,cache,encode(response),markers);return response;
  }finally{guard.release(lock,token);}
 }
 private boolean valid(CollectResponse value){return value!=null && value.marketplace()==MarketplaceCode.MOCK && value.productIds()!=null && value.productIds().size()<=100 && value.productIds().stream().noneMatch(Objects::isNull);}
 private CollectResponse cached(CollectResponse value){return new CollectResponse(value.marketplace(),0,0,value.productIds(),true,value.productIds().size());}
 private <T>T decode(String value,Class<T> type){if(value==null)return null;try{return mapper.readValue(value,type);}catch(Exception e){return null;}}
 private String encode(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Could not encode collection cache");}}
 public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
