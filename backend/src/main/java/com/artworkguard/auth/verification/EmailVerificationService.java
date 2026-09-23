package com.artworkguard.auth.verification;
import com.artworkguard.auth.service.AuthException;
import com.artworkguard.notification.EmailGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
@Service
public class EmailVerificationService {
 private final EmailVerificationRepository repository;private final ObjectProvider<EmailGateway> email;private final boolean enabled;
 private final SecureRandom random=new SecureRandom();
 public EmailVerificationService(EmailVerificationRepository repository,ObjectProvider<EmailGateway> email,
  @Value("${artworkguard.email-verification.enabled:false}") boolean enabled){this.repository=repository;this.email=email;this.enabled=enabled;}
 public record Status(boolean verified){}
 @Transactional(readOnly=true) public Status status(UUID owner){return new Status(repository.account(owner,false).verified());}
 @Transactional public Status request(UUID owner){
  var account=repository.account(owner,true);
  if(account.verified())return new Status(true);
  var gateway=enabled?email.getIfAvailable():null;
  if(gateway==null)throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE,"EMAIL_VERIFICATION_UNAVAILABLE","이메일 인증 발송 설정이 필요합니다.");
  byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  if(!repository.issue(owner,account.email(),hash(token)))throw new AuthException(HttpStatus.TOO_MANY_REQUESTS,"EMAIL_VERIFICATION_COOLDOWN","인증 메일은 5분 후 다시 요청할 수 있습니다.");
  try{gateway.send(account.email(),"ArtworkGuard 이메일 인증","로그인한 계정에서 다음 인증 코드를 입력해 주세요. 유효기간은 30분입니다.\n\n"+token+"\n\n요청하지 않았다면 이 메일을 무시해 주세요.");}
  catch(RuntimeException e){throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE,"EMAIL_VERIFICATION_DELIVERY_FAILED","인증 메일 발송에 실패했습니다. 잠시 후 다시 요청해 주세요.");}
  return new Status(false);
 }
 @Transactional public Status confirm(UUID owner,String token){
  repository.account(owner,true);
  if(token==null || !token.matches("[A-Za-z0-9_-]{43}") || !repository.consume(owner,hash(token)))
   throw new AuthException(HttpStatus.BAD_REQUEST,"EMAIL_VERIFICATION_INVALID","인증 코드가 올바르지 않거나 만료되었습니다.");
  return new Status(true);
 }
 @Transactional(readOnly=true) public void requireVerified(UUID owner){
  if(!repository.account(owner,false).verified())throw new AuthException(HttpStatus.FORBIDDEN,"EMAIL_VERIFICATION_REQUIRED","신고 준비 전 이메일 인증이 필요합니다.");
 }
 static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
