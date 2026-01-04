package com.example.cursorquitterweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 声网（Agora）配置类
 */
@Configuration
@ConfigurationProperties(prefix = "agora.rtm")
public class AgoraConfig {
    
    /**
     * 声网 App ID
     */
    private String appId;
    
    /**
     * 声网 App Certificate
     */
    private String appCertificate;
    
    /**
     * Token 过期时间（秒，默认 24 小时）
     */
    private int tokenExpireTime = 86400;
    
    public String getAppId() {
        return appId;
    }
    
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    public String getAppCertificate() {
        return appCertificate;
    }
    
    public void setAppCertificate(String appCertificate) {
        this.appCertificate = appCertificate;
    }
    
    public int getTokenExpireTime() {
        return tokenExpireTime;
    }
    
    public void setTokenExpireTime(int tokenExpireTime) {
        this.tokenExpireTime = tokenExpireTime;
    }
}

