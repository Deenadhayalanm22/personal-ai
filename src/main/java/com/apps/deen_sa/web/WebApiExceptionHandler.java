package com.apps.deen_sa.web;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = {
        "com.apps.deen_sa.web",
        "com.apps.deen_sa.v2.controller"
})
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException error) {
        if ("month".equals(error.getParameterName())) return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_MONTH", "month must use YYYY-MM format"));
        return ResponseEntity.badRequest().body(new ApiError("INVALID_PARAMETER", "Missing request parameter"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException error) {
        if (error.getStatusCode().value() == 401) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("UNAUTHORIZED", "Your session has expired."));
        return ResponseEntity.status(error.getStatusCode())
                .body(new ApiError("REQUEST_FAILED", "Unable to complete the request."));
    }

    public record ApiError(String code, String message) { }
}
