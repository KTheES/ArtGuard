package com.artworkguard.audit;
import com.artworkguard.marketplace.service.CatalogAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class AuditService {
 private final CatalogAccess access;private final AuditRepository repository;private final AuditWriter writer;
 public AuditService(CatalogAccess access,AuditRepository repository,AuditWriter writer){this.access=access;this.repository=repository;this.writer=writer;}
 @Transactional public AuditRepository.Page list(UUID admin,Long before,UUID subject,AuditRepository.Type type,int limit){
  access.admin(admin);var page=repository.list(before,subject,type,limit);writer.record(AuditWriter.Action.ADMIN_AUDIT_READ,admin,admin,admin);return page;
 }
}
