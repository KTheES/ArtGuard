package com.artworkguard.subscription;

import org.springframework.http.HttpStatus;

public class SubscriptionException extends RuntimeException {
 private final HttpStatus status;private final String code;
 public SubscriptionException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}
 public HttpStatus status(){return status;}public String code(){return code;}
 static SubscriptionException artworkLimit(){return new SubscriptionException(HttpStatus.FORBIDDEN,"ARTWORK_LIMIT_REACHED","현재 플랜의 작품 등록 한도에 도달했습니다.");}
 static SubscriptionException evidence(){return new SubscriptionException(HttpStatus.FORBIDDEN,"FEATURE_NOT_INCLUDED","현재 플랜에는 증거 열람 기능이 포함되지 않습니다.");}
}
