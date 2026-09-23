package com.artworkguard.social;
import com.artworkguard.common.response.ApiResponse;
import com.artworkguard.redis.RedisWorkGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.artworkguard.social.SocialDtos.*;
@RestController @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class SocialController {
 private final SocialService service;private final RedisWorkGuard guard;
 public SocialController(SocialService service,RedisWorkGuard guard){this.service=service;this.guard=guard;}
 @PostMapping("/api/v1/social-checks") public ApiResponse<Check> start(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Start request){UUID owner=UUID.fromString(jwt.getSubject());guard.rate("social-start",owner,1);return ApiResponse.ok(service.start(owner,request));}
 @GetMapping("/api/v1/social-checks") public ApiResponse<Page> own(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return ApiResponse.ok(service.own(UUID.fromString(jwt.getSubject()),page,size));}
 @PatchMapping("/api/v1/social-checks/{id}/revoke") public ApiResponse<Check> revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Revoke request){return ApiResponse.ok(service.revoke(UUID.fromString(jwt.getSubject()),id,request));}
 @GetMapping("/api/v1/admin/social-checks") public ApiResponse<Page> queue(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return ApiResponse.ok(service.queue(UUID.fromString(jwt.getSubject()),page,size));}
 @PatchMapping("/api/v1/admin/social-checks/{id}") public ApiResponse<Check> review(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Review request){return ApiResponse.ok(service.review(UUID.fromString(jwt.getSubject()),id,request));}
}
