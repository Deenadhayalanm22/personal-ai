package com.apps.deen_sa.web;

import org.springframework.http.HttpStatus;

public class WebApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public WebApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
