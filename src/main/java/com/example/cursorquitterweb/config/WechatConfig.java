package com.example.cursorquitterweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信配置类
 */
@Configuration
@ConfigurationProperties(prefix = "wechat")
public class WechatConfig {
    
    /**
     * 微信小程序AppID
     */
    private String appId;
    
    /**
     * 微信小程序AppSecret
     */
    private String appSecret;
    
    /**
     * 微信开放平台 OAuth code 换取 access_token 的 URL
     */
    private String oauthAccessTokenUrl = "https://api.weixin.qq.com/sns/oauth2/access_token";

    /**
     * 微信小程序 code2Session 的 URL（如需兼容小程序可继续使用）
     */
    private String code2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
    
    /**
     * 获取用户信息的URL
     */
    private String userInfoUrl = "https://api.weixin.qq.com/sns/userinfo";
    
    public String getAppId() {
        return appId;
    }
    
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    public String getAppSecret() {
        return appSecret;
    }
    
    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }
    
    public String getCode2SessionUrl() {
        return code2SessionUrl;
    }
    
    public void setCode2SessionUrl(String code2SessionUrl) {
        this.code2SessionUrl = code2SessionUrl;
    }

    public String getOauthAccessTokenUrl() {
        return oauthAccessTokenUrl;
    }

    public void setOauthAccessTokenUrl(String oauthAccessTokenUrl) {
        this.oauthAccessTokenUrl = oauthAccessTokenUrl;
    }
    
    public String getUserInfoUrl() {
        return userInfoUrl;
    }
    
    public void setUserInfoUrl(String userInfoUrl) {
        this.userInfoUrl = userInfoUrl;
    }
} 
