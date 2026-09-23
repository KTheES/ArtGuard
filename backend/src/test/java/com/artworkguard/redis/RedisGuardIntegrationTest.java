package com.artworkguard.redis;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("integration") @Testcontainers
class RedisGuardIntegrationTest {
 @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
 static LettuceConnectionFactory factory;static StringRedisTemplate template;static RedisWorkGuard guard;
 @BeforeAll static void connect(){
  factory=new LettuceConnectionFactory(redis.getHost(),redis.getMappedPort(6379));factory.afterPropertiesSet();factory.start();
  template=new StringRedisTemplate(factory);template.afterPropertiesSet();guard=new RedisWorkGuard(template);
 }
 @AfterAll static void close(){if(factory!=null)factory.destroy();}
 @Test void rateLimitIsAtomicAndExpires(){
  UUID actor=UUID.randomUUID();for(int i=0;i<10;i++)guard.rate("collect",actor,10);
  assertEquals(429,assertThrows(RedisGuardException.class,()->guard.rate("collect",actor,10)).getStatus().value());
  Long ttl=template.getExpire("ag:v1:rate:{"+actor+"}:collect");assertTrue(ttl>0&&ttl<=60);
  guard.rate("collect",UUID.randomUUID(),10);
 }
 @Test void staleHolderCannotDeleteRenewOrPublishOverNewHolder(){
  String key="test:{guard}:lock",cache="test:{guard}:cache",marker="test:{guard}:product";
  String first=guard.acquire(key);
  assertThrows(RedisGuardException.class,()->guard.acquire(key));
  // Simulate expiry/reacquisition without waiting for a wall-clock lease.
  template.opsForValue().set(key,"new-holder");
  guard.release(key,first);assertEquals("new-holder",template.opsForValue().get(key));
  assertThrows(RedisGuardException.class,()->guard.renew(key,first));
  guard.publish(key,first,cache,"bad",Map.of(marker,"bad"));assertNull(template.opsForValue().get(cache));
  guard.publish(key,"new-holder",cache,"good",Map.of(marker,"fingerprint"));
  assertEquals("good",template.opsForValue().get(cache));assertEquals("fingerprint",template.opsForValue().get(marker));
  assertTrue(template.getExpire(cache)>0&&template.getExpire(cache)<=30);
  assertTrue(template.getExpire(marker)>0&&template.getExpire(marker)<=60);
  guard.release(key,"new-holder");assertNull(template.opsForValue().get(key));
 }
}
