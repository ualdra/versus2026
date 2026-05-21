package com.versus.api.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(HttpStatus.GONE),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
