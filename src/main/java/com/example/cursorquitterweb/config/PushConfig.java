package com.example.cursorquitterweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * U-Push（友盟推送）配置类
 */
@Configuration
@ConfigurationProperties(prefix = "upush")
public class PushConfig {
    
    /**
     * U-Push API 基础 URL
     */
    private String baseUrl = "https://msgapi.umeng.com";
    
    /**
     * iOS App Key（需要从友盟控制台获取）
     */
    private String iosAppKey;
    
    /**
     * iOS App Master Secret
     */
    private String iosMasterSecret = "vvkynsxnwyszyseh8isgimvbs8upi5ep";
    
    /**
     * Android App Key（需要从友盟控制台获取）
     */
    private String androidAppKey;
    
    /**
     * Android App Master Secret
     */
    private String androidMasterSecret = "s6uz1fhnt7qbp6qwguu7rjwhbkwlk8tt";
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public String getIosAppKey() {
        return iosAppKey;
    }
    
    public void setIosAppKey(String iosAppKey) {
        this.iosAppKey = iosAppKey;
    }
    
    public String getIosMasterSecret() {
        return iosMasterSecret;
    }
    
    public void setIosMasterSecret(String iosMasterSecret) {
        this.iosMasterSecret = iosMasterSecret;
    }
    
    public String getAndroidAppKey() {
        return androidAppKey;
    }
    
    public void setAndroidAppKey(String androidAppKey) {
        this.androidAppKey = androidAppKey;
    }
    
    public String getAndroidMasterSecret() {
        return androidMasterSecret;
    }
    
    public void setAndroidMasterSecret(String androidMasterSecret) {
        this.androidMasterSecret = androidMasterSecret;
    }
}
