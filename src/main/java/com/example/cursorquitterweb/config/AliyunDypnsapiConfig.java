package com.example.cursorquitterweb.config;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.profile.DefaultProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云号码认证服务配置类
 */
@Configuration
public class AliyunDypnsapiConfig {

    @Value("${aliyun_oss.access_key_id}")
    private String accessKeyId;

    @Value("${aliyun_oss.access_key_secret}")
    private String accessKeySecret;

    @Value("${aliyun_dypnsapi.region_id:cn-hangzhou}")
    private String regionId;

    @Value("${aliyun_dypnsapi.endpoint:dypnsapi.aliyuncs.com}")
    private String endpoint;

    @Bean
    public IAcsClient dypnsapiClient() {
        // 验证配置
        if (accessKeyId == null || accessKeyId.trim().isEmpty()) {
            throw new IllegalStateException("阿里云号码认证服务 AccessKeyId 未配置，请设置环境变量 ALIYUN_DYPNSAPI_ACCESS_KEY_ID");
        }
        if (accessKeySecret == null || accessKeySecret.trim().isEmpty()) {
            throw new IllegalStateException("阿里云号码认证服务 AccessKeySecret 未配置，请设置环境变量 ALIYUN_DYPNSAPI_ACCESS_KEY_SECRET");
        }
        
        // 确保 endpoint 不包含协议前缀
        String cleanEndpoint = endpoint.replaceAll("^https?://", "");
        
        DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
        IAcsClient client = new DefaultAcsClient(profile);
        
        // 设置自定义 endpoint（如果需要）
        if (!cleanEndpoint.equals("dypnsapi.aliyuncs.com")) {
            // 注意：DefaultAcsClient 使用 profile 中的 endpoint，通常不需要单独设置
            // 但如果需要自定义，可以通过 profile 设置
        }
        
        return client;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getEndpoint() {
        // 返回清理后的 endpoint（移除协议前缀）
        return endpoint.replaceAll("^https?://", "");
    }
}

