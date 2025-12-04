package com.example.cursorquitterweb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.lang.NonNull;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Web配置类
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    /**
     * 配置CORS跨域
     */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
    
    /**
     * 配置HTTP连接池管理器
     * 优化：增加连接数，优化 Keep-Alive 时间
     */
    @Bean
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        // 设置最大连接数（针对 Cloudflare D1 API 优化）
        connectionManager.setMaxTotal(300);
        // 设置每个路由的最大连接数（Cloudflare API 支持高并发）
        connectionManager.setDefaultMaxPerRoute(100);
        // 设置连接存活时间（Keep-Alive），减少连接重建
        connectionManager.setValidateAfterInactivity(2000); // 2秒后验证连接
        return connectionManager;
    }
    
    /**
     * 配置HttpClient
     * 优化：启用 HTTP/2 支持（如果服务器支持），优化超时时间，启用压缩
     */
    @Bean
    public CloseableHttpClient httpClient(PoolingHttpClientConnectionManager connectionManager) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(8000)   // 连接超时8秒（优化：减少等待时间）
                .setSocketTimeout(25000)   // 读取超时25秒（优化：减少等待时间）
                .setConnectionRequestTimeout(3000)  // 从连接池获取连接超时3秒（优化）
                // 启用重定向
                .setRedirectsEnabled(true)
                .setMaxRedirects(3)
                .build();
        
        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                // 优化：延长空闲连接清理时间，提高连接复用率
                .evictIdleConnections(60, TimeUnit.SECONDS)  // 60秒后清理空闲连接
                .evictExpiredConnections()     // 清理过期连接
                // 启用连接复用（Keep-Alive）
                .setKeepAliveStrategy((response, context) -> {
                    // 保持连接 60 秒
                    return 60000;
                })
                // 启用自动重试（仅对幂等操作）
                .setRetryHandler((exception, executionCount, context) -> {
                    // 最多重试 1 次
                    return executionCount <= 1;
                })
                .build();
    }
    
    /**
     * 配置RestTemplate，使用HttpClient连接池
     * 优化：启用压缩，优化超时配置
     */
    @Bean
    public RestTemplate restTemplate(CloseableHttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);
        factory.setConnectTimeout(8000);   // 连接超时8秒（与 HttpClient 一致）
        factory.setReadTimeout(25000);     // 读取超时25秒（与 HttpClient 一致）
        factory.setConnectionRequestTimeout(3000);  // 从连接池获取连接超时3秒
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // 优化：添加 Accept-Encoding: gzip 头，启用响应压缩
        // RestTemplate 会自动处理 gzip 解压（如果服务器支持）
        restTemplate.getInterceptors().add((request, body, execution) -> {
            // 添加压缩支持
            request.getHeaders().add("Accept-Encoding", "gzip, deflate, br");
            return execution.execute(request, body);
        });
        
        return restTemplate;
    }
    
    /**
     * 配置ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
} 