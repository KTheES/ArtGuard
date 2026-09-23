package com.artworkguard.marketplace.aliexpress;
import com.artworkguard.common.response.ApiResponse;
import com.artworkguard.product.dto.CatalogDtos.CollectResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/admin/marketplaces/ALIEXPRESS")
public class AliController {
 private final AliCollectionService service;
 public AliController(AliCollectionService service){this.service=service;}
 @PostMapping("/collect") public ApiResponse<CollectResponse> collect(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AliDtos.SearchRequest request){
  return ApiResponse.ok(service.collect(UUID.fromString(jwt.getSubject()),request));
 }
}
