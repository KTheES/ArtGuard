package com.artworkguard.marketplace.aliexpress;
import org.springframework.http.HttpStatus;
public class AliException extends RuntimeException {
 private final HttpStatus status;private final String code;
 public AliException(HttpStatus status,String code){super(code);this.status=status;this.code=code;}
 public HttpStatus getStatus(){return status;}public String getCode(){return code;}
 public static AliException invalid(){return new AliException(HttpStatus.BAD_GATEWAY,"ALIEXPRESS_INVALID_RESPONSE");}
}
