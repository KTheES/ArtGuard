package com.artworkguard.common.exception;
import com.artworkguard.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> uploadTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(ApiResponse.failure("UPLOAD_TOO_LARGE","첨부 파일은 20 MiB 이하로 제출해 주세요."));
    }
    @ExceptionHandler(com.artworkguard.takedown.TakedownException.class)
    ResponseEntity<ApiResponse<Void>> takedown(com.artworkguard.takedown.TakedownException e) {
        return ResponseEntity.status(409).body(ApiResponse.failure("TAKEDOWN_CONFLICT",e.getMessage()));
    }
    @ExceptionHandler(com.artworkguard.billing.BillingException.class)
    ResponseEntity<ApiResponse<Void>> billing(com.artworkguard.billing.BillingException e) {
        return ResponseEntity.status(e.status()).body(ApiResponse.failure(e.code(), e.getMessage()));
    }
    @ExceptionHandler(com.artworkguard.subscription.SubscriptionException.class)
    ResponseEntity<ApiResponse<Void>> subscription(com.artworkguard.subscription.SubscriptionException e) {
        return ResponseEntity.status(e.status()).body(ApiResponse.failure(e.code(), e.getMessage()));
    }
    @ExceptionHandler(com.artworkguard.marketplace.aliexpress.AliException.class)
    ResponseEntity<ApiResponse<Void>> aliexpress(com.artworkguard.marketplace.aliexpress.AliException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.failure(e.getCode(),"AliExpress 설정 또는 응답을 확인해 주세요."));
    }
    @ExceptionHandler(com.artworkguard.redis.RedisGuardException.class)
    ResponseEntity<ApiResponse<Void>> redisGuard(com.artworkguard.redis.RedisGuardException e) {
        return ResponseEntity.status(e.getStatus()).header("Retry-After",Integer.toString(e.getRetryAfter()))
          .body(ApiResponse.failure(e.getCode(),"잠시 후 다시 시도해 주세요."));
    }
    @ExceptionHandler(com.artworkguard.detection.DetectionReviewException.class)
    ResponseEntity<ApiResponse<Void>> review(com.artworkguard.detection.DetectionReviewException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.failure(e.getCode(), e.getMessage()));
    }
    @ExceptionHandler(com.artworkguard.detection.DetectionException.class)
    ResponseEntity<ApiResponse<Void>> detection(com.artworkguard.detection.DetectionException e) {
        return ResponseEntity.status(409).body(ApiResponse.failure("ARTWORK_EMBEDDING_NOT_READY", e.getMessage()));
    }
    @ExceptionHandler(com.artworkguard.marketplace.service.CatalogException.class)
    ResponseEntity<ApiResponse<Void>> catalog(com.artworkguard.marketplace.service.CatalogException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.failure(e.getCode(), e.getMessage()));
    }
    @ExceptionHandler(com.artworkguard.artwork.service.ArtworkException.class)
    ResponseEntity<ApiResponse<Void>> artwork(com.artworkguard.artwork.service.ArtworkException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> stale(org.springframework.orm.ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(409).body(ApiResponse.failure("ARTWORK_CONFLICT", "작품이 변경되었습니다. 다시 조회해 주세요."));
    }
    @ExceptionHandler(com.artworkguard.auth.service.AuthException.class)
    ResponseEntity<ApiResponse<Void>> auth(com.artworkguard.auth.service.AuthException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> conflict(org.springframework.dao.DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(ApiResponse.failure("DATA_CONFLICT", "이미 등록되었거나 충돌하는 정보입니다."));
    }
    @ExceptionHandler(org.springframework.dao.DataAccessResourceFailureException.class)
    ResponseEntity<ApiResponse<Void>> unavailable(org.springframework.dao.DataAccessResourceFailureException exception) {
        return ResponseEntity.status(503).body(ApiResponse.failure("SERVICE_UNAVAILABLE", "잠시 후 다시 시도해 주세요."));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
        HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class, org.springframework.web.bind.MissingRequestHeaderException.class})
    ResponseEntity<ApiResponse<Void>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("INVALID_REQUEST", "요청 값을 확인해 주세요."));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> missing(NoResourceFoundException exception) {
        return ResponseEntity.status(404).body(ApiResponse.failure("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        return ResponseEntity.internalServerError().body(ApiResponse.failure("INTERNAL_ERROR", "요청을 처리하지 못했습니다."));
    }
}
