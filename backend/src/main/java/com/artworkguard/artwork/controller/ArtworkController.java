package com.artworkguard.artwork.controller;
import com.artworkguard.artwork.dto.ArtworkDtos.*;
import com.artworkguard.artwork.service.ArtworkService;
import com.artworkguard.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/artworks") @SecurityRequirement(name="bearerAuth")
public class ArtworkController {
 private final ArtworkService service;
 public ArtworkController(ArtworkService service){this.service=service;}
 @PostMapping("/upload-url")
 public ApiResponse<UploadResponse> upload(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody UploadRequest request){return ApiResponse.ok(service.uploadUrl(owner(jwt),request));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public ApiResponse<ArtworkResponse> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateRequest request){return ApiResponse.ok(service.create(owner(jwt),request));}
 @GetMapping
 public ApiResponse<ArtworkPage> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return ApiResponse.ok(service.list(owner(jwt),page,size));}
 @GetMapping("/{id}")
 public ApiResponse<ArtworkResponse> get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ApiResponse.ok(service.get(owner(jwt),id));}
 @PatchMapping("/{id}")
 public ApiResponse<ArtworkResponse> update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody UpdateRequest request){return ApiResponse.ok(service.update(owner(jwt),id,request));}
 @DeleteMapping("/{id}")
 public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){service.delete(owner(jwt),id);return ApiResponse.ok(null);}
 @GetMapping("/{id}/image-url")
 public ApiResponse<ImageUrlResponse> image(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean thumbnail){return ApiResponse.ok(service.imageUrl(owner(jwt),id,thumbnail));}
 @PostMapping("/{id}/embedding") @ResponseStatus(HttpStatus.ACCEPTED)
 public ApiResponse<com.artworkguard.embedding.EmbeddingJobService.JobResponse> requestEmbedding(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {
  return ApiResponse.ok(service.requestEmbedding(owner(jwt),id));
 }
 @GetMapping("/{id}/embedding")
 public ApiResponse<com.artworkguard.embedding.EmbeddingJobService.JobResponse> embeddingStatus(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {
  return ApiResponse.ok(service.embeddingStatus(owner(jwt),id));
 }
 private UUID owner(Jwt jwt){return UUID.fromString(jwt.getSubject());}
}
