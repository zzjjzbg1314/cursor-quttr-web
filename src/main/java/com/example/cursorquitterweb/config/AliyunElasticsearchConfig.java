package com.example.cursorquitterweb.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

/**
 * 阿里云 Elasticsearch Serverless 专用配置类
 */
@Configuration
@Slf4j
@Data
@EnableElasticsearchRepositories(basePackages = "com.example.cursorquitterweb.repository.elasticsearch")
public class AliyunElasticsearchConfig {

    @Value("${elasticsearch.aliyun.endpoint}")
    private String endpoint;

    @Value("${elasticsearch.aliyun.username}")
    private String username;

    @Value("${elasticsearch.aliyun.password}")
    private String password;

    @Value("${elasticsearch.aliyun.protocol:http}")
    private String protocol;

    @Value("${elasticsearch.aliyun.port:9200}")
    private int port;

    @Value("${elasticsearch.aliyun.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${elasticsearch.aliyun.socket-timeout:60000}")
    private int socketTimeout;

    @Value("${elasticsearch.aliyun.max-conn-total:100}")
    private int maxConnTotal;

    @Value("${elasticsearch.aliyun.max-conn-per-route:20}")
    private int maxConnPerRoute;

    /**
     * 创建阿里云 Elasticsearch Serverless 客户端
     * 针对阿里云ES Serverless优化配置
     */
    @Bean
    @Primary
    public RestHighLevelClient elasticsearchClient() {
        try {
            // 构建认证信息
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));

            // 构建 RestClientBuilder
            RestClientBuilder builder = RestClient.builder(
                            new HttpHost(endpoint, port, protocol))
                    .setHttpClientConfigCallback(httpClientBuilder -> {
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                        httpClientBuilder.setMaxConnTotal(maxConnTotal);
                        httpClientBuilder.setMaxConnPerRoute(maxConnPerRoute);
                        return httpClientBuilder;
                    })
                    .setRequestConfigCallback(requestConfigBuilder -> {
                        requestConfigBuilder.setConnectTimeout(connectionTimeout);
                        requestConfigBuilder.setSocketTimeout(socketTimeout);
                        return requestConfigBuilder;
                    });

            // 创建 RestHighLevelClient
            return new RestHighLevelClient(builder);

        } catch (Exception e) {
            log.error("创建 Elasticsearch 客户端失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建 Elasticsearch 客户端失败", e);
        }
    }

    /**
     * 创建 ElasticsearchOperations bean
     */
    @Bean(name = {"elasticsearchOperations", "elasticsearchTemplate"})
    public ElasticsearchOperations elasticsearchOperations() {
        return new ElasticsearchRestTemplate(elasticsearchClient());
    }
}
