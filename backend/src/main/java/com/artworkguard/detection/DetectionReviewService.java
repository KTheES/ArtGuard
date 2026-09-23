package com.artworkguard.detection;
import com.artworkguard.detection.DetectionReviewDtos.*;
import com.artworkguard.marketplace.service.CatalogAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.UUID;
@Service
public class DetectionReviewService {
 private final CatalogAccess access;private final DetectionReviewRepository repository;
 public DetectionReviewService(CatalogAccess access,DetectionReviewRepository repository){this.access=access;this.repository=repository;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Page list(UUID owner,UUID artwork,ReviewStatus status,DetectionPolicy.Severity severity,int page,int size){
  access.active(owner);return repository.list(owner,artwork,status,severity,page,size);
 }
 @Transactional(readOnly=true)
 public Detail detail(UUID owner,UUID id){access.active(owner);return owned(owner,id);}
 @Transactional
 public Detail update(UUID owner,UUID id,UpdateRequest request){
  access.active(owner);owned(owner,id);
  if(!repository.update(owner,id,request)){
   owned(owner,id);throw DetectionReviewException.conflict();
  }
  return owned(owner,id);
 }
 private Detail owned(UUID owner,UUID id){return repository.detail(owner,id).orElseThrow(DetectionReviewException::missing);}
}
