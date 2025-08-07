package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信登录请求DTO
 */
public class WechatLoginRequest {
    
    @JsonProperty("code")
    private String code;
    
    public WechatLoginRequest() {}
    
    public WechatLoginRequest(String code) {
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    @Override
    public String toString() {
        return "WechatLoginRequest{" +
                "code='" + code + '\'' +
                '}';
    }
} 