package com.artworkguard.ownership;
import com.artworkguard.artwork.service.ArtworkException;
import com.artworkguard.auth.verification.EmailVerificationService;
import com.artworkguard.marketplace.service.CatalogAccess;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.artworkguard.ownership.OwnershipDtos.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OwnershipServiceTest {
 final OwnershipRepository repository=mock(OwnershipRepository.class);final CatalogAccess access=mock(CatalogAccess.class);
 final EmailVerificationService verification=mock(EmailVerificationService.class);final OwnershipService service=new OwnershipService(repository,access,verification);
 final UUID owner=UUID.randomUUID(),art=UUID.randomUUID(),id=UUID.randomUUID(),admin=UUID.randomUUID();
 final Submit submission=new Submit("https://example.com/art","원본 게시 자료",true);
 Claim claim(Status status){return new Claim(id,art,owner,submission.publicationUrl(),submission.statement(),status,null,null,0,null,null);}
 @Test void foreignArtworkCannotSubmit(){assertThrows(ArtworkException.class,()->service.submit(owner,art,submission));verify(repository,never()).insert(any(),any(),any(),any());}
 @Test void unverifiedCannotSubmit(){when(repository.owned(owner,art,true)).thenReturn(true);doThrow(new IllegalStateException()).when(verification).requireVerified(owner);assertThrows(IllegalStateException.class,()->service.submit(owner,art,submission));verify(repository,never()).insert(any(),any(),any(),any());}
 @Test void pendingConflictDoesNotOverwrite(){when(repository.owned(owner,art,true)).thenReturn(true);assertThrows(ArtworkException.class,()->service.submit(owner,art,submission));}
 @Test void createsClaimWithJwtOwner(){when(repository.owned(owner,art,true)).thenReturn(true);when(repository.insert(any(),eq(owner),eq(art),eq(submission))).thenReturn(true);when(repository.find(any())).thenReturn(Optional.of(claim(Status.PENDING)));assertEquals(owner,service.submit(owner,art,submission).ownerId());}
 @Test void unsafeSchemesAndCredentialsRejected(){for(String url:List.of("http://example.com","javascript:alert(1)","https://user:pass@example.com/a","https://example.com:8443/a","https://example.com/#x"))assertFalse(OwnershipService.validUrl(url));assertTrue(OwnershipService.validUrl("https://example.com/a"));}
 @Test void ownListCannotUseForeignArtwork(){assertThrows(ArtworkException.class,()->service.own(owner,art,0,20));verify(repository,never()).list(any(),any(),any(),anyInt(),anyInt());}
 @Test void revokedAdminCannotReadQueue(){doThrow(new IllegalStateException()).when(access).admin(admin);assertThrows(IllegalStateException.class,()->service.queue(admin,Status.PENDING,0,20));verifyNoInteractions(repository);}
 @Test void selfReviewRejected(){when(repository.find(id)).thenReturn(Optional.of(claim(Status.PENDING)));assertThrows(ArtworkException.class,()->service.review(owner,id,new Review(Status.ACCEPTED,"checked",0L)));verify(repository,never()).review(any(),any(),any());}
 @Test void completedReviewCannotChange(){when(repository.find(id)).thenReturn(Optional.of(claim(Status.ACCEPTED)));assertThrows(ArtworkException.class,()->service.review(admin,id,new Review(Status.REJECTED,"changed",0L)));}
 @Test void staleReviewRejected(){when(repository.find(id)).thenReturn(Optional.of(claim(Status.PENDING)));assertThrows(ArtworkException.class,()->service.review(admin,id,new Review(Status.ACCEPTED,"checked",0L)));}
 @Test void successfulReviewRetainsActor(){when(repository.find(id)).thenReturn(Optional.of(claim(Status.PENDING)),Optional.of(claim(Status.ACCEPTED)));var review=new Review(Status.ACCEPTED,"checked",0L);when(repository.review(id,admin,review)).thenReturn(true);assertEquals(Status.ACCEPTED,service.review(admin,id,review).status());verify(access).admin(admin);}
}
