package com.artworkguard.abuse;
import com.artworkguard.artwork.service.ArtworkException;
import com.artworkguard.auth.service.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.UUID;

@Service
public class UploadAbuseGuard {
 private final JdbcTemplate jdbc;
 public UploadAbuseGuard(JdbcTemplate jdbc){this.jdbc=jdbc;}
 // The enclosing upload transaction keeps this user lock until the ticket is saved.
 @Transactional(propagation=Propagation.MANDATORY)
 public void check(UUID owner){
  var accounts=jdbc.query("SELECT created_at FROM app_user WHERE id=? AND status='ACTIVE' FOR UPDATE",
   (r,n)->r.getTimestamp(1).toInstant(),owner);
  if(accounts.isEmpty())throw AuthException.unauthorized();
  Instant now=jdbc.queryForObject("SELECT transaction_timestamp()",(r,n)->r.getTimestamp(1).toInstant());
  int limit=dailyLimit(accounts.getFirst(),now);
  long count=jdbc.queryForObject("SELECT count(*) FROM artwork_upload WHERE user_id=? AND created_at>?",Long.class,
   owner,java.sql.Timestamp.from(now.minus(Duration.ofDays(1))));
  if(count>=limit)throw new ArtworkException(HttpStatus.TOO_MANY_REQUESTS,"UPLOAD_DAILY_LIMIT",
   "최근 24시간 업로드 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
 }
 static int dailyLimit(Instant created,Instant now){return created.plus(Duration.ofDays(7)).isAfter(now)?5:50;}
}
