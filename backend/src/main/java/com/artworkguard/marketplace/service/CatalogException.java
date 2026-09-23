package com.artworkguard.marketplace.service;
import org.springframework.http.HttpStatus;
public class CatalogException extends RuntimeException {
 private final HttpStatus status;private final String code;
 public CatalogException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}
 public HttpStatus getStatus(){return status;}public String getCode(){return code;}
 public static CatalogException notFound(){return new CatalogException(HttpStatus.NOT_FOUND,"CATALOG_NOT_FOUND","마켓 또는 상품을 찾을 수 없습니다.");}
}
