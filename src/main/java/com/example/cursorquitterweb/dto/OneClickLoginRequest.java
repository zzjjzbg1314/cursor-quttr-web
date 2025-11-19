package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;

/**
 * 一键登录请求DTO（号码认证）
 */
public class OneClickLoginRequest {
    
    @NotBlank(message = "访问令牌不能为空")
    private String accessToken;

    public OneClickLoginRequest() {
    }

    public OneClickLoginRequest(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public String toString() {
        return "OneClickLoginRequest{" +
                "accessToken='" + accessToken + '\'' +
                '}';
    }
}

