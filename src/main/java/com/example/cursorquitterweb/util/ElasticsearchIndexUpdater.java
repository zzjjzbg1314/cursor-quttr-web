package com.example.cursorquitterweb.util;

import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch索引更新工具类
 * 用于在运行时更新现有索引的映射
 */
@Component
public class ElasticsearchIndexUpdater {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchIndexUpdater.class);
    private static final String INDEX_NAME = "chatmessage";

    @Autowired
    private RestHighLevelClient elasticsearchClient;

    /**
     * 更新索引映射，添加avatarUrl字段
     * @return 是否更新成功
     */
    public boolean addAvatarUrlField() {
        try {
            logger.info("开始更新索引映射，添加avatarUrl字段...");

            // 尝试使用更兼容的方法
            // 先检查索引是否存在
            if (!indexExists()) {
                logger.info("索引不存在，无法更新映射");
                return false;
            }

            // 构建映射更新请求
            PutMappingRequest request = new PutMappingRequest(INDEX_NAME);
            
            // 使用更兼容的映射格式
            String mappingJson = "{\"properties\":{\"avatarUrl\":{\"type\":\"keyword\"}}}";
            
            request.source(mappingJson, XContentType.JSON);
            
            // 执行更新请求
            boolean acknowledged = elasticsearchClient.indices().putMapping(request, RequestOptions.DEFAULT).isAcknowledged();
            
            if (acknowledged) {
                logger.info("索引映射更新成功，avatarUrl字段已添加");
                return true;
            } else {
                logger.warn("索引映射更新失败，服务器未确认");
                return false;
            }
            
        } catch (IOException e) {
            logger.error("更新索引映射时发生IO错误: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.error("更新索引映射时发生未知错误: {}", e.getMessage(), e);
            // 记录详细错误信息
            logger.error("详细错误信息: ", e);
            return false;
        }
    }

    /**
     * 检查索引是否存在
     */
    private boolean indexExists() {
        try {
            org.elasticsearch.client.indices.GetIndexRequest request = new org.elasticsearch.client.indices.GetIndexRequest(INDEX_NAME);
            return elasticsearchClient.indices().exists(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            logger.error("检查索引是否存在时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查索引是否存在avatarUrl字段
     * @return 是否存在
     */
    public boolean hasAvatarUrlField() {
        try {
            // 这里可以添加检查逻辑，暂时返回false表示需要更新
            // 实际项目中可以通过查询索引映射来检查
            return false;
        } catch (Exception e) {
            logger.error("检查avatarUrl字段时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 自动更新索引映射（如果需要）
     * @return 是否更新成功
     */
    public boolean autoUpdateIfNeeded() {
        if (hasAvatarUrlField()) {
            logger.info("索引已包含avatarUrl字段，无需更新");
            return true;
        }
        
        logger.info("索引不包含avatarUrl字段，开始自动更新...");
        return addAvatarUrlField();
    }
}
