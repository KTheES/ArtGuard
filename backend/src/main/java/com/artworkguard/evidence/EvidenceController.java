package com.artworkguard.evidence;
import com.artworkguard.common.response.ApiResponse;
import com.artworkguard.evidence.EvidenceDtos.Evidence;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/detections/{detectionId}/evidence") @SecurityRequirement(name="bearerAuth")
public class EvidenceController {
 private final EvidenceService service;public EvidenceController(EvidenceService service){this.service=service;}
 @GetMapping public ApiResponse<List<Evidence>> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID detectionId){return ApiResponse.ok(service.list(owner(jwt),detectionId));}
 @GetMapping("/{evidenceId}/screenshot") public ResponseEntity<byte[]> screenshot(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID detectionId,@PathVariable UUID evidenceId){
  return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/svg+xml")).cacheControl(CacheControl.noStore())
   .header("Content-Security-Policy","default-src 'none'; sandbox").header("X-Content-Type-Options","nosniff").body(service.screenshot(owner(jwt),detectionId,evidenceId));
 }
 @GetMapping("/{evidenceId}/image") public ResponseEntity<byte[]> image(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID detectionId,@PathVariable UUID evidenceId){
  return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(service.image(owner(jwt),detectionId,evidenceId));
 }
 private UUID owner(Jwt jwt){return UUID.fromString(jwt.getSubject());}
}
