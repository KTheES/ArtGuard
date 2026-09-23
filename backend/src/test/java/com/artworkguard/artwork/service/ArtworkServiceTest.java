package com.artworkguard.artwork.service;
import com.artworkguard.artwork.domain.*;
import com.artworkguard.artwork.dto.ArtworkDtos.*;
import com.artworkguard.artwork.repository.*;
import com.artworkguard.storage.ObjectStorage;
import com.artworkguard.user.domain.AppUser;
import com.artworkguard.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ArtworkServiceTest {
 ArtworkRepository artworks=mock(ArtworkRepository.class);
 ArtworkUploadRepository uploads=mock(ArtworkUploadRepository.class);
 UserRepository users=mock(UserRepository.class);
 ObjectStorage storage=mock(ObjectStorage.class);
 com.artworkguard.embedding.EmbeddingJobService embeddings=mock(com.artworkguard.embedding.EmbeddingJobService.class);
 com.artworkguard.subscription.SubscriptionService subscriptions=mock(com.artworkguard.subscription.SubscriptionService.class);
 Clock clock=Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"),ZoneOffset.UTC);
 UUID owner=UUID.randomUUID(),id=UUID.randomUUID();
 com.artworkguard.abuse.UploadAbuseGuard abuse=mock(com.artworkguard.abuse.UploadAbuseGuard.class);
 com.artworkguard.abuse.ArtworkSourceRepository sources=mock(com.artworkguard.abuse.ArtworkSourceRepository.class);
 ArtworkService service=new ArtworkService(artworks,uploads,users,storage,new ImageValidator(),clock,embeddings,subscriptions,abuse,sources);
 @Test void quotaBlocksBeforeIssuingStorageTicket(){
  doThrow(new ArtworkException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"UPLOAD_DAILY_LIMIT","limit")).when(abuse).check(owner);
  assertThatThrownBy(()->service.uploadUrl(owner,new UploadRequest("image/png",10))).isInstanceOf(ArtworkException.class);
  verifyNoInteractions(storage,uploads);
 }
 @BeforeEach void active(){when(users.findById(owner)).thenReturn(Optional.of(new AppUser("test@example.com","hash","Creator")));}
 @Test void foreignArtworkReadUpdateDeleteAndImageUrlNeverReachStorage() {
  when(artworks.findByIdAndUserIdAndDeletedFalse(id,owner)).thenReturn(Optional.empty());
  assertThatThrownBy(()->service.get(owner,id)).isInstanceOf(ArtworkException.class);
  assertThatThrownBy(()->service.update(owner,id,new UpdateRequest("New",null,null,0L))).isInstanceOf(ArtworkException.class);
  assertThatThrownBy(()->service.delete(owner,id)).isInstanceOf(ArtworkException.class);
  assertThatThrownBy(()->service.imageUrl(owner,id,false)).isInstanceOf(ArtworkException.class);
  assertThatThrownBy(()->service.requestEmbedding(owner,id)).isInstanceOf(ArtworkException.class);
  assertThatThrownBy(()->service.embeddingStatus(owner,id)).isInstanceOf(ArtworkException.class);
  verifyNoInteractions(storage,embeddings);verify(artworks,never()).flush();
 }
 @Test void foreignUploadIsNotDownloaded() {
  assertThatThrownBy(()->service.create(owner,new CreateRequest(id,"Title",""))).isInstanceOf(ArtworkException.class);
  verifyNoInteractions(storage);
 }
 @Test void expiredUploadIsRejectedBeforeStorage() {
  var ticket=new ArtworkUpload(owner,"image/png",10,clock.instant().minusSeconds(1));
  when(uploads.findOwnedForUpdate(ticket.getId(),owner)).thenReturn(Optional.of(ticket));
  assertThatThrownBy(()->service.create(owner,new CreateRequest(ticket.getId(),"Title",""))).isInstanceOf(ArtworkException.class);
  verifyNoInteractions(storage);
 }
 @Test void creationWritesValidatedFinalKeysAndConsumesTicket()throws Exception {
  byte[] png=ImageValidatorTest.png(20,30);
  var ticket=new ArtworkUpload(owner,"image/png",png.length,clock.instant().plusSeconds(60));
  when(uploads.findOwnedForUpdate(ticket.getId(),owner)).thenReturn(Optional.of(ticket));
  when(storage.readUpload(ticket.getObjectKey(),"image/png",png.length)).thenReturn(png);
  var result=service.create(owner,new CreateRequest(ticket.getId(),"Title","Description"));
  assertThat(ticket.getArtworkId()).isEqualTo(result.id());
  verify(storage).putImage(eq("artworks/"+owner+"/"+result.id()+"/original.png"),any());
  verify(storage).putImage(eq("artworks/"+owner+"/"+result.id()+"/thumbnail.png"),any());
  assertThat(result.width()).isEqualTo(20);
  verify(subscriptions).requireArtworkCapacity(owner);
  verify(embeddings).enqueue(result.id());
  verify(sources).record(result.id(),png,"image/png",20,30);
 }
 @Test void retryReturnsExistingArtworkWithoutWritingAgain() {
  var ticket=new ArtworkUpload(owner,"image/png",10,clock.instant().minusSeconds(1));ticket.consume(id);
  when(uploads.findOwnedForUpdate(ticket.getId(),owner)).thenReturn(Optional.of(ticket));
  when(artworks.findByIdAndUserIdAndDeletedFalse(id,owner)).thenReturn(Optional.of(artwork()));
  assertThat(service.create(owner,new CreateRequest(ticket.getId(),"Title","")).id()).isEqualTo(id);
  verifyNoInteractions(storage);
 }
 @Test void staleUpdateIsRejected() {
  when(artworks.findByIdAndUserIdAndDeletedFalse(id,owner)).thenReturn(Optional.of(artwork()));
  assertThatThrownBy(()->service.update(owner,id,new UpdateRequest("New",null,null,1L))).isInstanceOf(ArtworkException.class);
  verify(artworks,never()).flush();
 }
 @Test void deleteHidesArtworkAndStopsMonitoring() {
  var artwork=artwork();artwork.update(null,null,true,clock.instant());
  when(artworks.findByIdAndUserIdAndDeletedFalse(id,owner)).thenReturn(Optional.of(artwork));
  service.delete(owner,id);
  assertThat(artwork.isDeleted()).isTrue();assertThat(artwork.isMonitoringEnabled()).isFalse();
 }
 @Test void listAlwaysScopesRepositoryQueryToOwner() {
  when(artworks.findByUserIdAndDeletedFalse(eq(owner),any())).thenReturn(new PageImpl<>(List.of(artwork())));
  assertThat(service.list(owner,0,20).items()).hasSize(1);
  verify(artworks).findByUserIdAndDeletedFalse(eq(owner),any());
 }
 @Test void inactiveOrMissingUserCannotRequestUpload() {
  when(users.findById(owner)).thenReturn(Optional.empty());
  assertThatThrownBy(()->service.uploadUrl(owner,new UploadRequest("image/png",10))).isInstanceOf(com.artworkguard.auth.service.AuthException.class);
  verifyNoInteractions(storage,uploads);
 }
 private Artwork artwork(){return new Artwork(id,owner,"Title","",20,30,clock.instant());}
}
