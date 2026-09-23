package com.artworkguard.detection;
import com.artworkguard.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/artworks/{artworkId}/detections") @SecurityRequirement(name="bearerAuth")
public class DetectionController {
 private final com.artworkguard.detection.queue.DetectionJobService service;
 public DetectionController(com.artworkguard.detection.queue.DetectionJobService service){this.service=service;}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED) public ApiResponse<com.artworkguard.detection.queue.DetectionJobService.JobResponse> run(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID artworkId,
  @RequestParam(defaultValue="100") @Min(1) @Max(500) int limit){
  return ApiResponse.ok(service.request(UUID.fromString(jwt.getSubject()),artworkId,limit));
 }
}
