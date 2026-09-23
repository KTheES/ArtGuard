package com.artworkguard.product.service;
import com.artworkguard.marketplace.adapter.MockImageLibrary;
import com.artworkguard.marketplace.domain.*;
import com.artworkguard.marketplace.service.*;
import com.artworkguard.product.dto.CatalogDtos;
import com.artworkguard.product.dto.CatalogDtos.*;
import com.artworkguard.product.repository.CatalogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class CatalogService {
 private final CatalogAccess access;private final CatalogRepository repository;private final MockImageLibrary images;private final boolean mockEnabled;private final com.artworkguard.storage.ObjectStorage storage;private final com.artworkguard.marketplace.aliexpress.AliSettings ali;
 public CatalogService(CatalogAccess access,CatalogRepository repository,MockImageLibrary images,@Value("${artworkguard.marketplace.mock-enabled:false}") boolean mockEnabled,com.artworkguard.storage.ObjectStorage storage,com.artworkguard.marketplace.aliexpress.AliSettings ali){
  this.access=access;this.repository=repository;this.images=images;this.mockEnabled=mockEnabled;this.storage=storage;this.ali=ali;
 }
 @Transactional(readOnly=true)
 public List<MarketplaceResponse> marketplaces(UUID user){
  access.active(user);
  return repository.marketplaces().stream().map(m->new MarketplaceResponse(m.id(),m.code(),m.name(),m.baseUrl(),m.enabled(),m.enabled()&&((m.code()==MarketplaceCode.MOCK&&mockEnabled)||(m.code()==MarketplaceCode.ALIEXPRESS&&ali.ready())),m.code()==MarketplaceCode.MOCK)).toList();
 }
 @Transactional(readOnly=true)
 public ProductPage products(UUID user,MarketplaceCode marketplace,String query,int page,int size){
  access.active(user);var result=repository.products(marketplace,query,page,size);
  return new ProductPage(result.rows().stream().map(r->CatalogDtos.response(r.product(),r.marketplace(),r.seller())).toList(),page,size,result.total(),(result.total()+size-1)/size);
 }
 @Transactional(readOnly=true)
 public ProductDetail product(UUID user,UUID id){
  access.active(user);var row=repository.product(id).orElseThrow(CatalogException::notFound);
  return new ProductDetail(CatalogDtos.response(row.product(),row.marketplace(),row.seller()),repository.images(id).stream().map(CatalogDtos::response).toList());
 }
 @Transactional(readOnly=true)
 public byte[] preview(UUID user,UUID product,UUID image){
  access.active(user);var data=repository.image(product,image).orElseThrow(CatalogException::notFound);
  if("STORED_PNG".equals(data.sourceType()) && data.sourceKey().equals("catalog-imports/aliexpress/"+data.imageHash()+".png") && data.sizeBytes()>0)
   return storage.readUpload(data.sourceKey(),"image/png",data.sizeBytes());
  if(!"MOCK_RESOURCE".equals(data.sourceType()))throw CatalogException.notFound();
  return images.read(data.sourceKey());
 }
}
