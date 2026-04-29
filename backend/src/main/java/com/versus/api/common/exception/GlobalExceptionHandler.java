package com.versus.api.common.exception;

import com.versus.api.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        ErrorCode code = ex.getCode();
        return ResponseEntity.status(code.status()).body(
                new ErrorResponse(code.name(), ex.getMessage(), code.status().value()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(
                new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), msg, 400));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status()).body(
                new ErrorResponse(ErrorCode.FORBIDDEN.name(), "Access denied", 403));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.status()).body(
                new ErrorResponse(ErrorCode.UNAUTHORIZED.name(), ex.getMessage(), 401));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(
                new ErrorResponse(ErrorCode.INTERNAL_ERROR.name(), "Internal server error", 500));
    }
}
