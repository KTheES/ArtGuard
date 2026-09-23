package com.artworkguard.detection.queue;
import com.artworkguard.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/artworks/{artworkId}/detection-jobs")
public class DetectionJobController {
 private final DetectionJobService jobs;
 public DetectionJobController(DetectionJobService jobs){this.jobs=jobs;}
 @GetMapping("/{jobId}") public ApiResponse<DetectionJobService.JobResponse> status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID artworkId,@PathVariable UUID jobId){return ApiResponse.ok(jobs.status(UUID.fromString(jwt.getSubject()),artworkId,jobId));}
 @PostMapping("/{jobId}/retry") public ApiResponse<DetectionJobService.JobResponse> retry(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID artworkId,@PathVariable UUID jobId){return ApiResponse.ok(jobs.retry(UUID.fromString(jwt.getSubject()),artworkId,jobId));}
}
