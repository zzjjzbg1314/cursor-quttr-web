package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch控制器 - 基础健康检查
 */
@RestController
@RequestMapping("/api/elasticsearch")
public class ElasticsearchController {

    @Autowired
    private RestHighLevelClient elasticsearchClient;

    /**
     * 检查Elasticsearch连接状态
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> checkHealth() {
        Map<String, Object> healthInfo = new HashMap<>();
        
        try {
            // 检查客户端是否可用
            boolean isAvailable = elasticsearchClient.ping(RequestOptions.DEFAULT);
            healthInfo.put("status", isAvailable ? "UP" : "DOWN");
            healthInfo.put("message", isAvailable ? "Elasticsearch连接正常" : "Elasticsearch连接异常");
            healthInfo.put("timestamp", System.currentTimeMillis());
            
            return ApiResponse.success(healthInfo);
        } catch (IOException e) {
            healthInfo.put("status", "DOWN");
            healthInfo.put("message", "Elasticsearch连接失败: " + e.getMessage());
            healthInfo.put("timestamp", System.currentTimeMillis());
            return ApiResponse.error("连接失败");
        }
    }

    /**
     * 获取Elasticsearch基本信息
     */
    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            // 简单的连接信息
            info.put("message", "Elasticsearch客户端已配置");
            info.put("timestamp", System.currentTimeMillis());
            
            return ApiResponse.success(info);
        } catch (Exception e) {
            return ApiResponse.error("获取信息失败: " + e.getMessage());
        }
    }
}
