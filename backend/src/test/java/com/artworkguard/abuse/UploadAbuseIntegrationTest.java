package com.artworkguard.abuse;
import com.artworkguard.artwork.service.ArtworkException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") @Testcontainers
class UploadAbuseIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Test void recentTicketsCountEvenWhenUnusedAndOwnersAreIsolated(){
  var source=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
  var jdbc=new JdbcTemplate(source);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));var guard=new UploadAbuseGuard(jdbc);
  jdbc.execute("CREATE TABLE app_user(id UUID PRIMARY KEY,status TEXT,created_at TIMESTAMPTZ); CREATE TABLE artwork_upload(user_id UUID,created_at TIMESTAMPTZ)");
  UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
  jdbc.update("INSERT INTO app_user VALUES(?,'ACTIVE',now()),(?,'ACTIVE',now())",owner,other);
  for(int i=0;i<5;i++)jdbc.update("INSERT INTO artwork_upload VALUES(?,now())",owner);
  assertThrows(ArtworkException.class,()->tx.execute(s->{guard.check(owner);return null;}));
  assertDoesNotThrow(()->tx.execute(s->{guard.check(other);return null;}));
  jdbc.update("UPDATE artwork_upload SET created_at=now()-interval '25 hours'");
  assertDoesNotThrow(()->tx.execute(s->{guard.check(owner);return null;}));
  jdbc.update("UPDATE app_user SET status='SUSPENDED' WHERE id=?",owner);
  assertThrows(com.artworkguard.auth.service.AuthException.class,()->tx.execute(s->{guard.check(owner);return null;}));
 }
}
