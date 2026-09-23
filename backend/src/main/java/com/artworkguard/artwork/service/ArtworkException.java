package com.artworkguard.artwork.service;
import org.springframework.http.HttpStatus;
public class ArtworkException extends RuntimeException {
 private final HttpStatus status;
 private final String code;
 public ArtworkException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}
 public HttpStatus getStatus(){return status;} public String getCode(){return code;}
 public static ArtworkException notFound(){return new ArtworkException(HttpStatus.NOT_FOUND,"ARTWORK_NOT_FOUND","작품 또는 업로드 요청을 찾을 수 없습니다.");}
 public static ArtworkException invalidImage(){return new ArtworkException(HttpStatus.BAD_REQUEST,"INVALID_IMAGE","PNG/JPEG 형식과 이미지 크기를 확인해 주세요.");}
 public static ArtworkException storageUnavailable(){return new ArtworkException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","이미지 저장소를 사용할 수 없습니다.");}
}
