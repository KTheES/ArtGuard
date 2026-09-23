package com.artworkguard.detection;
import org.springframework.http.HttpStatus;
public class DetectionReviewException extends RuntimeException {
 private final HttpStatus status;private final String code;
 private DetectionReviewException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}
 public HttpStatus getStatus(){return status;}public String getCode(){return code;}
 public static DetectionReviewException missing(){return new DetectionReviewException(HttpStatus.NOT_FOUND,"DETECTION_NOT_FOUND","탐지 결과를 찾을 수 없습니다.");}
 public static DetectionReviewException conflict(){return new DetectionReviewException(HttpStatus.CONFLICT,"DETECTION_CONFLICT","탐지 결과가 변경되었습니다. 다시 조회해 주세요.");}
}
