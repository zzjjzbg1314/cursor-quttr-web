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
        com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setCredential(credential);
        // Endpoint 请参考 https://api.aliyun.com/product/Dypnsapi
        config.endpoint = "dypnsapi.aliyuncs.com";
        return new com.aliyun.dypnsapi20170525.Client(config);
    }

    public String getRegionId() {
        return regionId;
    }

    public String getEndpoint() {
        // 返回清理后的 endpoint（移除协议前缀）
        return endpoint.replaceAll("^https?://", "");
    }
}

