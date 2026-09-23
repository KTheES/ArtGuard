package com.artworkguard.seller;
import com.artworkguard.common.response.ApiResponse;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.artworkguard.seller.SellerIntelligenceDtos.*;
@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class SellerIntelligenceController {
 private final SellerIntelligenceService service;
 public SellerIntelligenceController(SellerIntelligenceService service){this.service=service;}
 @GetMapping("/api/v1/sellers/intelligence")
 public ApiResponse<Page> own(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) MarketplaceCode marketplace,
  @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
  return ApiResponse.ok(service.own(UUID.fromString(jwt.getSubject()),marketplace,page,size));
 }
 @GetMapping("/api/v1/admin/sellers/intelligence")
 public ApiResponse<Page> admin(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) MarketplaceCode marketplace,
  @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
  return ApiResponse.ok(service.admin(UUID.fromString(jwt.getSubject()),marketplace,page,size));
 }
}
