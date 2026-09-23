package com.artworkguard.ownership;
import com.artworkguard.artwork.service.ArtworkException;
import com.artworkguard.auth.verification.EmailVerificationService;
import com.artworkguard.marketplace.service.CatalogAccess;
import com.artworkguard.storage.ObjectStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.*;
import java.util.*;
import java.time.Instant;
@Service
public class SourceFileService {
 private final SourceFileRepository repository;private final OwnershipRepository claims;private final CatalogAccess access;
 private final EmailVerificationService verification;private final ObjectStorage storage;private final SourceFileValidator validator;
 private final com.artworkguard.audit.AuditWriter audit;
 public SourceFileService(SourceFileRepository repository,OwnershipRepository claims,CatalogAccess access,EmailVerificationService verification,ObjectStorage storage,SourceFileValidator validator,com.artworkguard.audit.AuditWriter audit){this.repository=repository;this.claims=claims;this.access=access;this.verification=verification;this.storage=storage;this.validator=validator;this.audit=audit;}
 public record Metadata(UUID claimId,String format,String sha256,long sizeBytes,String validation,Instant uploadedAt){}
 private Metadata metadata(SourceFileRepository.Source s){return new Metadata(s.claimId(),s.format(),s.sha256(),s.sizeBytes(),s.validation(),s.uploadedAt());}
 @Transactional(timeout=90) public Metadata upload(UUID owner,UUID claim,SourceFileValidator.Format format,byte[] bytes){
  access.active(owner);verification.requireVerified(owner);if(!repository.lockPending(owner,claim))throw ArtworkException.notFound();
  if(repository.find(claim).isPresent())throw new ArtworkException(HttpStatus.CONFLICT,"SOURCE_FILE_EXISTS","기존 원본 자료는 교체할 수 없습니다. 보완 자료는 새 검토 요청으로 제출해 주세요.");
  String validation=validator.validate(format,bytes),hash=sha(bytes),key="ownership-source/"+claim+"/"+UUID.randomUUID()+"/"+hash;
  storage.putSource(key,bytes);repository.insert(claim,format.name(),hash,bytes.length,validation,key);return metadata(repository.find(claim).orElseThrow());
 }
 @Transactional(readOnly=true) public Optional<Metadata> own(UUID owner,UUID claim){access.active(owner);claims.find(claim).filter(c->c.ownerId().equals(owner)).orElseThrow(ArtworkException::notFound);return repository.find(claim).map(this::metadata);}
 @Transactional(readOnly=true) public Optional<Metadata> admin(UUID admin,UUID claim){access.admin(admin);claims.find(claim).orElseThrow(ArtworkException::notFound);return repository.find(claim).map(this::metadata);}
 @Transactional public byte[] download(UUID admin,UUID claim){
  access.admin(admin);var owned=claims.find(claim).orElseThrow(ArtworkException::notFound);var source=repository.find(claim).orElseThrow(ArtworkException::notFound);
  if(!source.storageKey().startsWith("ownership-source/"+claim+"/") || !source.storageKey().endsWith("/"+source.sha256()))throw ArtworkException.notFound();
  byte[] bytes=storage.readUpload(source.storageKey(),"application/octet-stream",source.sizeBytes());if(!sha(bytes).equals(source.sha256()))throw ArtworkException.notFound();
  audit.record(com.artworkguard.audit.AuditWriter.Action.SOURCE_FILE_DOWNLOADED,admin,owned.ownerId(),claim);return bytes;
 }
 static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
