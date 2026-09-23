package com.artworkguard.marketplace.controller;
import com.artworkguard.marketplace.service.MockCollectionService;
import com.artworkguard.product.dto.CatalogDtos.*;
import com.artworkguard.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/admin/marketplaces") @SecurityRequirement(name="bearerAuth")
public class MockCollectionController {
 private final MockCollectionService service;
 public MockCollectionController(MockCollectionService service){this.service=service;}
 @PostMapping("/MOCK/collect")
 public ApiResponse<CollectResponse> collect(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CollectRequest request){
  return ApiResponse.ok(service.collect(UUID.fromString(jwt.getSubject()),request));
 }
}
