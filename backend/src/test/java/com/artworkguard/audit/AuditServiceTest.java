package com.artworkguard.audit;
import com.artworkguard.marketplace.service.CatalogAccess;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AuditServiceTest {
 final CatalogAccess access=mock(CatalogAccess.class);final AuditRepository repository=mock(AuditRepository.class);final AuditWriter writer=mock(AuditWriter.class);
 final AuditService service=new AuditService(access,repository,writer);final UUID admin=UUID.randomUUID(),subject=UUID.randomUUID();
 @Test void revokedAdminCannotReadOrWriteAudit(){doThrow(new IllegalStateException()).when(access).admin(admin);assertThrows(IllegalStateException.class,()->service.list(admin,null,null,null,50));verifyNoInteractions(repository,writer);}
 @Test void filtersPassThroughAndReadIsAudited(){var page=new AuditRepository.Page(List.of(),null);when(repository.list(100L,subject,AuditRepository.Type.LOGIN,50)).thenReturn(page);assertSame(page,service.list(admin,100L,subject,AuditRepository.Type.LOGIN,50));var order=inOrder(access,repository,writer);order.verify(access).admin(admin);order.verify(repository).list(100L,subject,AuditRepository.Type.LOGIN,50);order.verify(writer).record(AuditWriter.Action.ADMIN_AUDIT_READ,admin,admin,admin);}
 @Test void auditFailureDoesNotReturnPage(){doThrow(new IllegalStateException()).when(writer).record(any(),any(),any(),any());assertThrows(IllegalStateException.class,()->service.list(admin,null,null,null,50));}
}
