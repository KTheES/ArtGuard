package com.artworkguard.ownership;
import com.artworkguard.common.response.ApiResponse;
import com.artworkguard.redis.RedisWorkGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.artworkguard.ownership.OwnershipDtos.*;
@RestController @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class OwnershipController {
 private final OwnershipService service;private final RedisWorkGuard guard;
 public OwnershipController(OwnershipService service,RedisWorkGuard guard){this.service=service;this.guard=guard;}
 @PostMapping("/api/v1/artworks/{artworkId}/ownership-claims") public ApiResponse<Claim> submit(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID artworkId,@Valid @RequestBody Submit request){
  UUID owner=UUID.fromString(jwt.getSubject());guard.rate("ownership-submit",owner,3);return ApiResponse.ok(service.submit(owner,artworkId,request));
 }
 @GetMapping("/api/v1/artworks/{artworkId}/ownership-claims") public ApiResponse<Page> own(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID artworkId,
  @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return ApiResponse.ok(service.own(UUID.fromString(jwt.getSubject()),artworkId,page,size));}
 @GetMapping("/api/v1/admin/ownership-claims") public ApiResponse<Page> queue(@AuthenticationPrincipal Jwt jwt,
  @RequestParam(defaultValue="PENDING") Status status,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return ApiResponse.ok(service.queue(UUID.fromString(jwt.getSubject()),status,page,size));}
 @PatchMapping("/api/v1/admin/ownership-claims/{id}") public ApiResponse<Claim> review(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Review request){return ApiResponse.ok(service.review(UUID.fromString(jwt.getSubject()),id,request));}
}
