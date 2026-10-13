package com.friendlystation.platform.common;

public record ApiResponse<T>(Integer code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(500, message, null);
    }

    public static <T> ApiResponse<T> unauthorized(String message) {
        return new ApiResponse<>(401, message, null);
    }

    public static <T> ApiResponse<T> forbidden(String message) {
        return new ApiResponse<>(403, message, null);
    }
}
