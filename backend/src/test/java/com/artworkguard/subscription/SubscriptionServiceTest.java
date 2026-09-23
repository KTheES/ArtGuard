package com.artworkguard.subscription;

import com.artworkguard.subscription.SubscriptionDtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class SubscriptionServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final SubscriptionService service=new SubscriptionService(jdbc);final UUID owner=UUID.randomUUID();
 final Plan free=new Plan("FREE","Free",3,168,25,0,false,false,false,false);
 Current current(long used,long remaining){return new Current(free,"FREE","ACTIVE",true,null,false,used,remaining);}
 @Test void currentReportsUsageAndRemainingCapacity(){
  when(jdbc.query(contains("active_artworks"),any(RowMapper.class),eq(owner))).thenReturn(List.of(current(2,1)));
  var current=service.current(owner);assertEquals("FREE",current.plan().code());assertEquals(2,current.activeArtworks());assertEquals(1,current.remainingArtworks());
 }
 @Test void detectionLimitIsCappedByPlan(){
  when(jdbc.query(contains("active_artworks"),any(RowMapper.class),eq(owner))).thenReturn(List.of(current(0,3)));
  assertEquals(25,service.allowedDetectionLimit(owner,500));assertEquals(10,service.allowedDetectionLimit(owner,10));
 }
 @Test void artworkLimitIsCheckedWhileUserRowIsLocked(){
  when(jdbc.query(contains("FOR UPDATE"),any(RowMapper.class),eq(owner))).thenReturn(List.of(owner));
  when(jdbc.query(contains("active_artworks"),any(RowMapper.class),eq(owner))).thenReturn(List.of(current(3,0)));
  assertThrows(SubscriptionException.class,()->service.requireArtworkCapacity(owner));
 }
 @Test void freePlanCannotReadEvidence(){
  when(jdbc.query(contains("active_artworks"),any(RowMapper.class),eq(owner))).thenReturn(List.of(current(0,3)));
  assertThrows(SubscriptionException.class,()->service.requireEvidence(owner));
 }
}
