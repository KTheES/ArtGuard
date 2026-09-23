package com.artworkguard.auth.jwt;

import com.artworkguard.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class JwtConfigTest {
    private final JwtConfig config = new JwtConfig();
    private final AuthProperties properties = properties();
    private final JwtEncoder encoder = config.jwtEncoder(config.jwtSigningKey(properties));
    private final JwtDecoder decoder = config.jwtDecoder(config.jwtSigningKey(properties), properties);
    private final AppUser user = new AppUser("test@example.com", "unused", "Creator");

    static AuthProperties properties() {
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        return new AuthProperties(Base64.getEncoder().encodeToString(bytes), "artworkguard", "artworkguard-api", Duration.ofMinutes(15), Duration.ofDays(14));
    }
    @Test void issuesVerifiableAccessTokenWithoutPersonalData() {
        String token = new AccessTokenService(encoder, properties, Clock.systemUTC()).issue(user);
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(jwt.getClaims()).doesNotContainKeys("email", "password", "nickname");
    }
    @Test void rejectsExpiredToken() {
        String token = new AccessTokenService(encoder, properties, Clock.fixed(Instant.now().minusSeconds(3600), ZoneOffset.UTC)).issue(user);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsWrongSigningKey() {
        var other = properties();
        String token = new AccessTokenService(config.jwtEncoder(config.jwtSigningKey(other)), other, Clock.systemUTC()).issue(user);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsWrongIssuerAudienceAndTokenType() {
        for (String claim : List.of("issuer", "audience", "token_use")) {
            JwtClaimsSet claims = JwtClaimsSet.builder().issuer(claim.equals("issuer") ? "other" : properties.issuer())
                .subject(user.getId().toString()).audience(List.of(claim.equals("audience") ? "other" : properties.audience()))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900))
                .claim("token_use", claim.equals("token_use") ? "refresh" : "access").build();
            String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }
    }
    @Test void rejectsMissingExpiryAndInvalidSubject() {
        for (boolean missingExpiry : List.of(true, false)) {
            var builder = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(missingExpiry ? user.getId().toString() : "not-a-user-id").issuedAt(Instant.now()).claim("token_use", "access");
            if (!missingExpiry) builder.expiresAt(Instant.now().plusSeconds(900));
            String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }
    }
    @Test void rejectsWeakSigningKey() {
        var weak = new AuthProperties(Base64.getEncoder().encodeToString(new byte[16]), "issuer", "audience", Duration.ofMinutes(15), Duration.ofDays(14));
        assertThatThrownBy(() -> config.jwtSigningKey(weak)).isInstanceOf(IllegalArgumentException.class);
    }
}
