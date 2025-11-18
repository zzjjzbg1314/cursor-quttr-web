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

    @Value("${aliyun_dypnsapi.access_key_id}")
    private String accessKeyId;

    @Value("${aliyun_dypnsapi.access_key_secret}")
    private String accessKeySecret;

    @Value("${aliyun_dypnsapi.region_id:cn-hangzhou}")
    private String regionId;

    @Value("${aliyun_dypnsapi.endpoint:https://dypnsapi.aliyuncs.com}")
    private String endpoint;

    @Bean
    public IAcsClient dypnsapiClient() {
        DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
        return new DefaultAcsClient(profile);
    }

    public String getRegionId() {
        return regionId;
    }

    public String getEndpoint() {
        return endpoint;
    }
}

