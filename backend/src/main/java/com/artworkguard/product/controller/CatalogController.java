package com.artworkguard.product.controller;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import com.artworkguard.product.dto.CatalogDtos.*;
import com.artworkguard.product.service.CatalogService;
import com.artworkguard.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1") @SecurityRequirement(name="bearerAuth")
public class CatalogController {
 private final CatalogService service;
 public CatalogController(CatalogService service){this.service=service;}
 @GetMapping("/marketplaces")
 public ApiResponse<List<MarketplaceResponse>> marketplaces(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.marketplaces(owner(jwt)));}
 @GetMapping("/products")
 public ApiResponse<ProductPage> products(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) MarketplaceCode marketplace,
  @RequestParam(defaultValue="") @Size(max=100) String query,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
  @RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return ApiResponse.ok(service.products(owner(jwt),marketplace,query,page,size));}
 @GetMapping("/products/{id}")
 public ApiResponse<ProductDetail> product(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ApiResponse.ok(service.product(owner(jwt),id));}
 @GetMapping("/products/{id}/images/{imageId}/preview")
 public ResponseEntity<byte[]> preview(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable UUID imageId){
  return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(service.preview(owner(jwt),id,imageId));
 }
 private UUID owner(Jwt jwt){return UUID.fromString(jwt.getSubject());}
}
