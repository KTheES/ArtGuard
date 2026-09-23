package com.artworkguard.ownership;
import com.artworkguard.artwork.service.ArtworkException;
import com.artworkguard.auth.verification.EmailVerificationService;
import com.artworkguard.marketplace.service.CatalogAccess;
import com.artworkguard.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SourceFileServiceTest {
 final SourceFileRepository repository=mock(SourceFileRepository.class);final OwnershipRepository claims=mock(OwnershipRepository.class);
 final CatalogAccess access=mock(CatalogAccess.class);final EmailVerificationService verification=mock(EmailVerificationService.class);final ObjectStorage storage=mock(ObjectStorage.class);
 final com.artworkguard.audit.AuditWriter audit=mock(com.artworkguard.audit.AuditWriter.class);
 final SourceFileService service=new SourceFileService(repository,claims,access,verification,storage,new SourceFileValidator(),audit);
 final UUID owner=UUID.randomUUID(),id=UUID.randomUUID();final byte[] data=SourceFileValidatorTest.psd();
 SourceFileRepository.Source source(String key){return new SourceFileRepository.Source(id,"PSD",SourceFileService.sha(data),data.length,"HEADER_ONLY",key,null);}
 @Test void validDownloadRecordsAdminAndSubject(){
  UUID subject=UUID.randomUUID();String key="ownership-source/"+id+"/"+UUID.randomUUID()+"/"+SourceFileService.sha(data);
  var claim=mock(OwnershipDtos.Claim.class);when(claim.ownerId()).thenReturn(subject);when(claims.find(id)).thenReturn(Optional.of(claim));
  when(repository.find(id)).thenReturn(Optional.of(source(key)));when(storage.readUpload(key,"application/octet-stream",data.length)).thenReturn(data);
  assertArrayEquals(data,service.download(owner,id));verify(audit).record(com.artworkguard.audit.AuditWriter.Action.SOURCE_FILE_DOWNLOADED,owner,subject,id);
 }
 @Test void foreignOrCompletedClaimCannotUpload(){assertThrows(ArtworkException.class,()->service.upload(owner,id,SourceFileValidator.Format.PSD,data));verifyNoInteractions(storage);}
 @Test void unverifiedCannotUpload(){doThrow(new IllegalStateException()).when(verification).requireVerified(owner);assertThrows(IllegalStateException.class,()->service.upload(owner,id,SourceFileValidator.Format.PSD,data));verifyNoInteractions(storage);}
 @Test void existingAttachmentCannotBeOverwritten(){when(repository.lockPending(owner,id)).thenReturn(true);when(repository.find(id)).thenReturn(Optional.of(source("key")));assertThrows(ArtworkException.class,()->service.upload(owner,id,SourceFileValidator.Format.PSD,data));verifyNoInteractions(storage);}
 @Test void validSourceUsesServerKeyAndDoesNotExposeIt(){when(repository.lockPending(owner,id)).thenReturn(true);when(repository.find(id)).thenReturn(Optional.empty(),Optional.of(source("key")));var result=service.upload(owner,id,SourceFileValidator.Format.PSD,data);assertEquals(SourceFileService.sha(data),result.sha256());var key=org.mockito.ArgumentCaptor.forClass(String.class);verify(storage).putSource(key.capture(),eq(data));assertTrue(key.getValue().startsWith("ownership-source/"+id+"/"));verify(repository).insert(id,"PSD",SourceFileService.sha(data),data.length,"PSD_HEADER_AND_SECTION_BOUNDS_ONLY",key.getValue());}
 @Test void foreignMetadataHidden(){var claim=mock(OwnershipDtos.Claim.class);when(claim.ownerId()).thenReturn(UUID.randomUUID());when(claims.find(id)).thenReturn(Optional.of(claim));assertThrows(ArtworkException.class,()->service.own(owner,id));verifyNoInteractions(repository,storage);}
 @Test void revokedAdminCannotDownload(){doThrow(new IllegalStateException()).when(access).admin(owner);assertThrows(IllegalStateException.class,()->service.download(owner,id));verifyNoInteractions(storage);}
 @Test void storedFileHashIsVerified(){String key="ownership-source/"+id+"/"+UUID.randomUUID()+"/"+SourceFileService.sha(data);when(claims.find(id)).thenReturn(Optional.of(mock(OwnershipDtos.Claim.class)));when(repository.find(id)).thenReturn(Optional.of(source(key)));when(storage.readUpload(key,"application/octet-stream",data.length)).thenReturn(new byte[]{1});assertThrows(ArtworkException.class,()->service.download(owner,id));}
}
