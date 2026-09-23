package com.artworkguard.abuse;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class UploadAbuseGuardTest {
 @Test void sevenDayBoundaryAndFutureClockAreConservative(){
  Instant now=Instant.parse("2026-09-10T00:00:00Z");
  assertEquals(5,UploadAbuseGuard.dailyLimit(now,now));
  assertEquals(5,UploadAbuseGuard.dailyLimit(now.minusSeconds(7*86400-1),now));
  assertEquals(50,UploadAbuseGuard.dailyLimit(now.minusSeconds(7*86400),now));
  assertEquals(5,UploadAbuseGuard.dailyLimit(now.plusSeconds(10),now));
 }
 @Test void sourceHashIdentifiesExactUploadedBytes(){
  assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",ArtworkSourceRepository.sha256("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
 }
}
