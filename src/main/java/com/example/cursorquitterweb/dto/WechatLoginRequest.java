package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信登录请求DTO
 */
public class WechatLoginRequest {
    
    @JsonProperty("code")
    private String code;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("emoji_country")
    private String emojiCountry;
    
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
        return "WechatLoginRequest{" +
                "code='" + code + '\'' +
                ", countryCode='" + countryCode + '\'' +
                ", emojiCountry='" + emojiCountry + '\'' +
                '}';
    }
}
