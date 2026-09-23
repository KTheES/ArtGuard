package com.artworkguard.redis;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;
@Component
public class RedisWorkGuard {
 static final DefaultRedisScript<Long> RATE=new DefaultRedisScript<>("""
  local n=redis.call('INCR',KEYS[1])
  if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[2]) end
  if n>tonumber(ARGV[1]) then return math.max(1,redis.call('TTL',KEYS[1])) end
  return 0
  """,Long.class);
 static final DefaultRedisScript<Long> RELEASE=new DefaultRedisScript<>("""
  if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end
  return 0
  """,Long.class);
 static final DefaultRedisScript<Long> RENEW=new DefaultRedisScript<>("""
  if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('EXPIRE',KEYS[1],120) end
  return 0
  """,Long.class);
 static final DefaultRedisScript<Long> PUBLISH=new DefaultRedisScript<>("""
  if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end
  redis.call('SET',KEYS[2],ARGV[2],'EX',30)
  for i=3,#KEYS do redis.call('SET',KEYS[i],ARGV[i],'EX',60) end
  return 1
  """,Long.class);
 private final StringRedisTemplate redis;
 public RedisWorkGuard(StringRedisTemplate redis){this.redis=redis;}
 public void rate(String operation,UUID actor,int limit){
  try{
   Long seconds=redis.execute(RATE,List.of("ag:v1:rate:{"+actor+"}:"+operation),Integer.toString(limit),"60");
   if(seconds==null)throw RedisGuardException.unavailable();
   if(seconds>0)throw new RedisGuardException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED",seconds.intValue());
  }catch(org.springframework.dao.DataAccessException e){throw RedisGuardException.unavailable();}
 }
 public String acquire(String key){
  String token=UUID.randomUUID().toString();
  try{
   if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key,token,Duration.ofSeconds(120))))
    throw new RedisGuardException(HttpStatus.CONFLICT,"COLLECTION_IN_PROGRESS",2);
   return token;
  }catch(org.springframework.dao.DataAccessException e){throw RedisGuardException.unavailable();}
 }
 public void renew(String key,String token){
  try{if(!Long.valueOf(1).equals(redis.execute(RENEW,List.of(key),token)))throw new RedisGuardException(HttpStatus.CONFLICT,"COLLECTION_LEASE_LOST",2);}
  catch(org.springframework.dao.DataAccessException e){throw RedisGuardException.unavailable();}
 }
 public String read(String key){
  try{return redis.opsForValue().get(key);}catch(org.springframework.dao.DataAccessException e){throw RedisGuardException.unavailable();}
 }
 public void release(String key,String token){try{redis.execute(RELEASE,List.of(key),token);}catch(org.springframework.dao.DataAccessException ignored){}}
 public void publish(String lock,String token,String cache,String value,Map<String,String> markers){
  var keys=new ArrayList<String>(List.of(lock,cache));var args=new ArrayList<String>(List.of(token,value));
  markers.forEach((key,item)->{keys.add(key);args.add(item);});
  // Database is committed already. Cache failure must not turn a successful import into a failed request.
  try{redis.execute(PUBLISH,keys,args.toArray());}catch(org.springframework.dao.DataAccessException ignored){}
 }
}
