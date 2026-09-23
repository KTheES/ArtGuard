package com.artworkguard.audit;
import com.artworkguard.common.response.ApiResponse;
import com.artworkguard.redis.RedisWorkGuard;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/admin/audit-events")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class AuditController {
 private final AuditService service;private final RedisWorkGuard guard;
 public AuditController(AuditService service,RedisWorkGuard guard){this.service=service;this.guard=guard;}
 @GetMapping public ApiResponse<AuditRepository.Page> list(@AuthenticationPrincipal Jwt jwt,
  @RequestParam(required=false) @Min(1) Long beforeId,@RequestParam(required=false) UUID subjectUserId,
  @RequestParam(required=false) AuditRepository.Type resourceType,@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit){
  UUID admin=UUID.fromString(jwt.getSubject());guard.rate("audit-read",admin,30);return ApiResponse.ok(service.list(admin,beforeId,subjectUserId,resourceType,limit));
 }
}
