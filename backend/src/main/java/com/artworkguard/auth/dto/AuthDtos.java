package com.artworkguard.auth.dto;
import com.artworkguard.user.domain.AppUser;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
public final class AuthDtos {
    private AuthDtos() {}
    public record SignupRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 50) String nickname
    ) {
        public SignupRequest {
            email = normalizeEmail(email);
            nickname = nickname == null ? null : nickname.strip();
        }
        @AssertTrue(message = "Password must be at most 72 UTF-8 bytes")
        public boolean isPasswordWithinBcryptLimit() { return fitsBcrypt(password); }
        @Override public String toString() { return "SignupRequest[REDACTED]"; }
    }
    public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password
    ) {
        public LoginRequest { email = normalizeEmail(email); }
        @AssertTrue(message = "Password must be at most 72 UTF-8 bytes")
        public boolean isPasswordWithinBcryptLimit() { return fitsBcrypt(password); }
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
    public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {
        @Override public String toString() { return "RefreshRequest[REDACTED]"; }
    }
    public record UserResponse(UUID id, String email, String nickname, AppUser.Role role) {
        public static UserResponse from(AppUser user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
        }
    }
    public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                                long expiresIn, long refreshExpiresIn) {
        @Override public String toString() { return "TokenResponse[REDACTED]"; }
    }
    private static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
    private static boolean fitsBcrypt(String password) {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
