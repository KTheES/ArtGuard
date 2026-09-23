package com.artworkguard.audit;
import com.artworkguard.auth.service.*;
import com.artworkguard.auth.dto.AuthDtos;
import com.artworkguard.auth.jwt.*;
import com.artworkguard.user.domain.AppUser;
import com.artworkguard.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AuthAuditTest {
 final UserRepository users=mock(UserRepository.class);final PasswordEncoder passwords=mock(PasswordEncoder.class);
 final RefreshTokenStore refresh=mock(RefreshTokenStore.class);final AccessTokenService tokens=mock(AccessTokenService.class);
 final AuthProperties properties=mock(AuthProperties.class);final AuditWriter writer=mock(AuditWriter.class);
 final AuthService service=new AuthService(users,passwords,refresh,tokens,properties,writer);
 @Test void deniedLoginDoesNotClaimSuccess(){assertThrows(AuthException.class,()->service.login(new AuthDtos.LoginRequest("test@example.com","password")));verifyNoInteractions(writer);}
 @Test void successfulLoginLogsIdsOnly(){var user=new AppUser("test@example.com","hash","test");when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));when(passwords.matches("password","hash")).thenReturn(true);when(properties.refreshTtl()).thenReturn(Duration.ofDays(1));when(properties.accessTtl()).thenReturn(Duration.ofMinutes(10));service.login(new AuthDtos.LoginRequest(user.getEmail(),"password"));verify(writer).record(AuditWriter.Action.LOGIN,user.getId(),user.getId(),user.getId());}
}
