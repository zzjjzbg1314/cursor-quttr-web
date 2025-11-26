package com.example.cursorquitterweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cloudflare D1 数据库配置
 */
@Configuration
@ConfigurationProperties(prefix = "cloudflare.d1")
public class CloudflareD1Config {
    
    /**
     * Cloudflare API Token
     */
    private String apiToken;
    
    /**
     * Cloudflare Account ID
     */
    private String accountId;
    
    /**
     * D1 Database ID
     */
    private String databaseId;
    
    /**
     * D1 API Base URL
     */
    private String baseUrl = "https://api.cloudflare.com/client/v4";
    
    /**
     * Connection timeout in milliseconds
     */
    private int connectionTimeout = 10000;
    
    /**
     * Read timeout in milliseconds
     */
    private int readTimeout = 30000;

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    
    public int getReadTimeout() {
        return readTimeout;
    }
    
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    /**
     * 获取 D1 API 端点 URL
     */
    public String getApiEndpoint() {
        return String.format("%s/accounts/%s/d1/database/%s", 
            baseUrl, accountId, databaseId);
    }
}

