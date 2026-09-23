package com.artworkguard.ownership;
import com.artworkguard.artwork.service.ArtworkException;
import com.artworkguard.auth.verification.EmailVerificationService;
import com.artworkguard.marketplace.service.CatalogAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.net.URI;
import java.util.UUID;
import static com.artworkguard.ownership.OwnershipDtos.*;
@Service
public class OwnershipService {
 private final OwnershipRepository repository;private final CatalogAccess access;private final EmailVerificationService verification;
 public OwnershipService(OwnershipRepository repository,CatalogAccess access,EmailVerificationService verification){this.repository=repository;this.access=access;this.verification=verification;}
 @Transactional public Claim submit(UUID owner,UUID artwork,Submit request){
  access.active(owner);
  if(!repository.owned(owner,artwork,true))throw ArtworkException.notFound();
  verification.requireVerified(owner);
  if(!Boolean.TRUE.equals(request.authorized()) || !validUrl(request.publicationUrl()))throw new ArtworkException(HttpStatus.BAD_REQUEST,"OWNERSHIP_INVALID","권한 확인과 유효한 HTTPS 게시 URL이 필요합니다.");
  UUID id=UUID.randomUUID();if(!repository.insert(id,owner,artwork,request))throw conflict();
  return repository.find(id).orElseThrow(ArtworkException::notFound);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Page own(UUID owner,UUID artwork,int page,int size){
  access.active(owner);if(!repository.owned(owner,artwork,false))throw ArtworkException.notFound();return repository.list(owner,artwork,null,page,size);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Page queue(UUID admin,Status status,int page,int size){
  access.admin(admin);return repository.list(null,null,status,page,size);
 }
 @Transactional public Claim review(UUID admin,UUID id,Review request){
  access.admin(admin);var claim=repository.find(id).orElseThrow(ArtworkException::notFound);
  if(claim.ownerId().equals(admin) || request.status()==Status.PENDING || claim.status()!=Status.PENDING)throw conflict();
  if(!repository.review(id,admin,request))throw conflict();return repository.find(id).orElseThrow(ArtworkException::notFound);
 }
 static boolean validUrl(String value){
  try{var uri=URI.create(value);return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null && uri.getFragment()==null && (uri.getPort()==-1 || uri.getPort()==443);}
  catch(RuntimeException e){return false;}
 }
 private static ArtworkException conflict(){return new ArtworkException(HttpStatus.CONFLICT,"OWNERSHIP_CONFLICT","이미 검토 중이거나 변경된 자료입니다. 본인 자료는 직접 검토할 수 없습니다.");}
}
