package com.artworkguard.auth.jwt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
@Validated
@ConfigurationProperties("artworkguard.auth")
public record AuthProperties(@NotBlank String jwtSecret, @NotBlank String issuer,
                             @NotBlank String audience, @NotNull Duration accessTtl,
                             @NotNull Duration refreshTtl) {
    public AuthProperties {
        if (accessTtl != null && (accessTtl.isNegative() || accessTtl.isZero() || accessTtl.compareTo(Duration.ofHours(1)) > 0))
            throw new IllegalArgumentException("Access TTL must be positive and at most one hour");
        if (refreshTtl != null && (refreshTtl.toSeconds() < 1 || refreshTtl.compareTo(Duration.ofDays(30)) > 0))
            throw new IllegalArgumentException("Refresh TTL must be at least one second and at most 30 days");
    }
    @Override public String toString() { return "AuthProperties[REDACTED]"; }
}
