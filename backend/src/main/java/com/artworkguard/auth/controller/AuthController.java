package com.artworkguard.auth.controller;
import com.artworkguard.auth.dto.AuthDtos.*;
import com.artworkguard.auth.service.AuthService;
import com.artworkguard.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/signup") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signup(@Valid @RequestBody SignupRequest request) { return ApiResponse.ok(auth.signup(request)); }
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) { return ApiResponse.ok(auth.login(request)); }
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) { return ApiResponse.ok(auth.refresh(request.refreshToken())); }
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken()); return ApiResponse.ok(null);
    }
    @GetMapping("/me") @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(auth.me(UUID.fromString(jwt.getSubject()))); }
}
