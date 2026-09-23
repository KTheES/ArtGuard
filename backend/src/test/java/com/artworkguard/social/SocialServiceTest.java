package com.artworkguard.social;
import com.artworkguard.auth.service.AuthException;
import com.artworkguard.auth.verification.EmailVerificationService;
import com.artworkguard.marketplace.service.CatalogAccess;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.artworkguard.social.SocialDtos.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SocialServiceTest {
 final SocialRepository repository=mock(SocialRepository.class);final CatalogAccess access=mock(CatalogAccess.class);
 final EmailVerificationService verification=mock(EmailVerificationService.class);final SocialService service=new SocialService(repository,access,verification);
 final UUID owner=UUID.randomUUID(),admin=UUID.randomUUID(),id=UUID.randomUUID();final String url="https://example.com/profile",code="ArtworkGuard-"+"a".repeat(32);
 Check check(State state){return new Check(id,owner,url,code,state,0,null,null,null,null,null);}
 @Test void unverifiedCannotStart(){doThrow(new IllegalStateException()).when(verification).requireVerified(owner);assertThrows(IllegalStateException.class,()->service.start(owner,new Start(url)));verify(repository,never()).insert(any(),any(),any(),any());}
 @Test void duplicateOrPendingLimitRejected(){assertThrows(AuthException.class,()->service.start(owner,new Start(url)));verify(repository,never()).insert(any(),any(),any(),any());}
 @Test void generatedCodeIsBoundToOwnerAndUrl(){when(repository.canStart(owner,url)).thenReturn(true);when(repository.find(any())).thenReturn(Optional.of(check(State.PENDING)));service.start(owner,new Start(url));var token=org.mockito.ArgumentCaptor.forClass(String.class);verify(repository).insert(any(),eq(owner),eq(url),token.capture());assertTrue(token.getValue().matches("ArtworkGuard-[A-Za-z0-9_-]{32}"));}
 @Test void unsafeUrlsRejectedAndHostNormalized(){for(String invalid:List.of("http://example.com/a","https://user@example.com/a","https://example.com/a?token=x","https://example.com/#x","https://example.com:8443/a","javascript:alert(1)"))assertThrows(AuthException.class,()->SocialService.canonicalUrl(invalid));assertEquals("https://example.com/",SocialService.canonicalUrl("https://EXAMPLE.com:443"));}
 @Test void ownListIsScoped(){service.own(owner,0,20);verify(repository).list(owner,0,20);}
 @Test void revokedAdminCannotReview(){doThrow(new IllegalStateException()).when(access).admin(admin);assertThrows(IllegalStateException.class,()->service.queue(admin,0,20));verifyNoInteractions(repository);}
 @Test void selfReviewCannotVerify(){when(repository.find(id)).thenReturn(Optional.of(check(State.PENDING)));assertThrows(AuthException.class,()->service.review(owner,id,new Review(0L,true,code,true,"checked")));verify(repository,never()).review(any(),any(),any());}
 @Test void mismatchedCodeOrUncheckedProfileRejected(){when(repository.find(id)).thenReturn(Optional.of(check(State.PENDING)));for(var request:List.of(new Review(0L,true,"wrong",true,"checked"),new Review(0L,true,code,false,"checked")))assertThrows(AuthException.class,()->service.review(admin,id,request));verify(repository,never()).review(any(),any(),any());}
 @Test void expiredCannotBeReviewed(){when(repository.find(id)).thenReturn(Optional.of(check(State.EXPIRED)));assertThrows(AuthException.class,()->service.review(admin,id,new Review(0L,true,code,true,"checked")));}
 @Test void staleReviewRejected(){when(repository.find(id)).thenReturn(Optional.of(check(State.PENDING)));assertThrows(AuthException.class,()->service.review(admin,id,new Review(0L,true,code,true,"checked")));}
 @Test void successfulReviewDoesNotExposeChallenge(){when(repository.find(id)).thenReturn(Optional.of(check(State.PENDING)),Optional.of(check(State.VERIFIED)));var request=new Review(0L,true,code,true,"checked");when(repository.review(id,admin,request)).thenReturn(true);var result=service.review(admin,id,request);assertEquals(State.VERIFIED,result.state());assertNull(result.challenge());}
 @Test void foreignRevocationIsHidden(){when(repository.find(id)).thenReturn(Optional.of(check(State.VERIFIED)));assertThrows(AuthException.class,()->service.revoke(admin,id,new Revoke(0L)));verify(repository,never()).revoke(any(),any(),anyLong());}
}
