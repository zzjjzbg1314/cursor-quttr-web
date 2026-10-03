package com.example.cursorquitterweb.musicmv.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 只提取安全业务摘要，原样返回响应，不缓存媒体流。 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
@ControllerAdvice(basePackages = "com.example.cursorquitterweb.musicmv")
public class MusicMvBusinessLogAdvice implements ResponseBodyAdvice<Object> {
    private static final String[] FIELDS = {"code", "retryable", "status", "available", "enabled",
            "jobId", "projectId", "assetId", "remaining", "received"};
    @Override public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body, MethodParameter method, MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest) || !(body instanceof Map)) return body;
        javax.servlet.http.HttpServletRequest servlet = ((ServletServerHttpRequest) request).getServletRequest();
        if (!servlet.getRequestURI().startsWith("/api/music-mv/v1/")) return body;
        Map<String,Object> summary = new LinkedHashMap<>();
        for (String key : FIELDS) {
            Object value = ((Map<?,?>) body).get(key);
            // 限制为短标识或标量，禁止换行注入和嵌套用户内容。
            if (value instanceof Boolean || value instanceof Number
                    || value instanceof String && ((String) value).matches("[A-Za-z0-9_.:-]{1,100}")) summary.put(key, value);
        }
        servlet.setAttribute(MusicMvPerformanceFilter.BUSINESS_ATTRIBUTE, summary);
        return body;
    }
}
