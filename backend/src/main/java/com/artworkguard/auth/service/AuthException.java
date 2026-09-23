package com.artworkguard.auth.service;
import org.springframework.http.HttpStatus;
public class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public static AuthException unauthorized() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "인증 정보를 확인해 주세요.");
    }
}
