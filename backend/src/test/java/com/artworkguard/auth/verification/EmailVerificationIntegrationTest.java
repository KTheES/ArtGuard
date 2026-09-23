package com.artworkguard.auth.verification;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") @Testcontainers
class EmailVerificationIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Test void tokenRotationExpirySingleUseAndEmailChange()throws Exception{
  var source=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());var jdbc=new JdbcTemplate(source);
  var repository=new EmailVerificationRepository(jdbc);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
  jdbc.execute("CREATE TABLE app_user(id UUID PRIMARY KEY,email VARCHAR(254),status TEXT,updated_at TIMESTAMPTZ)");
  try(var input=getClass().getResourceAsStream("/db/migration/V21__email_verification.sql")){jdbc.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
  UUID owner=UUID.randomUUID(),other=UUID.randomUUID();String first="a".repeat(64),second="b".repeat(64);
  jdbc.update("INSERT INTO app_user(id,email,status) VALUES(?,'owner@example.com','ACTIVE'),(?,'other@example.com','ACTIVE')",owner,other);
  assertTrue(repository.issue(owner,"owner@example.com",first));assertFalse(repository.issue(owner,"owner@example.com",second));
  jdbc.update("UPDATE email_verification SET last_sent_at=now()-interval '6 minutes'");assertTrue(repository.issue(owner,"owner@example.com",second));
  assertFalse(repository.consume(owner,first));assertFalse(repository.consume(other,second));
  jdbc.update("UPDATE email_verification SET expires_at=now()-interval '1 second'");assertFalse(repository.consume(owner,second));
  jdbc.update("UPDATE email_verification SET expires_at=now()+interval '1 minute'");
  Boolean consumed=tx.execute(s->{repository.account(owner,true);return repository.consume(owner,second);});
  assertEquals(Boolean.TRUE,consumed);assertFalse(repository.consume(owner,second));
  assertTrue(repository.account(owner,false).verified());jdbc.update("UPDATE app_user SET email='new@example.com' WHERE id=?",owner);assertFalse(repository.account(owner,false).verified());
 }
}
