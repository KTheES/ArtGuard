package com.artworkguard.common.response;
import java.time.Instant;
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {
    public record ApiError(String code, String message) {}
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }
    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message), Instant.now());
    }
}