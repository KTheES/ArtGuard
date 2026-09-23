package com.artworkguard.auth.service;

import com.artworkguard.auth.dto.AuthDtos.*;
import com.artworkguard.auth.jwt.*;
import com.artworkguard.user.domain.AppUser;
import com.artworkguard.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Duration;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    UserRepository users = mock(UserRepository.class);
    RefreshTokenStore store = mock(RefreshTokenStore.class);
    AccessTokenService tokens = mock(AccessTokenService.class);
    BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    AuthProperties properties = new AuthProperties("test-only-unused", "issuer", "audience", Duration.ofMinutes(15), Duration.ofDays(14));
    AuthService service;
    AppUser user;
    @BeforeEach void setup() {
        service = new AuthService(users, passwords, store, tokens, properties, mock(com.artworkguard.audit.AuditWriter.class));
        user = new AppUser("creator@example.com", passwords.encode("ValidPassword123!"), "Creator");
    }
    @Test void signupNormalizesEmailHashesPasswordAndAssignsUserRole() {
        when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.signup(new SignupRequest(" CREATOR@EXAMPLE.COM ", "ValidPassword123!", " Creator "));
        var capture = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(capture.capture());
        assertThat(result.email()).isEqualTo("creator@example.com");
        assertThat(result.nickname()).isEqualTo("Creator");
        assertThat(result.role()).isEqualTo(AppUser.Role.ROLE_USER);
        assertThat(capture.getValue().getPasswordHash()).isNotEqualTo("ValidPassword123!");
        assertThat(passwords.matches("ValidPassword123!", capture.getValue().getPasswordHash())).isTrue();
    }
    @Test void duplicateSignupDoesNotOverwriteExistingUser() {
        when(users.existsByEmail(user.getEmail())).thenReturn(true);
        assertThatThrownBy(() -> service.signup(new SignupRequest(user.getEmail(), "ValidPassword123!", "Other")))
            .isInstanceOf(AuthException.class);
        verify(users, never()).saveAndFlush(any());
    }
    @Test void invalidPasswordAndUnknownUserReturnSameErrorAndNoTokens() {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.login(new LoginRequest(user.getEmail(), "WrongPassword!")))
            .isInstanceOf(AuthException.class).hasMessage("인증 정보를 확인해 주세요.");
        assertThatThrownBy(() -> service.login(new LoginRequest("unknown@example.com", "WrongPassword!")))
            .isInstanceOf(AuthException.class).hasMessage("인증 정보를 확인해 주세요.");
        verifyNoInteractions(store, tokens);
    }
    @Test void loginStoresOnlyRefreshHashWithTtl() {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokens.issue(user)).thenReturn("access");
        var result = service.login(new LoginRequest(user.getEmail(), "ValidPassword123!"));
        verify(store).save(user.getId(), RefreshTokens.hash(result.refreshToken()), Duration.ofDays(14));
        assertThat(result.expiresIn()).isEqualTo(900);
        assertThat(result.toString()).doesNotContain(result.refreshToken());
    }
    @Test void refreshRejectsReusedToken() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        String token = RefreshTokens.generate(user.getId());
        when(store.rotate(eq(user.getId()), eq(RefreshTokens.hash(token)), anyString(), any())).thenReturn(true, false);
        var result = service.refresh(token);
        assertThat(result.refreshToken()).isNotEqualTo(token);
        assertThatThrownBy(() -> service.refresh(token)).isInstanceOf(AuthException.class);
    }
    @Test void logoutUsesCompareAndDeleteAndIsIdempotent() {
        String token = RefreshTokens.generate(user.getId());
        service.logout(token);
        service.logout(token);
        verify(store, times(2)).revoke(user.getId(), RefreshTokens.hash(token));
    }
    @Test void malformedRefreshTokenNeverTouchesStorage() {
        assertThatThrownBy(() -> service.refresh("bad-token")).isInstanceOf(AuthException.class);
        verifyNoInteractions(store, users);
    }
}
