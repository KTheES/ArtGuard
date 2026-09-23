package com.artworkguard.seller;
import com.artworkguard.marketplace.service.CatalogAccess;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SellerIntelligenceServiceTest {
 final CatalogAccess access=mock(CatalogAccess.class);
 final SellerIntelligenceRepository repository=mock(SellerIntelligenceRepository.class);
 final SellerIntelligenceService service=new SellerIntelligenceService(access,repository);
 final UUID owner=UUID.randomUUID();
 @Test void ownScopeAlwaysPassesOwner(){service.own(owner,MarketplaceCode.ALIEXPRESS,2,10);verify(access).active(owner);verify(repository).list(owner,"ALIEXPRESS",2,10);}
 @Test void adminScopeRequiresDatabaseRoleCheck(){service.admin(owner,null,0,20);var order=inOrder(access,repository);order.verify(access).admin(owner);order.verify(repository).list(null,null,0,20);}
 @Test void revokedAdminCannotAggregate(){doThrow(new IllegalStateException()).when(access).admin(owner);assertThrows(IllegalStateException.class,()->service.admin(owner,null,0,20));verifyNoInteractions(repository);}
 @Test void inactiveUserCannotAggregate(){when(access.active(owner)).thenThrow(new IllegalStateException());assertThrows(IllegalStateException.class,()->service.own(owner,null,0,20));verifyNoInteractions(repository);}
}
