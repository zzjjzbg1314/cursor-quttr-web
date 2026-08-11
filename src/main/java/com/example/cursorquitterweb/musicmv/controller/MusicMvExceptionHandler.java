package com.example.cursorquitterweb.musicmv.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.cursorquitterweb.musicmv.support.ApiException;

/**
 * Error mapping that is deliberately scoped to the isolated Music MV API.
 * Existing production controllers keep their original exception behaviour.
 */
@RestControllerAdvice(assignableTypes = {
        MusicMvRenderJobController.class,
        RendererMusicMvRenderJobController.class,
        RendererNodeController.class
})
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(error(
                exception.getCode(), exception.getMessage(), exception.isRetryable(), exception.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(
                "VALIDATION_FAILED", "Request validation failed", false, null));
    }

    private Map<String, Object> error(String code, String message, boolean retryable,
                                      Map<String, Object> details) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("message", message);
        body.put("retryable", Boolean.valueOf(retryable));
        if (details != null && !details.isEmpty()) {
            body.put("details", details);
        }
        return body;
    }
}
