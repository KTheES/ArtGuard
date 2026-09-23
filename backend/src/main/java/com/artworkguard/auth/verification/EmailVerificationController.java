package com.artworkguard.auth.verification;
import com.artworkguard.common.response.ApiResponse;
import com.artworkguard.redis.RedisWorkGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/auth/email-verification")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class EmailVerificationController {
 private final EmailVerificationService service;private final RedisWorkGuard guard;
 public EmailVerificationController(EmailVerificationService service,RedisWorkGuard guard){this.service=service;this.guard=guard;}
 public record Confirm(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token){@Override public String toString(){return "Confirm[token=REDACTED]";}}
 @GetMapping public ApiResponse<EmailVerificationService.Status> status(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.status(UUID.fromString(jwt.getSubject())));}
 @PostMapping("/request") public ApiResponse<EmailVerificationService.Status> request(@AuthenticationPrincipal Jwt jwt){
  UUID owner=UUID.fromString(jwt.getSubject());guard.rate("email-verification-request",owner,1);return ApiResponse.ok(service.request(owner));
 }
 @PostMapping("/confirm") public ApiResponse<EmailVerificationService.Status> confirm(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Confirm request){
  UUID owner=UUID.fromString(jwt.getSubject());guard.rate("email-verification-confirm",owner,5);return ApiResponse.ok(service.confirm(owner,request.token()));
 }
}
