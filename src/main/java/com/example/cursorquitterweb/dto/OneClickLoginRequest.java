package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;

/**
 * 一键登录请求DTO（融合认证）
 */
public class OneClickLoginRequest {
    
    @NotBlank(message = "统一认证Token不能为空")
    private String verifyToken;

    public OneClickLoginRequest() {
    }

    public OneClickLoginRequest(String verifyToken) {
        this.verifyToken = verifyToken;
    }

    public String getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(String verifyToken) {
        this.verifyToken = verifyToken;
    }

    @Override
    public String toString() {
        return "OneClickLoginRequest{" +
                "verifyToken='" + verifyToken + '\'' +
                '}';
    }
}

