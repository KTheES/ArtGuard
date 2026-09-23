package com.artworkguard.redis;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@SuppressWarnings("unchecked")
class RedisWorkGuardTest {
 final StringRedisTemplate redis=mock(StringRedisTemplate.class);final ValueOperations<String,String> values=mock(ValueOperations.class);
 final RedisWorkGuard guard=new RedisWorkGuard(redis);
 @Test void excessRequestsReturn429WithRetryAfter(){
  when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenReturn(37L);
  var error=assertThrows(RedisGuardException.class,()->guard.rate("collect",UUID.randomUUID(),10));
  assertEquals(429,error.getStatus().value());assertEquals(37,error.getRetryAfter());
 }
 @Test void redisFailureDoesNotBypassLimits(){
  when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenThrow(new DataAccessResourceFailureException("down"));
  assertEquals(503,assertThrows(RedisGuardException.class,()->guard.rate("collect",UUID.randomUUID(),10)).getStatus().value());
 }
 @Test void existingLockReturnsConflict(){
  when(redis.opsForValue()).thenReturn(values);when(values.setIfAbsent(anyString(),anyString(),any(java.time.Duration.class))).thenReturn(false);
  assertEquals("COLLECTION_IN_PROGRESS",assertThrows(RedisGuardException.class,()->guard.acquire("lock")).getCode());
 }
 @Test void lostLeaseIsNotRenewed(){
  when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenReturn(0L);
  assertEquals("COLLECTION_LEASE_LOST",assertThrows(RedisGuardException.class,()->guard.renew("lock","token")).getCode());
 }
 @Test void postCommitCacheFailureDoesNotFailCollection(){
  when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenThrow(new DataAccessResourceFailureException("down"));
  assertDoesNotThrow(()->guard.publish("lock","token","cache","{}",Map.of()));
  assertDoesNotThrow(()->guard.release("lock","token"));
 }
}
