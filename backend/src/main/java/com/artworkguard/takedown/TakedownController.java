package com.artworkguard.takedown;
import com.artworkguard.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.artworkguard.takedown.TakedownDtos.*;

@RestController
@RequestMapping("/api/v1/detections/{detectionId}/takedown")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class TakedownController {
 private final TakedownService service;
 public TakedownController(TakedownService service){this.service=service;}
 @PostMapping public ApiResponse<Report> create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID detectionId,@Valid @RequestBody Create request){
  return ApiResponse.ok(service.create(UUID.fromString(jwt.getSubject()),detectionId,request));
 }
 @GetMapping public ApiResponse<Optional<Report>> get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID detectionId){
  return ApiResponse.ok(service.get(UUID.fromString(jwt.getSubject()),detectionId));
 }
 @PatchMapping public ApiResponse<Report> update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID detectionId,@Valid @RequestBody Update request){
  return ApiResponse.ok(service.update(UUID.fromString(jwt.getSubject()),detectionId,request));
 }
}
