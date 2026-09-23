package com.artworkguard.auth.service;
import com.artworkguard.auth.dto.AuthDtos.*;
import com.artworkguard.auth.jwt.AccessTokenService;
import com.artworkguard.auth.jwt.AuthProperties;
import com.artworkguard.user.domain.AppUser;
import com.artworkguard.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenStore refreshTokens;
    private final AccessTokenService accessTokens;
    private final AuthProperties properties;
    private final String dummyPasswordHash;
    private final com.artworkguard.audit.AuditWriter audit;
    public AuthService(UserRepository users, PasswordEncoder passwords, RefreshTokenStore refreshTokens,
                       AccessTokenService accessTokens, AuthProperties properties, com.artworkguard.audit.AuditWriter audit) {
        this.users = users; this.passwords = passwords; this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens; this.properties = properties;
        this.audit=audit;
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }
    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (users.existsByEmail(request.email()))
            throw new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 등록된 이메일입니다.");
        var user = new AppUser(request.email(), passwords.encode(request.password()), request.nickname());
        return UserResponse.from(users.saveAndFlush(user));
    }
    public TokenResponse login(LoginRequest request) {
        var user = users.findByEmail(request.email()).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !matches || !user.isActive()) throw AuthException.unauthorized();
        String access = accessTokens.issue(user);
        String refresh = RefreshTokens.generate(user.getId());
        refreshTokens.save(user.getId(), RefreshTokens.hash(refresh), properties.refreshTtl());
        audit.record(com.artworkguard.audit.AuditWriter.Action.LOGIN,user.getId(),user.getId(),user.getId());
        return response(access, refresh);
    }
    public TokenResponse refresh(String token) {
        UUID userId = RefreshTokens.userId(token);
        var user = activeUser(userId);
        String access = accessTokens.issue(user);
        String replacement = RefreshTokens.generate(userId);
        if (!refreshTokens.rotate(userId, RefreshTokens.hash(token), RefreshTokens.hash(replacement), properties.refreshTtl()))
            throw AuthException.unauthorized();
        return response(access, replacement);
    }
    public void logout(String token) {
        UUID userId = RefreshTokens.userId(token);
        // Compare-and-delete prevents a stale token from removing a newer login session.
        refreshTokens.revoke(userId, RefreshTokens.hash(token));
    }
    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) { return UserResponse.from(activeUser(userId)); }
    private AppUser activeUser(UUID userId) {
        return users.findById(userId).filter(AppUser::isActive).orElseThrow(AuthException::unauthorized);
    }
    private TokenResponse response(String access, String refresh) {
        return new TokenResponse(access, refresh, "Bearer", properties.accessTtl().toSeconds(), properties.refreshTtl().toSeconds());
    }
}
