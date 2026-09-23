package com.artworkguard.subscription;

import com.artworkguard.common.response.ApiResponse;
import com.artworkguard.subscription.SubscriptionDtos.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/subscription")
public class SubscriptionController {
 private final SubscriptionService service;
 public SubscriptionController(SubscriptionService service){this.service=service;}
 @GetMapping("/plans") public ApiResponse<Catalog> plans(){return ApiResponse.ok(service.plans());}
 @GetMapping @SecurityRequirement(name="bearerAuth") public ApiResponse<Current> current(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.current(UUID.fromString(jwt.getSubject())));}
}
