package com.artworkguard.auth.verification;
import com.artworkguard.auth.service.AuthException;
import com.artworkguard.notification.EmailGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class EmailVerificationServiceTest {
 final EmailVerificationRepository repository=mock(EmailVerificationRepository.class);
 final ObjectProvider<EmailGateway> provider=mock(ObjectProvider.class);
 final EmailGateway gateway=mock(EmailGateway.class);
 final EmailVerificationService service=new EmailVerificationService(repository,provider,true);
 final UUID owner=UUID.randomUUID();final String token="a".repeat(43);
 void account(){when(repository.account(owner,true)).thenReturn(new EmailVerificationRepository.Account("owner@example.com",false));when(provider.getIfAvailable()).thenReturn(gateway);}
 @Test void storesHashAndSendsOnlyToRegisteredEmail(){
  account();when(repository.issue(eq(owner),eq("owner@example.com"),any())).thenReturn(true);
  assertFalse(service.request(owner).verified());
  var body=org.mockito.ArgumentCaptor.forClass(String.class);verify(gateway).send(eq("owner@example.com"),any(),body.capture());
  String sent=body.getValue().split("\n\n")[1];assertTrue(sent.matches("[A-Za-z0-9_-]{43}"));
  verify(repository).issue(owner,"owner@example.com",EmailVerificationService.hash(sent));
 }
 @Test void disabledDeliveryDoesNotIssueToken(){account();assertThrows(AuthException.class,()->new EmailVerificationService(repository,provider,false).request(owner));verify(repository,never()).issue(any(),any(),any());verifyNoInteractions(gateway);}
 @Test void cooldownDoesNotSend(){account();assertThrows(AuthException.class,()->service.request(owner));verifyNoInteractions(gateway);}
 @Test void verifiedRequestIsIdempotent(){when(repository.account(owner,true)).thenReturn(new EmailVerificationRepository.Account("owner@example.com",true));assertTrue(service.request(owner).verified());verifyNoInteractions(provider,gateway);}
 @Test void smtpFailureIsSanitized(){account();when(repository.issue(any(),any(),any())).thenReturn(true);doThrow(new IllegalStateException("secret SMTP details")).when(gateway).send(any(),any(),any());var error=assertThrows(AuthException.class,()->service.request(owner));assertFalse(error.getMessage().contains("secret"));}
 @Test void validTokenIsHashedBeforeConsumption(){account();when(repository.consume(owner,EmailVerificationService.hash(token))).thenReturn(true);assertTrue(service.confirm(owner,token).verified());}
 @Test void consumedExpiredOrWrongOwnerTokenIsRejected(){account();assertThrows(AuthException.class,()->service.confirm(owner,token));}
 @Test void malformedTokenNeverQueriesTokenStore(){account();assertThrows(AuthException.class,()->service.confirm(owner,"bad"));verify(repository,never()).consume(any(),any());}
 @Test void unverifiedCannotPassReportGate(){when(repository.account(owner,false)).thenReturn(new EmailVerificationRepository.Account("owner@example.com",false));assertThrows(AuthException.class,()->service.requireVerified(owner));}
 @Test void verifiedCanPassReportGate(){when(repository.account(owner,false)).thenReturn(new EmailVerificationRepository.Account("owner@example.com",true));assertDoesNotThrow(()->service.requireVerified(owner));}
}
