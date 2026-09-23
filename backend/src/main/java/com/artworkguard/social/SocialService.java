package com.artworkguard.social;
import com.artworkguard.auth.service.AuthException;
import com.artworkguard.auth.verification.EmailVerificationService;
import com.artworkguard.marketplace.service.CatalogAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.net.URI;
import java.security.SecureRandom;
import java.util.*;
import static com.artworkguard.social.SocialDtos.*;
@Service
public class SocialService {
 private final SocialRepository repository;private final CatalogAccess access;private final EmailVerificationService verification;
 private final SecureRandom random=new SecureRandom();
 public SocialService(SocialRepository repository,CatalogAccess access,EmailVerificationService verification){this.repository=repository;this.access=access;this.verification=verification;}
 @Transactional public Check start(UUID owner,Start request){
  access.active(owner);repository.lockOwner(owner);verification.requireVerified(owner);
  String url=canonicalUrl(request.profileUrl());if(!repository.canStart(owner,url))throw conflict();
  byte[] bytes=new byte[24];random.nextBytes(bytes);String challenge="ArtworkGuard-"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  UUID id=UUID.randomUUID();repository.insert(id,owner,url,challenge);return repository.find(id).orElseThrow(SocialService::missing);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Page own(UUID owner,int page,int size){access.active(owner);return repository.list(owner,page,size);}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Page queue(UUID admin,int page,int size){access.admin(admin);return repository.list(null,page,size);}
 @Transactional public Check review(UUID admin,UUID id,Review request){
  access.admin(admin);var check=repository.find(id).orElseThrow(SocialService::missing);repository.lockOwner(check.ownerId());
  if(check.ownerId().equals(admin) || check.state()!=State.PENDING || !Boolean.TRUE.equals(request.profileChecked())
   || request.approved()==null || request.approved() && !check.challenge().equals(request.observedCode()))throw conflict();
  if(!repository.review(id,admin,request))throw conflict();return repository.find(id).orElseThrow(SocialService::missing).withoutChallenge();
 }
 @Transactional public Check revoke(UUID owner,UUID id,Revoke request){
  access.active(owner);repository.lockOwner(owner);var check=repository.find(id).filter(c->c.ownerId().equals(owner)).orElseThrow(SocialService::missing);
  if(!repository.revoke(owner,check.id(),request.version()))throw conflict();return repository.find(id).orElseThrow(SocialService::missing);
 }
 static String canonicalUrl(String value){
  try{var u=URI.create(value);if(!"https".equalsIgnoreCase(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null || u.getPort()!=-1 && u.getPort()!=443)throw new IllegalArgumentException();
   return "https://"+u.getHost().toLowerCase(Locale.ROOT)+(u.getRawPath()==null || u.getRawPath().isEmpty()?"/":u.getRawPath());
  }catch(RuntimeException e){throw new AuthException(HttpStatus.BAD_REQUEST,"SOCIAL_URL_INVALID","쿼리·인증 정보 없는 HTTPS 프로필 URL이 필요합니다.");}
 }
 private static AuthException conflict(){return new AuthException(HttpStatus.CONFLICT,"SOCIAL_CHECK_CONFLICT","요청 상태·코드·버전을 확인해 주세요. 중복 요청과 자기 검토는 허용하지 않습니다.");}
 private static AuthException missing(){return new AuthException(HttpStatus.NOT_FOUND,"SOCIAL_CHECK_NOT_FOUND","확인 요청을 찾을 수 없습니다.");}
}
