package com.artworkguard.redis;
import org.springframework.http.HttpStatus;
public class RedisGuardException extends RuntimeException {
 private final HttpStatus status;private final String code;private final int retryAfter;
 public RedisGuardException(HttpStatus status,String code,int retryAfter){super(code);this.status=status;this.code=code;this.retryAfter=retryAfter;}
 public HttpStatus getStatus(){return status;}public String getCode(){return code;}public int getRetryAfter(){return retryAfter;}
 public static RedisGuardException unavailable(){return new RedisGuardException(HttpStatus.SERVICE_UNAVAILABLE,"REDIS_GUARD_UNAVAILABLE",5);}
}
