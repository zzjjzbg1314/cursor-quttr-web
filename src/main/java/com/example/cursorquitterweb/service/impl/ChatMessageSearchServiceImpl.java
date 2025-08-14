package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.elasticsearch.ChatMessageDocument;
import com.example.cursorquitterweb.service.ChatMessageSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.common.settings.Settings;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Collections;

/**
 * 群聊消息搜索服务实现类 - 直接调用阿里云ES API
 * 
 * 状态码处理说明：
 * - HTTP 200 OK: 操作成功
 * - HTTP 201 Created: 资源创建成功（兼容此状态码，捕获可能抛出的异常）
 * - HTTP 2xx: 其他成功状态码
 * 
 * 所有 2xx 状态码都被视为成功，确保与阿里云ES的兼容性
 * 
 * 异常处理策略：
 * - 201状态码虽然表示成功，但可能抛出异常，需要特殊处理
 * - 即使出现异常，201状态码仍然被视为成功操作
 * - 批量操作中特别关注201状态码的响应处理
 * 
 * 排序策略：
 * - getLatestMessages方法：按时间倒序查询获取最新数据，然后反转列表
 * - 确保返回的是最新的limit条数据，且按时间正序排列（旧消息在前，新消息在后）
 * - 这样设计便于前端按时间顺序加载和显示消息
 * - 其他查询方法保持原有的排序逻辑
 */
@Service
public class ChatMessageSearchServiceImpl implements ChatMessageSearchService {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageSearchServiceImpl.class);
    private static final String INDEX_NAME = "chatmessage";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 检查HTTP状态码是否表示成功
     * 兼容 HTTP 200 OK 和 201 Created 状态码
     * 
     * @param statusCode HTTP状态码
     * @return true表示成功，false表示失败
     */
    private boolean isSuccessStatusCode(int statusCode) {
        return statusCode == 200 || statusCode == 201 || (statusCode >= 200 && statusCode < 300);
    }
    
    /**
     * 获取状态码描述（用于日志记录）
     * 
     * @param statusCode HTTP状态码
     * @return 状态码描述
     */
    private String getStatusCodeDescription(int statusCode) {
        switch (statusCode) {
            case 200: return "200 OK";
            case 201: return "201 Created";
            default: return String.valueOf(statusCode);
        }
    }
    
    /**
     * 安全处理成功状态码响应，捕获201状态码可能抛出的异常
     * 
     * @param statusCode HTTP状态码
     * @param operation 操作名称（用于日志）
     * @param id 操作对象ID（用于日志）
     * @param response 响应对象
     * @return 是否处理成功
     */
    private boolean safelyHandleSuccessResponse(int statusCode, String operation, String id, Object response) {
        try {
            if (statusCode == 201) {
                logger.debug("{}成功: {} (资源已创建), ID={}", operation, getStatusCodeDescription(statusCode), id);
            } else {
                logger.debug("{}成功: {}, ID={}", operation, getStatusCodeDescription(statusCode), id);
            }
            return true;
        } catch (Exception e) {
            logger.warn("处理{}响应时出现异常，状态码: {}, 错误: {}, ID={}", 
                operation, getStatusCodeDescription(statusCode), e.getMessage(), id);
            // 即使出现异常，201状态码仍然表示成功
            return true;
        }
    }

    @Autowired
    private RestHighLevelClient elasticsearchClient;

    @Override
    public ChatMessageDocument indexMessage(ChatMessageDocument message) {
        try {
            logger.debug("开始索引消息到阿里云ES: {}", message.getId());
            
            // 确保消息有ID
            if (message.getId() == null) {
                message.setId(UUID.randomUUID().toString());
            }
            
            // 转换为Map
            Map<String, Object> messageMap = convertToMap(message);
            
            // 创建索引请求
            IndexRequest request = new IndexRequest(INDEX_NAME)
                .id(message.getId())
                .source(messageMap);
            
            // 执行索引请求
            IndexResponse response = elasticsearchClient.index(request, RequestOptions.DEFAULT);
            
            int statusCode = response.status().getStatus();
            if (isSuccessStatusCode(statusCode)) {
                // 安全处理成功响应，捕获201状态码可能抛出的异常
                safelyHandleSuccessResponse(statusCode, "消息索引到阿里云ES", response.getId(), response);
                message.setId(response.getId());
                return message;
            } else {
                logger.error("索引失败，状态码: {}", getStatusCodeDescription(statusCode));
                throw new RuntimeException("索引失败，状态码: " + statusCode);
            }
            
        } catch (Exception e) {
            logger.info("阿里云ES索引失败: {}, 错误: {}", message.getId(), e.getMessage(), e);
            return message;
        }
    }

    @Override
    public boolean saveMessage(ChatMessageDocument message) {
        try {
            indexMessage(message);
            return true;
        } catch (Exception e) {
            logger.error("保存消息失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Iterable<ChatMessageDocument> indexMessages(List<ChatMessageDocument> messages) {
        try {
            logger.debug("开始批量索引消息到阿里云ES，数量: {}", messages.size());
            
            BulkRequest bulkRequest = new BulkRequest();
            
            for (ChatMessageDocument message : messages) {
                // 确保每个消息都有ID
                if (message.getId() == null) {
                    message.setId(UUID.randomUUID().toString());
                }
                
                Map<String, Object> messageMap = convertToMap(message);
                IndexRequest indexRequest = new IndexRequest(INDEX_NAME)
                    .id(message.getId())
                    .source(messageMap);
                
                bulkRequest.add(indexRequest);
            }
            
            BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
            
            // 检查批量操作结果，特别关注201状态码
            if (bulkResponse.hasFailures()) {
                logger.error("批量索引存在失败项: {}", bulkResponse.buildFailureMessage());
                throw new RuntimeException("批量索引存在失败项");
            }
            
            // 检查每个操作的响应状态
            for (BulkItemResponse item : bulkResponse.getItems()) {
                if (item.isFailed()) {
                    logger.error("批量索引中单个项目失败: {}, 错误: {}", item.getId(), item.getFailureMessage());
                } else {
                    int statusCode = item.status().getStatus();
                    if (statusCode == 201) {
                        logger.debug("批量索引项目成功创建: {}, 状态码: {}", item.getId(), getStatusCodeDescription(statusCode));
                    }
                }
            }
            
            logger.debug("批量消息索引到阿里云ES成功，数量: {}", messages.size());
            return messages;
            
        } catch (Exception e) {
            logger.error("批量消息索引到阿里云ES失败，数量: {}, 错误: {}", messages.size(), e.getMessage(), e);
            throw new DataAccessResourceFailureException("阿里云ES批量索引失败", e);
        }
    }

    @Override
    public boolean saveMessages(List<ChatMessageDocument> messages) {
        try {
            indexMessages(messages);
            return true;
        } catch (Exception e) {
            logger.error("批量保存消息失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public ChatMessageDocument findMessageById(String id) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery("_id", id));
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits();
            
            if (hits.getTotalHits().value > 0) {
                SearchHit hit = hits.getAt(0);
                return convertToDocument(hit);
            }
            
            return null;
        } catch (Exception e) {
            logger.error("根据ID查找消息失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<ChatMessageDocument> findMessagesByType(String msgType) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery("msgType", msgType));
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return convertSearchResponseToDocuments(response);
        } catch (Exception e) {
            logger.error("根据类型查找消息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public Page<ChatMessageDocument> findMessagesByType(String msgType, Pageable pageable) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery("msgType", msgType));
            searchSourceBuilder.from((int) pageable.getOffset());
            searchSourceBuilder.size(pageable.getPageSize());
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            List<ChatMessageDocument> documents = convertSearchResponseToDocuments(response);
            
            return new PageImpl<>(documents, pageable, response.getHits().getTotalHits().value);
        } catch (Exception e) {
            logger.error("根据类型分页查找消息失败: {}", e.getMessage(), e);
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
    }

    @Override
    public List<ChatMessageDocument> findMessagesByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("createAt")
                .gte(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                .lte(endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            
            searchSourceBuilder.query(rangeQuery);
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return convertSearchResponseToDocuments(response);
        } catch (Exception e) {
            logger.error("根据时间范围查找消息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public Page<ChatMessageDocument> findMessagesByTimeRange(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("createAt")
                .gte(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                .lte(endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            
            searchSourceBuilder.query(rangeQuery);
            searchSourceBuilder.from((int) pageable.getOffset());
            searchSourceBuilder.size(pageable.getPageSize());
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            List<ChatMessageDocument> documents = convertSearchResponseToDocuments(response);
            
            return new PageImpl<>(documents, pageable, response.getHits().getTotalHits().value);
        } catch (Exception e) {
            logger.error("根据时间范围分页查找消息失败: {}", e.getMessage(), e);
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
    }

    @Override
    public List<ChatMessageDocument> findMessagesByTypeAndTimeRange(String msgType, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("msgType", msgType))
                .must(QueryBuilders.rangeQuery("createAt")
                    .gte(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                    .lte(endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));
            
            searchSourceBuilder.query(boolQuery);
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return convertSearchResponseToDocuments(response);
        } catch (Exception e) {
            logger.error("根据类型和时间范围查找消息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public Page<ChatMessageDocument> findMessagesByTypeAndTimeRange(String msgType, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("msgType", msgType))
                .must(QueryBuilders.rangeQuery("createAt")
                    .gte(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                    .lte(endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));
            
            searchSourceBuilder.query(boolQuery);
            searchSourceBuilder.from((int) pageable.getOffset());
            searchSourceBuilder.size(pageable.getPageSize());
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            List<ChatMessageDocument> documents = convertSearchResponseToDocuments(response);
            
            return new PageImpl<>(documents, pageable, response.getHits().getTotalHits().value);
        } catch (Exception e) {
            logger.error("根据类型和时间范围分页查找消息失败: {}", e.getMessage(), e);
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
    }

    @Override
    public List<ChatMessageDocument> searchMessagesByContent(String content) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchQuery("content", content));
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return convertSearchResponseToDocuments(response);
        } catch (Exception e) {
            logger.error("根据内容搜索消息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public Page<ChatMessageDocument> searchMessagesByContent(String content, Pageable pageable) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchQuery("content", content));
            searchSourceBuilder.from((int) pageable.getOffset());
            searchSourceBuilder.size(pageable.getPageSize());
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            List<ChatMessageDocument> documents = convertSearchResponseToDocuments(response);
            
            return new PageImpl<>(documents, pageable, response.getHits().getTotalHits().value);
        } catch (Exception e) {
            logger.error("根据内容分页搜索消息失败: {}", e.getMessage(), e);
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
    }

    @Override
    public List<ChatMessageDocument> findMessagesByNickName(String nickName) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchQuery("nickName", nickName));
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return convertSearchResponseToDocuments(response);
        } catch (Exception e) {
            logger.error("根据昵称查找消息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<ChatMessageDocument> findMessagesByUserStage(String userStage) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery("userStage", userStage));
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return convertSearchResponseToDocuments(response);
        } catch (Exception e) {
            logger.error("根据用户阶段查找消息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<ChatMessageDocument> searchMessages(String msgType, String content, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("msgType", msgType))
                .must(QueryBuilders.matchQuery("content", content))
                .must(QueryBuilders.rangeQuery("createAt")
                    .gte(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                    .lte(endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));
            
            searchSourceBuilder.query(boolQuery);
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return convertSearchResponseToDocuments(response);
        } catch (Exception e) {
            logger.error("复合搜索消息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public Page<ChatMessageDocument> searchMessages(String msgType, String content, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("msgType", msgType))
                .must(QueryBuilders.matchQuery("content", content))
                .must(QueryBuilders.rangeQuery("createAt")
                    .gte(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                    .lte(endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));
            
            searchSourceBuilder.query(boolQuery);
            searchSourceBuilder.from((int) pageable.getOffset());
            searchSourceBuilder.size(pageable.getPageSize());
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            List<ChatMessageDocument> documents = convertSearchResponseToDocuments(response);
            
            return new PageImpl<>(documents, pageable, response.getHits().getTotalHits().value);
        } catch (Exception e) {
            logger.error("复合分页搜索消息失败: {}", e.getMessage(), e);
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
    }

    @Override
    public List<ChatMessageDocument> getLatestMessages(int limit) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            // 按时间倒序查询，获取最新的limit条数据
            searchSourceBuilder.sort("createAt", SortOrder.DESC);
            searchSourceBuilder.size(limit);
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            List<ChatMessageDocument> documents = convertSearchResponseToDocuments(response);
            
            // 反转列表，让最新的消息显示在最后，符合前端加载的预期
            // 这样前端可以按时间顺序显示：旧消息在上，新消息在下
            Collections.reverse(documents);
            
            return documents;
        } catch (Exception e) {
            logger.error("获取最新消息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<ChatMessageDocument> getLatestMessagesByType(String msgType, int limit) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery("msgType", msgType));
            // 按时间倒序查询，获取最新的limit条数据
            searchSourceBuilder.sort("createAt", SortOrder.DESC);
            searchSourceBuilder.size(limit);
            searchRequest.source(searchSourceBuilder);
            
            SearchResponse response = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
            List<ChatMessageDocument> documents = convertSearchResponseToDocuments(response);
            
            // 反转列表，让最新的消息显示在最后，符合前端加载的预期
            // 这样前端可以按时间顺序显示：旧消息在上，新消息在下
            Collections.reverse(documents);
            
            return documents;
        } catch (Exception e) {
            logger.error("根据类型获取最新消息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public ChatMessageDocument updateMessage(ChatMessageDocument message) {
        if (message.getId() == null) {
            return null;
        }
        
        try {
            Map<String, Object> messageMap = convertToMap(message);
            
            UpdateRequest updateRequest = new UpdateRequest(INDEX_NAME, message.getId())
                .doc(messageMap);
            
            UpdateResponse response = elasticsearchClient.update(updateRequest, RequestOptions.DEFAULT);
            
            int statusCode = response.status().getStatus();
            if (isSuccessStatusCode(statusCode)) {
                // 安全处理成功响应，捕获201状态码可能抛出的异常
                safelyHandleSuccessResponse(statusCode, "消息更新", message.getId(), response);
                return message;
            } else {
                logger.error("更新失败，状态码: {}", getStatusCodeDescription(statusCode));
                throw new RuntimeException("更新失败，状态码: " + statusCode);
            }
        } catch (Exception e) {
            logger.error("更新消息失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void deleteMessage(String id) {
        try {
            DeleteRequest deleteRequest = new DeleteRequest(INDEX_NAME, id);
            DeleteResponse response = elasticsearchClient.delete(deleteRequest, RequestOptions.DEFAULT);
            
            int statusCode = response.status().getStatus();
            if (isSuccessStatusCode(statusCode)) {
                // 安全处理成功响应，捕获201状态码可能抛出的异常
                safelyHandleSuccessResponse(statusCode, "删除消息", id, response);
            } else {
                logger.warn("删除消息失败，状态码: {}", getStatusCodeDescription(statusCode));
            }
        } catch (Exception e) {
            logger.error("删除消息失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteMessagesByType(String msgType) {
        try {
            // 先查询所有符合条件的消息
            List<ChatMessageDocument> messages = findMessagesByType(msgType);
            
            // 批量删除
            BulkRequest bulkRequest = new BulkRequest();
            for (ChatMessageDocument message : messages) {
                DeleteRequest deleteRequest = new DeleteRequest(INDEX_NAME, message.getId());
                bulkRequest.add(deleteRequest);
            }
            
            if (bulkRequest.numberOfActions() > 0) {
                BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                if (bulkResponse.hasFailures()) {
                    logger.error("批量删除存在失败项: {}", bulkResponse.buildFailureMessage());
                } else {
                    // 检查每个删除操作的响应状态，特别关注201状态码
                    for (BulkItemResponse item : bulkResponse.getItems()) {
                        if (item.isFailed()) {
                            logger.error("批量删除中单个项目失败: {}, 错误: {}", item.getId(), item.getFailureMessage());
                        } else {
                            int statusCode = item.status().getStatus();
                            if (statusCode == 201) {
                                logger.debug("批量删除项目成功: {}, 状态码: {}", item.getId(), getStatusCodeDescription(statusCode));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("根据类型删除消息失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteMessagesByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            // 先查询所有符合条件的消息
            List<ChatMessageDocument> messages = findMessagesByTimeRange(startTime, endTime);
            
            // 批量删除
            BulkRequest bulkRequest = new BulkRequest();
            for (ChatMessageDocument message : messages) {
                DeleteRequest deleteRequest = new DeleteRequest(INDEX_NAME, message.getId());
                bulkRequest.add(deleteRequest);
            }
            
            if (bulkRequest.numberOfActions() > 0) {
                BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                if (bulkResponse.hasFailures()) {
                    logger.error("批量删除存在失败项: {}", bulkResponse.buildFailureMessage());
                } else {
                    // 检查每个删除操作的响应状态，特别关注201状态码
                    for (BulkItemResponse item : bulkResponse.getItems()) {
                        if (item.isFailed()) {
                            logger.error("批量删除中单个项目失败: {}, 错误: {}", item.getId(), item.getFailureMessage());
                        } else {
                            int statusCode = item.status().getStatus();
                            if (statusCode == 201) {
                                logger.debug("批量删除项目成功: {}, 状态码: {}", item.getId(), getStatusCodeDescription(statusCode));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("根据时间范围删除消息失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean indexExists() {
        try {
            GetIndexRequest request = new GetIndexRequest(INDEX_NAME);
            return elasticsearchClient.indices().exists(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            logger.error("检查索引是否存在失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean createIndex() {
        try {
            CreateIndexRequest request = new CreateIndexRequest(INDEX_NAME);
            
            // 设置索引设置
            request.settings(Settings.builder()
                .put("index.number_of_shards", 1)
                .put("index.number_of_replicas", 0)
                .put("index.refresh_interval", "1s"));
            
            // 设置映射
            Map<String, Object> properties = new HashMap<>();
            
            Map<String, Object> nickNameField = new HashMap<>();
            nickNameField.put("type", "keyword");
            properties.put("nickName", nickNameField);
            
            Map<String, Object> userStageField = new HashMap<>();
            userStageField.put("type", "keyword");
            properties.put("userStage", userStageField);
            
            Map<String, Object> contentField = new HashMap<>();
            contentField.put("type", "text");
            contentField.put("analyzer", "ik_max_word");
            contentField.put("search_analyzer", "ik_smart");
            properties.put("content", contentField);
            
            Map<String, Object> msgTypeField = new HashMap<>();
            msgTypeField.put("type", "keyword");
            properties.put("msgType", msgTypeField);
            
            Map<String, Object> createAtField = new HashMap<>();
            createAtField.put("type", "date");
            createAtField.put("format", "yyyy-MM-dd'T'HH:mm:ss");
            properties.put("createAt", createAtField);
            
            Map<String, Object> mappings = new HashMap<>();
            mappings.put("properties", properties);
            
            request.mapping(mappings);
            
            CreateIndexResponse response = elasticsearchClient.indices().create(request, RequestOptions.DEFAULT);
            
            if (response.isAcknowledged()) {
                logger.info("索引创建成功: {}", INDEX_NAME);
                return true;
            } else {
                logger.warn("索引创建失败: {}", INDEX_NAME);
                return false;
            }
        } catch (IOException e) {
            logger.error("创建索引失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean deleteIndex() {
        // 删除索引功能暂时禁用
        return false;
    }

    /**
     * 将ChatMessageDocument转换为Map，适配阿里云ES
     */
    private Map<String, Object> convertToMap(ChatMessageDocument message) {
        Map<String, Object> map = new HashMap<>();
        map.put("nickName", message.getNickName());
        map.put("userStage", message.getUserStage());
        map.put("content", message.getContent());
        map.put("msgType", message.getMsgType());
        
        // 修复日期格式，确保与阿里云ES的date格式完全兼容
        if (message.getCreateAt() != null) {
            // 使用DateTimeFormatter格式化，确保无毫秒
            String formattedDate = message.getCreateAt().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            );
            map.put("createAt", formattedDate);
        } else {
            // 如果没有时间，使用当前时间并格式化
            String formattedDate = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            );
            map.put("createAt", formattedDate);
        }
        
        return map;
    }

    /**
     * 将SearchHit转换为ChatMessageDocument
     */
    private ChatMessageDocument convertToDocument(SearchHit hit) {
        try {
            Map<String, Object> source = hit.getSourceAsMap();
            ChatMessageDocument document = new ChatMessageDocument();
            
            document.setId(hit.getId());
            document.setNickName((String) source.get("nickName"));
            document.setUserStage((String) source.get("userStage"));
            document.setContent((String) source.get("content"));
            document.setMsgType((String) source.get("msgType"));
            
            // 解析日期
            String createAtStr = (String) source.get("createAt");
            if (createAtStr != null) {
                try {
                    LocalDateTime createAt = LocalDateTime.parse(createAtStr, 
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                    document.setCreateAt(createAt);
                } catch (Exception e) {
                    logger.warn("解析日期失败: {}", createAtStr);
                }
            }
            
            return document;
        } catch (Exception e) {
            logger.error("转换SearchHit失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将SearchResponse转换为ChatMessageDocument列表
     */
    private List<ChatMessageDocument> convertSearchResponseToDocuments(SearchResponse response) {
        List<ChatMessageDocument> documents = new ArrayList<>();
        
        for (SearchHit hit : response.getHits().getHits()) {
            ChatMessageDocument document = convertToDocument(hit);
            if (document != null) {
                documents.add(document);
            }
        }
        
        return documents;
    }
}
