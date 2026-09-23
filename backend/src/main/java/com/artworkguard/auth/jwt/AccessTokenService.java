package com.artworkguard.auth.jwt;
import com.artworkguard.user.domain.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
@Service
public class AccessTokenService {
    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;
    public AccessTokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder; this.properties = properties; this.clock = clock;
    }
    public String issue(AppUser user) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(user.getId().toString())
            .audience(List.of(properties.audience())).issuedAt(now).expiresAt(now.plus(properties.accessTtl()))
            .id(UUID.randomUUID().toString()).claim("token_use", "access")
            .claim("roles", List.of(user.getRole().name())).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
