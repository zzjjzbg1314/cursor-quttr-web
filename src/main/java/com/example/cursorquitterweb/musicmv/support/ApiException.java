package com.example.cursorquitterweb.musicmv.support;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final boolean retryable;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, false, null);
    }

    public ApiException(HttpStatus status, String code, String message, boolean retryable, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
