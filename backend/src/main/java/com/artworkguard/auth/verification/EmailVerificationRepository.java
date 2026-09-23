package com.artworkguard.auth.verification;
import com.artworkguard.auth.service.AuthException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public class EmailVerificationRepository {
 private final JdbcTemplate jdbc;
 public EmailVerificationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Account(String email,boolean verified){}
 public Account account(UUID owner,boolean lock){
  return jdbc.query("SELECT email,email_verified_at IS NOT NULL AS verified FROM app_user WHERE id=? AND status='ACTIVE'"+(lock?" FOR UPDATE":""),
   (r,n)->new Account(r.getString("email"),r.getBoolean("verified")),owner).stream().findFirst().orElseThrow(AuthException::unauthorized);
 }
 public boolean issue(UUID owner,String email,String hash){
  return jdbc.update("""
   INSERT INTO email_verification(user_id,email,token_hash,expires_at,last_sent_at)
   VALUES(?,?,?,clock_timestamp()+interval '30 minutes',clock_timestamp())
   ON CONFLICT(user_id) DO UPDATE SET email=excluded.email,token_hash=excluded.token_hash,
    expires_at=excluded.expires_at,last_sent_at=excluded.last_sent_at
   WHERE email_verification.last_sent_at<=clock_timestamp()-interval '5 minutes'
   """,owner,email,hash)==1;
 }
 public boolean consume(UUID owner,String hash){
  int updated=jdbc.update("""
   UPDATE app_user u SET email_verified_at=clock_timestamp(),updated_at=clock_timestamp()
   WHERE u.id=? AND u.status='ACTIVE' AND u.email_verified_at IS NULL AND EXISTS(
    SELECT 1 FROM email_verification v WHERE v.user_id=u.id AND v.email=u.email
     AND v.token_hash=? AND v.expires_at>clock_timestamp())
   """,owner,hash);
  if(updated==1)jdbc.update("DELETE FROM email_verification WHERE user_id=?",owner);
  return updated==1;
 }
}
