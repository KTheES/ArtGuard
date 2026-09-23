package com.artworkguard.seller;
import com.artworkguard.marketplace.service.CatalogAccess;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
import static com.artworkguard.seller.SellerIntelligenceDtos.*;
@Service
public class SellerIntelligenceService {
 private final CatalogAccess access;private final SellerIntelligenceRepository repository;
 public SellerIntelligenceService(CatalogAccess access,SellerIntelligenceRepository repository){this.access=access;this.repository=repository;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Page own(UUID owner,MarketplaceCode marketplace,int page,int size){
  access.active(owner);return repository.list(owner,marketplace==null?null:marketplace.name(),page,size);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Page admin(UUID owner,MarketplaceCode marketplace,int page,int size){
  access.admin(owner);return repository.list(null,marketplace==null?null:marketplace.name(),page,size);
 }
}
