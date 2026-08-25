package com.apps.deen_sa.web;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.apps.deen_sa.web")
public class WebApiExceptionHandler {
    @ExceptionHandler(WebApiException.class)
    public ResponseEntity<ApiError> apiError(WebApiException error) {
        return ResponseEntity.status(error.status()).body(new ApiError(error.code(), error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidParameter(MethodArgumentTypeMismatchException error) {
        if ("month".equals(error.getName())) return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_MONTH", "month must use YYYY-MM format"));
        return ResponseEntity.badRequest().body(new ApiError("INVALID_PARAMETER", "Invalid request parameter"));
    }

    public record ApiError(String error, String message) { }
}
