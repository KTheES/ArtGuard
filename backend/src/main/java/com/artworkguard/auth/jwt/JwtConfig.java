package com.artworkguard.auth.jwt;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.util.Base64;
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean SecretKey jwtSigningKey(AuthProperties properties) {
        byte[] key;
        try { key = Base64.getDecoder().decode(properties.jwtSecret()); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("JWT_SECRET must be Base64 encoded"); }
        if (key.length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes");
        return new SecretKeySpec(key, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key, AuthProperties properties) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean valid = jwt.getAudience() != null && jwt.getAudience().contains(properties.audience())
                && "access".equals(jwt.getClaimAsString("token_use"))
                && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null && jwt.getSubject() != null;
            if (valid) {
                try { java.util.UUID.fromString(jwt.getSubject()); }
                catch (IllegalArgumentException exception) { valid = false; }
            }
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Invalid access token claims", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.issuer()), claims));
        return decoder;
    }
}
