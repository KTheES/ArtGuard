package com.artworkguard.marketplace.service;
import com.artworkguard.marketplace.adapter.*;
import com.artworkguard.marketplace.domain.*;
import com.artworkguard.product.dto.CatalogDtos.CollectResponse;
import com.artworkguard.product.repository.CatalogRepository;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class CatalogImportService {
 private final CatalogRepository repository;
 private final Validator validator;
 private final MockImageLibrary images;
 private final com.artworkguard.product.embedding.ProductEmbeddingJobService jobs;
 public CatalogImportService(CatalogRepository repository,Validator validator,MockImageLibrary images,com.artworkguard.product.embedding.ProductEmbeddingJobService jobs){this.repository=repository;this.validator=validator;this.images=images;this.jobs=jobs;}
 @Transactional(timeout=30)
 public CollectResponse persist(MarketplaceCode code,List<MarketplaceListing> listings){
  if(code!=MarketplaceCode.MOCK || listings==null || listings.size()>100)throw invalid();
  Set<String> productIds=new HashSet<>();
  for(var listing:listings){
   if(listing==null || !validator.validate(listing).isEmpty() || !productIds.add(listing.externalProductId()))throw invalid();
   Set<String> imageIds=new HashSet<>();
   for(var image:listing.images()){
    if(!imageIds.add(image.externalImageId()))throw invalid();
    if(!images.matches(image))throw invalid(); // Require actual bundled image dimensions and content hash.
   }
  }
  var marketplace=repository.marketplace(code).orElseThrow(CatalogException::notFound);
  if(!marketplace.enabled())throw new CatalogException(HttpStatus.CONFLICT,"MARKETPLACE_DISABLED","비활성화된 마켓입니다.");
  var sorted=listings.stream().sorted(Comparator.comparing((MarketplaceListing p)->p.seller().externalSellerId()).thenComparing(MarketplaceListing::externalProductId)).toList();
  List<UUID> stored=new ArrayList<>();int imageCount=0;
  for(var listing:sorted){
   UUID seller=repository.upsertSeller(marketplace.id(),listing.seller());
   UUID product=repository.upsertProduct(marketplace.id(),seller,listing);
   for(var image:listing.images().stream().sorted(Comparator.comparing(MarketplaceListing.ImageListing::externalImageId)).toList()){
    repository.upsertImage(product,image);imageCount++;
   }
   repository.deactivateMissingImages(product,listing.images().stream().map(MarketplaceListing.ImageListing::externalImageId).toList());
   jobs.enqueueProduct(product);
   stored.add(product);
  }
  return new CollectResponse(code,stored.size(),imageCount,List.copyOf(stored));
 }
 private CatalogException invalid(){return new CatalogException(HttpStatus.BAD_GATEWAY,"INVALID_MARKETPLACE_DATA","마켓 응답 데이터 형식을 확인해 주세요.");}
}
