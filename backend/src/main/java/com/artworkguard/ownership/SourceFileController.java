package com.artworkguard.ownership;
import com.artworkguard.common.response.ApiResponse;
import com.artworkguard.redis.RedisWorkGuard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.util.*;
import java.io.IOException;
@RestController @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class SourceFileController {
 private final SourceFileService service;private final RedisWorkGuard guard;
 public SourceFileController(SourceFileService service,RedisWorkGuard guard){this.service=service;this.guard=guard;}
 @PostMapping(value="/api/v1/ownership-claims/{id}/source-file",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public ApiResponse<SourceFileService.Metadata> upload(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestParam SourceFileValidator.Format format,@RequestPart MultipartFile file)throws IOException{
  UUID owner=UUID.fromString(jwt.getSubject());guard.rate("source-file-upload",owner,2);
  if(file.isEmpty() || file.getSize()>SourceFileValidator.MAX_BYTES)throw new com.artworkguard.artwork.service.ArtworkException(HttpStatus.BAD_REQUEST,"SOURCE_FILE_INVALID","원본 자료는 20 MiB 이하로 제출해 주세요.");
  return ApiResponse.ok(service.upload(owner,id,format,file.getBytes()));
 }
 @GetMapping("/api/v1/ownership-claims/{id}/source-file") public ApiResponse<Optional<SourceFileService.Metadata>> own(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ApiResponse.ok(service.own(UUID.fromString(jwt.getSubject()),id));}
 @GetMapping("/api/v1/admin/ownership-claims/{id}/source-file") public ApiResponse<Optional<SourceFileService.Metadata>> admin(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ApiResponse.ok(service.admin(UUID.fromString(jwt.getSubject()),id));}
 @GetMapping("/api/v1/admin/ownership-claims/{id}/source-file/download") public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
  UUID admin=UUID.fromString(jwt.getSubject());guard.rate("source-file-download",admin,10);
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
   .header("Content-Disposition","attachment; filename=\"source-"+id+".bin\"").header("X-Content-Type-Options","nosniff")
   .header("Content-Security-Policy","default-src 'none'; sandbox").body(service.download(admin,id));
 }
}
