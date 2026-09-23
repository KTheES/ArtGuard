package com.artworkguard.billing;

import org.springframework.http.HttpStatus;

public class BillingException extends RuntimeException {
 private final HttpStatus status;private final String code;
 public BillingException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}
 public HttpStatus status(){return status;}public String code(){return code;}
 static BillingException unavailable(){return new BillingException(HttpStatus.SERVICE_UNAVAILABLE,"BILLING_UNAVAILABLE","결제 서비스를 사용할 수 없습니다.");}
 static BillingException invalidPlan(){return new BillingException(HttpStatus.BAD_REQUEST,"INVALID_PLAN","결제 가능한 플랜을 선택해 주세요.");}
 static BillingException invalidKey(){return new BillingException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","유효한 Idempotency-Key 헤더가 필요합니다.");}
 static BillingException conflict(){return new BillingException(HttpStatus.CONFLICT,"BILLING_CONFLICT","동일한 요청 키가 다른 결제 요청에 사용되었습니다.");}
 static BillingException invalidWebhook(){return new BillingException(HttpStatus.BAD_REQUEST,"INVALID_WEBHOOK","결제 웹훅을 확인할 수 없습니다.");}
}
