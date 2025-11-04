package com.example.cursorquitterweb.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云OSS配置类
 */
@Configuration
public class AliyunOssConfig {

    @Value("${aliyun_oss.endpoint}")
    private String endpoint;

    @Value("${aliyun_oss.access_key_id}")
    private String accessKeyId;

    @Value("${aliyun_oss.access_key_secret}")
    private String accessKeySecret;

    @Bean
    public OSS ossClient() {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}
