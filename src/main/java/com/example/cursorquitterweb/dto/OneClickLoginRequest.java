package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;

/**
 * 一键登录请求DTO（号码认证）
 */
public class OneClickLoginRequest {
    
    @NotBlank(message = "访问令牌不能为空")
    private String accessToken;

    private String countryCode;

    private String emojiCountry;

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

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getEmojiCountry() {
        return emojiCountry;
    }

    public void setEmojiCountry(String emojiCountry) {
        this.emojiCountry = emojiCountry;
    }

    @Override
    public String toString() {
        return "OneClickLoginRequest{" +
                "accessToken='" + accessToken + '\'' +
                ", countryCode='" + countryCode + '\'' +
                ", emojiCountry='" + emojiCountry + '\'' +
                '}';
    }
}
