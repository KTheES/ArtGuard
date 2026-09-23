package com.artworkguard.marketplace.aliexpress;
import com.artworkguard.marketplace.service.CatalogAccess;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import com.artworkguard.marketplace.service.CatalogException;
import com.artworkguard.product.repository.CatalogRepository;
import com.artworkguard.product.dto.CatalogDtos.CollectResponse;
import com.artworkguard.artwork.service.ImageValidator;
import com.artworkguard.storage.ObjectStorage;
import com.artworkguard.redis.RedisWorkGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.util.*;
@Service
public class AliCollectionService {
 private final CatalogAccess access;private final AliSettings settings;private final RedisWorkGuard guard;
 private final AliClient client;private final AliTransport transport;private final ImageValidator validator;
 private final ObjectStorage storage;private final AliImporter importer;private final ObjectMapper mapper;private final CatalogRepository catalog;
 public AliCollectionService(CatalogAccess access,AliSettings settings,RedisWorkGuard guard,AliClient client,AliTransport transport,ImageValidator validator,ObjectStorage storage,AliImporter importer,ObjectMapper mapper,CatalogRepository catalog){
  this.access=access;this.settings=settings;this.guard=guard;this.client=client;this.transport=transport;this.validator=validator;this.storage=storage;this.importer=importer;this.mapper=mapper;this.catalog=catalog;
 }
 public CollectResponse collect(UUID actor,AliDtos.SearchRequest request){
  access.admin(actor);settings.requireReady();guard.rate("collect",actor,10);
  catalog.marketplace(MarketplaceCode.ALIEXPRESS).filter(m->m.enabled()).orElseThrow(CatalogException::notFound);
  String root="ag:v1:collect:{ALIEXPRESS}:",lock=root+"lock";
  String cache=root+"search:"+com.artworkguard.marketplace.service.RedisCollectionCoordinator.hash(request.query().strip()+"\n"+request.page()+"\n"+request.limit());
  var hit=cached(guard.read(cache));if(hit!=null)return hit;
  String token=guard.acquire(lock);
  try{
   hit=cached(guard.read(cache));if(hit!=null)return hit;
   var listings=client.search(request);var prepared=new ArrayList<AliDtos.Prepared>();
   for(var listing:listings){
    guard.renew(lock,token);
    var download=transport.image(listing.imageUrl());ImageValidator.ValidatedImage image;
    try{image=validator.validate(download.bytes(),download.contentType());}catch(com.artworkguard.artwork.service.ArtworkException e){throw AliException.invalid();}
    if(image.original().length>20971520)throw AliException.invalid();
    String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image.original()));}catch(Exception e){throw new IllegalStateException(e);}
    String key="catalog-imports/aliexpress/"+hash+".png";storage.putImage(key,image.original());
    prepared.add(new AliDtos.Prepared(listing,key,hash,image.width(),image.height(),image.original().length));
   }
   guard.renew(lock,token);var result=importer.persist(prepared);
   try{guard.publish(lock,token,cache,mapper.writeValueAsString(result),Map.of());}catch(com.fasterxml.jackson.core.JsonProcessingException ignored){}
   return result;
  }finally{guard.release(lock,token);}
 }
 private CollectResponse cached(String json){
  if(json==null)return null;
  try{
   var value=mapper.readValue(json,CollectResponse.class);
   if(value.marketplace()!=MarketplaceCode.ALIEXPRESS||value.productIds()==null||value.productIds().size()>5||value.productIds().stream().anyMatch(Objects::isNull))return null;
   return new CollectResponse(MarketplaceCode.ALIEXPRESS,0,0,value.productIds(),true,value.productIds().size());
  }catch(Exception e){return null;}
 }
}
