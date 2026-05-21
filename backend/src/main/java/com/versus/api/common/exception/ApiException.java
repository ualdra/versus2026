package com.versus.api.common.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(ErrorCode.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }

    public static ApiException tokenInvalid(String message) {
        return new ApiException(ErrorCode.TOKEN_INVALID, message);
    }

    public static ApiException tokenExpired(String message) {
        return new ApiException(ErrorCode.TOKEN_EXPIRED, message);
    }

    public static ApiException emailNotVerified(String message) {
        return new ApiException(ErrorCode.EMAIL_NOT_VERIFIED, message);
    }
}
