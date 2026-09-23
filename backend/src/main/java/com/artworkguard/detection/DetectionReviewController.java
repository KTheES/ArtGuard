package com.artworkguard.detection;
import com.artworkguard.detection.DetectionReviewDtos.*;
import com.artworkguard.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/detections") @SecurityRequirement(name="bearerAuth")
public class DetectionReviewController {
 private final DetectionReviewService service;
 public DetectionReviewController(DetectionReviewService service){this.service=service;}
 @GetMapping public ApiResponse<Page> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) UUID artworkId,
  @RequestParam(required=false) ReviewStatus status,@RequestParam(required=false) DetectionPolicy.Severity severity,
  @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
  return ApiResponse.ok(service.list(owner(jwt),artworkId,status,severity,page,size));
 }
 @GetMapping("/{id}") public ApiResponse<Detail> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ApiResponse.ok(service.detail(owner(jwt),id));}
 @PatchMapping("/{id}/status") public ApiResponse<Detail> update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody UpdateRequest request){return ApiResponse.ok(service.update(owner(jwt),id,request));}
 private UUID owner(Jwt jwt){return UUID.fromString(jwt.getSubject());}
}
