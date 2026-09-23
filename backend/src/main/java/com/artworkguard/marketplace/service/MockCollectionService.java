package com.artworkguard.marketplace.service;
import com.artworkguard.product.dto.CatalogDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
public class MockCollectionService {
 private final CatalogAccess access;private final RedisCollectionCoordinator coordinator;private final boolean enabled;
 public MockCollectionService(CatalogAccess access,RedisCollectionCoordinator coordinator,@Value("${artworkguard.marketplace.mock-enabled:false}") boolean enabled){this.access=access;this.coordinator=coordinator;this.enabled=enabled;}
 public CollectResponse collect(UUID actor,CollectRequest request){
  access.admin(actor);
  if(!enabled)throw new CatalogException(HttpStatus.SERVICE_UNAVAILABLE,"MOCK_COLLECTION_DISABLED","Mock 수집 설정이 비활성 상태입니다.");
  return coordinator.collect(actor,request);
 }
}
