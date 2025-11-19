package com.example.cursorquitterweb.config;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云号码认证服务配置类（使用 Tea OpenAPI SDK）
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
    public Client dypnsapiClient() throws Exception {
        // 验证配置
        if (accessKeyId == null || accessKeyId.trim().isEmpty()) {
            throw new IllegalStateException("阿里云号码认证服务 AccessKeyId 未配置，请设置环境变量 ALIYUN_DYPNSAPI_ACCESS_KEY_ID");
        }
        if (accessKeySecret == null || accessKeySecret.trim().isEmpty()) {
            throw new IllegalStateException("阿里云号码认证服务 AccessKeySecret 未配置，请设置环境变量 ALIYUN_DYPNSAPI_ACCESS_KEY_SECRET");
        }

        // 确保 endpoint 不包含协议前缀
        String cleanEndpoint = endpoint.replaceAll("^https?://", "");

        // 使用 AccessKey 方式初始化
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);

        // 设置 endpoint
        config.endpoint = cleanEndpoint;

        return new Client(config);
    }

    public String getRegionId() {
        return regionId;
    }

    public String getEndpoint() {
        // 返回清理后的 endpoint（移除协议前缀）
        return endpoint.replaceAll("^https?://", "");
    }
}

