package com.artworkguard.artwork.service;
import com.artworkguard.artwork.domain.*;
import com.artworkguard.artwork.dto.ArtworkDtos.*;
import com.artworkguard.artwork.repository.*;
import com.artworkguard.auth.service.AuthException;
import com.artworkguard.storage.ObjectStorage;
import com.artworkguard.user.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
@Service
public class ArtworkService {
 private static final Duration UPLOAD_TTL=Duration.ofMinutes(10),DOWNLOAD_TTL=Duration.ofSeconds(60);
 private final ArtworkRepository artworks;
 private final ArtworkUploadRepository uploads;
 private final UserRepository users;
 private final ObjectStorage storage;
 private final ImageValidator images;
 private final Clock clock;
 private final com.artworkguard.embedding.EmbeddingJobService embeddings;
 private final com.artworkguard.subscription.SubscriptionService subscriptions;
 private final com.artworkguard.abuse.UploadAbuseGuard abuse;
 private final com.artworkguard.abuse.ArtworkSourceRepository sources;
 public ArtworkService(ArtworkRepository artworks,ArtworkUploadRepository uploads,UserRepository users,ObjectStorage storage,ImageValidator images,Clock clock,com.artworkguard.embedding.EmbeddingJobService embeddings,com.artworkguard.subscription.SubscriptionService subscriptions,com.artworkguard.abuse.UploadAbuseGuard abuse,com.artworkguard.abuse.ArtworkSourceRepository sources) {
  this.artworks=artworks;this.uploads=uploads;this.users=users;this.storage=storage;this.images=images;this.clock=clock;this.embeddings=embeddings;
  this.subscriptions=subscriptions;
  this.abuse=abuse;this.sources=sources;
 }
 @Transactional
 public UploadResponse uploadUrl(UUID owner,UploadRequest request) {
  active(owner);
  abuse.check(owner);
  var ticket=new ArtworkUpload(owner,request.contentType(),request.sizeBytes(),clock.instant().plus(UPLOAD_TTL));
  var signed=storage.uploadUrl(ticket.getObjectKey(),request.contentType(),request.sizeBytes(),UPLOAD_TTL);
  uploads.save(ticket);
  return new UploadResponse(ticket.getId(),signed.url(),signed.headers(),ticket.getExpiresAt());
 }
 @Transactional(timeout=90)
 public ArtworkResponse create(UUID owner,CreateRequest request) {
  active(owner);
  var ticket=uploads.findOwnedForUpdate(request.uploadId(),owner).orElseThrow(ArtworkException::notFound);
  if(ticket.getArtworkId()!=null)return ArtworkResponse.from(owned(owner,ticket.getArtworkId()));
  if(!ticket.getExpiresAt().isAfter(clock.instant()))throw new ArtworkException(HttpStatus.GONE,"UPLOAD_EXPIRED","업로드 요청이 만료되었습니다.");
  subscriptions.requireArtworkCapacity(owner);
  var bytes=storage.readUpload(ticket.getObjectKey(),ticket.getContentType(),ticket.getSizeBytes());
  var validated=images.validate(bytes,ticket.getContentType());
  var artwork=new Artwork(UUID.randomUUID(),owner,request.title(),request.description(),validated.width(),validated.height(),clock.instant());
  // Final keys never appear in upload URLs. Replaying an upload cannot modify a registered artwork.
  storage.putImage(artwork.getOriginalKey(),validated.original());
  storage.putImage(artwork.getThumbnailKey(),validated.thumbnail());
  artworks.saveAndFlush(artwork);
  sources.record(artwork.getId(),bytes,ticket.getContentType(),validated.width(),validated.height());
  ticket.consume(artwork.getId());
  embeddings.enqueue(artwork.getId());
  return ArtworkResponse.from(artwork);
 }
 @Transactional(readOnly=true)
 public ArtworkPage list(UUID owner,int page,int size) {
  active(owner);
  var result=artworks.findByUserIdAndDeletedFalse(owner,PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
  return new ArtworkPage(result.getContent().stream().map(ArtworkResponse::from).toList(),page,size,result.getTotalElements(),result.getTotalPages());
 }
 @Transactional(readOnly=true)
 public ArtworkResponse get(UUID owner,UUID id){active(owner);return ArtworkResponse.from(owned(owner,id));}
 @Transactional
 public ArtworkResponse update(UUID owner,UUID id,UpdateRequest request) {
  active(owner);var artwork=owned(owner,id);
  if(artwork.getVersion()!=request.version())throw new ArtworkException(HttpStatus.CONFLICT,"ARTWORK_CONFLICT","작품이 변경되었습니다. 다시 조회해 주세요.");
  artwork.update(request.title(),request.description(),request.monitoringEnabled(),clock.instant());
  artworks.flush();return ArtworkResponse.from(artwork);
 }
 @Transactional
 public void delete(UUID owner,UUID id){active(owner);var artwork=owned(owner,id);artwork.delete(clock.instant());artworks.flush();}
 @Transactional(readOnly=true)
 public ImageUrlResponse imageUrl(UUID owner,UUID id,boolean thumbnail) {
  active(owner);var artwork=owned(owner,id);
  return new ImageUrlResponse(storage.downloadUrl(thumbnail?artwork.getThumbnailKey():artwork.getOriginalKey(),DOWNLOAD_TTL),clock.instant().plus(DOWNLOAD_TTL));
 }
 @Transactional
 public com.artworkguard.embedding.EmbeddingJobService.JobResponse requestEmbedding(UUID owner,UUID id) {
  active(owner);owned(owner,id);return embeddings.enqueue(id);
 }
 @Transactional(readOnly=true)
 public com.artworkguard.embedding.EmbeddingJobService.JobResponse embeddingStatus(UUID owner,UUID id) {
  active(owner);owned(owner,id);return embeddings.status(id);
 }
 private Artwork owned(UUID owner,UUID id){return artworks.findByIdAndUserIdAndDeletedFalse(id,owner).orElseThrow(ArtworkException::notFound);}
 private void active(UUID owner){users.findById(owner).filter(com.artworkguard.user.domain.AppUser::isActive).orElseThrow(AuthException::unauthorized);}
}
