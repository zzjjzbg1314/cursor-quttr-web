package com.example.cursorquitterweb.util;

import com.example.cursorquitterweb.config.CloudflareD1Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cloudflare D1数据库工具类
 * 优化版本：减少对象创建，提升HTTP查询性能
 *
 * @author davinci
 */
@Slf4j
@Component
public class CloudflareD1Util {

    @Autowired
    private CloudflareD1Config d1Config;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 缓存查询URL，避免重复构建
    private String queryUrl;
    
    // 异步查询线程池（用于并行查询）
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() * 2,
        r -> {
            Thread t = new Thread(r, "d1-async-query");
            t.setDaemon(true);
            return t;
        }
    );

    /**
     * 执行查询操作
     * 优化：减少对象创建，缓存URL，优化JSON解析
     *
     * @param sql    SQL查询语句
     * @param params 查询参数
     * @return 查询结果
     */
    public JsonNode query(String sql, List<Object> params) {
        try {
            String url = getQueryUrl();
            
            // 优化：使用更高效的方式构建请求体，预分配容量
            Map<String, Object> requestBody = new HashMap<>(2);
            requestBody.put("sql", sql);
            requestBody.put("params", params != null ? params : Arrays.asList());

            HttpHeaders headers = buildHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                if (responseNode.has("success") && responseNode.get("success").asBoolean()) {
                    JsonNode resultArray = responseNode.get("result");
                    if (resultArray != null && resultArray.isArray() && resultArray.size() > 0) {
                        return resultArray.get(0).get("results");
                    }
                    return null;
                } else {
                    JsonNode errors = responseNode.get("errors");
                    log.error("D1查询失败: {}", errors != null ? errors : "未知错误");
                    throw new RuntimeException("D1查询失败");
                }
            } else {
                log.error("D1查询HTTP错误: {}", response.getStatusCode());
                throw new RuntimeException("D1查询HTTP错误");
            }
        } catch (Exception e) {
            log.error("D1查询异常", e);
            throw new RuntimeException("D1查询异常", e);
        }
    }

    /**
     * 执行查询并返回单行结果
     * 
     * @param sql SQL 查询语句
     * @param params 查询参数
     * @return 单行结果，如果没有结果返回 null
     */
    public Map<String, Object> queryOne(String sql, Object... params) {
        JsonNode results = query(sql, params != null && params.length > 0 ? Arrays.asList(params) : Arrays.asList());
        if (results != null && results.isArray() && results.size() > 0) {
            return jsonNodeToMap(results.get(0));
        }
        return null;
    }
    
    /**
     * 执行查询并返回所有结果
     * 
     * @param sql SQL 查询语句
     * @param params 查询参数
     * @return 查询结果列表
     */
    public List<Map<String, Object>> queryList(String sql, Object... params) {
        JsonNode results = query(sql, params != null && params.length > 0 ? Arrays.asList(params) : Arrays.asList());
        if (results != null && results.isArray()) {
            List<Map<String, Object>> rows = new ArrayList<>(results.size());
            for (JsonNode row : results) {
                rows.add(jsonNodeToMap(row));
            }
            return rows;
        }
        return new ArrayList<>();
    }
    
    /**
     * 执行查询并返回单个值
     * 
     * @param sql SQL 查询语句
     * @param params 查询参数
     * @return 单个值，如果没有结果返回 null
     */
    public Object querySingleValue(String sql, Object... params) {
        Map<String, Object> row = queryOne(sql, params);
        if (row != null && !row.isEmpty()) {
            return row.values().iterator().next();
        }
        return null;
    }
    
    /**
     * 执行查询并返回整数值
     * 
     * @param sql SQL 查询语句
     * @param params 查询参数
     * @return 整数值，如果没有结果返回 0
     */
    public int queryInt(String sql, Object... params) {
        Object value = querySingleValue(sql, params);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse int value: {}", value);
            return 0;
        }
    }
    
    /**
     * 执行查询并返回长整数值
     * 
     * @param sql SQL 查询语句
     * @param params 查询参数
     * @return 长整数值，如果没有结果返回 0
     */
    public long queryLong(String sql, Object... params) {
        Object value = querySingleValue(sql, params);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse long value: {}", value);
            return 0;
        }
    }
    
    /**
     * 执行查询并返回字符串值
     * 
     * @param sql SQL 查询语句
     * @param params 查询参数
     * @return 字符串值，如果没有结果返回 null
     */
    public String queryString(String sql, Object... params) {
        Object value = querySingleValue(sql, params);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 执行查询并返回 UUID 值
     * 
     * @param sql SQL 查询语句
     * @param params 查询参数
     * @return UUID 值，如果没有结果返回 null
     */
    public UUID queryUUID(String sql, Object... params) {
        String value = queryString(sql, params);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse UUID value: {}", value);
            return null;
        }
    }
    
    /**
     * 执行更新操作（INSERT, UPDATE, DELETE）
     * 
     * @param sql SQL 语句
     * @param params 参数
     * @return 受影响的行数
     */
    public int execute(String sql, Object... params) {
        return executeMutationWithResult(sql, params != null && params.length > 0 ? Arrays.asList(params) : Arrays.asList());
    }
    
    /**
     * 执行插入操作并返回最后插入的行 ID
     * 
     * @param sql SQL INSERT 语句
     * @param params 参数
     * @return 最后插入的行 ID
     */
    public long executeInsert(String sql, Object... params) {
        return executeMutationWithLastRowId(sql, params != null && params.length > 0 ? Arrays.asList(params) : Arrays.asList());
    }

    /**
     * 执行插入操作
     *
     * @param sql    SQL插入语句
     * @param params 插入参数
     * @return 是否成功
     */
    public boolean insert(String sql, List<Object> params) {
        return executeMutation(sql, params);
    }

    /**
     * 执行更新操作
     *
     * @param sql    SQL更新语句
     * @param params 更新参数
     * @return 是否成功
     */
    public boolean update(String sql, List<Object> params) {
        return executeMutation(sql, params);
    }

    /**
     * 执行删除操作
     *
     * @param sql    SQL删除语句
     * @param params 删除参数
     * @return 是否成功
     */
    public boolean delete(String sql, List<Object> params) {
        return executeMutation(sql, params);
    }

    /**
     * 执行变更操作（插入、更新、删除）
     * 优化：减少对象创建，缓存URL，优化JSON解析
     *
     * @param sql    SQL语句
     * @param params 参数
     * @return 是否成功
     */
    private boolean executeMutation(String sql, List<Object> params) {
        try {
            String url = getQueryUrl();
            
            // 优化：使用更高效的方式构建请求体，预分配容量
            Map<String, Object> requestBody = new HashMap<>(2);
            requestBody.put("sql", sql);
            requestBody.put("params", params != null ? params : Arrays.asList());

            HttpHeaders headers = buildHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                if (responseNode.has("success") && responseNode.get("success").asBoolean()) {
                    return true;
                } else {
                    JsonNode errors = responseNode.get("errors");
                    log.error("D1变更操作失败: {}", errors != null ? errors : "未知错误");
                    return false;
                }
            } else {
                log.error("D1变更操作HTTP错误: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("D1变更操作异常", e);
            return false;
        }
    }

    /**
     * 获取查询URL（延迟初始化并缓存）
     * 优化：使用 getApiEndpoint() 方法，避免重复构建URL
     */
    private String getQueryUrl() {
        if (queryUrl == null) {
            queryUrl = d1Config.getApiEndpoint() + "/query";
        }
        return queryUrl;
    }

    /**
     * 执行变更操作并返回受影响行数
     */
    private int executeMutationWithResult(String sql, List<Object> params) {
        try {
            String url = getQueryUrl();
            Map<String, Object> requestBody = new HashMap<>(2);
            requestBody.put("sql", sql);
            requestBody.put("params", params != null ? params : Arrays.asList());

            HttpHeaders headers = buildHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                if (responseNode.has("success") && responseNode.get("success").asBoolean()) {
                    JsonNode resultArray = responseNode.get("result");
                    if (resultArray != null && resultArray.isArray() && resultArray.size() > 0) {
                        JsonNode meta = resultArray.get(0).get("meta");
                        if (meta != null && meta.has("changes")) {
                            return meta.get("changes").asInt();
                        }
                    }
                    return 0;
                } else {
                    JsonNode errors = responseNode.get("errors");
                    log.error("D1变更操作失败: {}", errors != null ? errors : "未知错误");
                    return 0;
                }
            } else {
                log.error("D1变更操作HTTP错误: {}", response.getStatusCode());
                return 0;
            }
        } catch (Exception e) {
            log.error("D1变更操作异常", e);
            return 0;
        }
    }
    
    /**
     * 执行变更操作并返回最后插入的行ID
     */
    private long executeMutationWithLastRowId(String sql, List<Object> params) {
        try {
            String url = getQueryUrl();
            Map<String, Object> requestBody = new HashMap<>(2);
            requestBody.put("sql", sql);
            requestBody.put("params", params != null ? params : Arrays.asList());

            HttpHeaders headers = buildHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                if (responseNode.has("success") && responseNode.get("success").asBoolean()) {
                    JsonNode resultArray = responseNode.get("result");
                    if (resultArray != null && resultArray.isArray() && resultArray.size() > 0) {
                        JsonNode meta = resultArray.get(0).get("meta");
                        if (meta != null && meta.has("last_row_id")) {
                            return meta.get("last_row_id").asLong();
                        }
                    }
                    return 0;
                } else {
                    JsonNode errors = responseNode.get("errors");
                    log.error("D1变更操作失败: {}", errors != null ? errors : "未知错误");
                    return 0;
                }
            } else {
                log.error("D1变更操作HTTP错误: {}", response.getStatusCode());
                return 0;
            }
        } catch (Exception e) {
            log.error("D1变更操作异常", e);
            return 0;
        }
    }
    
    /**
     * 检查记录是否存在
     * 
     * @param tableName 表名
     * @param whereClause WHERE 子句（不包含 WHERE 关键字）
     * @param params WHERE 子句的参数
     * @return 如果记录存在返回 true，否则返回 false
     */
    public boolean exists(String tableName, String whereClause, Object... params) {
        String sql = "SELECT 1 FROM " + tableName + " WHERE " + whereClause + " LIMIT 1";
        Map<String, Object> result = queryOne(sql, params);
        return result != null;
    }
    
    /**
     * 根据 ID 查询单条记录
     * 
     * @param tableName 表名
     * @param idColumn ID 列名
     * @param id ID 值
     * @return 记录，如果不存在返回 null
     */
    public Map<String, Object> findById(String tableName, String idColumn, Object id) {
        String sql = "SELECT * FROM " + tableName + " WHERE " + idColumn + " = ? LIMIT 1";
        return queryOne(sql, id);
    }
    
    /**
     * 插入单条记录
     * 
     * @param tableName 表名
     * @param data 数据映射（列名 -> 值）
     * @return 最后插入的行 ID
     */
    public long insert(String tableName, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        Object[] values = new Object[data.size()];
        
        int index = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (index > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(entry.getKey());
            placeholders.append("?");
            values[index++] = entry.getValue();
        }
        
        String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";
        return executeInsert(sql, values);
    }
    
    /**
     * 更新记录
     * 
     * @param tableName 表名
     * @param data 要更新的数据映射（列名 -> 值）
     * @param whereClause WHERE 子句（不包含 WHERE 关键字）
     * @param whereParams WHERE 子句的参数
     * @return 受影响的行数
     */
    public int update(String tableName, Map<String, Object> data, String whereClause, Object... whereParams) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        StringBuilder setClause = new StringBuilder();
        Object[] values = new Object[data.size() + whereParams.length];
        
        int index = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (index > 0) {
                setClause.append(", ");
            }
            setClause.append(entry.getKey()).append(" = ?");
            values[index++] = entry.getValue();
        }
        
        // 添加 WHERE 参数
        System.arraycopy(whereParams, 0, values, index, whereParams.length);
        
        String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
        return execute(sql, values);
    }
    
    /**
     * 根据 ID 更新记录
     * 
     * @param tableName 表名
     * @param data 要更新的数据映射（列名 -> 值）
     * @param idColumn ID 列名
     * @param id ID 值
     * @return 受影响的行数
     */
    public int updateById(String tableName, Map<String, Object> data, String idColumn, Object id) {
        return update(tableName, data, idColumn + " = ?", id);
    }
    
    /**
     * 删除记录
     * 
     * @param tableName 表名
     * @param whereClause WHERE 子句（不包含 WHERE 关键字）
     * @param params WHERE 子句的参数
     * @return 受影响的行数
     */
    public int delete(String tableName, String whereClause, Object... params) {
        String sql = "DELETE FROM " + tableName + " WHERE " + whereClause;
        return execute(sql, params);
    }
    
    /**
     * 根据 ID 删除记录
     * 
     * @param tableName 表名
     * @param idColumn ID 列名
     * @param id ID 值
     * @return 受影响的行数
     */
    public int deleteById(String tableName, String idColumn, Object id) {
        return delete(tableName, idColumn + " = ?", id);
    }
    
    /**
     * 分页查询
     * 
     * @param sql SQL 查询语句（不包含 LIMIT 和 OFFSET）
     * @param page 页码（从 1 开始）
     * @param pageSize 每页大小
     * @param params 查询参数
     * @return 查询结果列表
     */
    public List<Map<String, Object>> queryPage(String sql, int page, int pageSize, Object... params) {
        int offset = (page - 1) * pageSize;
        String pagedSql = sql + " LIMIT ? OFFSET ?";
        Object[] pagedParams = new Object[params.length + 2];
        System.arraycopy(params, 0, pagedParams, 0, params.length);
        pagedParams[params.length] = pageSize;
        pagedParams[params.length + 1] = offset;
        return queryList(pagedSql, pagedParams);
    }
    
    /**
     * 检查表是否存在
     * 
     * @param tableName 表名
     * @return 如果表存在返回 true，否则返回 false
     */
    public boolean tableExists(String tableName) {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        Map<String, Object> result = queryOne(sql, tableName);
        return result != null;
    }
    
    /**
     * 获取表的行数
     * 
     * @param tableName 表名
     * @return 表的行数
     */
    public long countTable(String tableName) {
        String sql = "SELECT COUNT(*) as count FROM " + tableName;
        return queryLong(sql);
    }
    
    /**
     * 批量执行 SQL 语句
     * 
     * @param sqls SQL 语句列表
     * @return 批量执行结果（简化版本，返回是否成功）
     */
    public boolean batchExecute(List<String> sqls) {
        try {
            String url = getQueryUrl();
            Map<String, Object> requestBody = new HashMap<>(1);
            requestBody.put("sql", String.join("; ", sqls));

            HttpHeaders headers = buildHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                return responseNode.has("success") && responseNode.get("success").asBoolean();
            } else {
                log.error("D1批量操作HTTP错误: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("D1批量操作异常", e);
            return false;
        }
    }
    
    /**
     * 执行事务（批量操作）
     * 
     * @param sqls SQL 语句列表
     * @return 是否全部成功
     */
    public boolean executeTransaction(List<String> sqls) {
        return batchExecute(sqls);
    }
    
    /**
     * 将 JsonNode 转换为 Map
     */
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        Map<String, Object> map = new HashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                map.put(entry.getKey(), parseJsonValue(entry.getValue()));
            });
        }
        return map;
    }
    
    /**
     * 解析 JSON 值为 Java 对象
     */
    private Object parseJsonValue(JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble()) {
            return node.asDouble();
        } else {
            return node.asText();
        }
    }

    /**
     * 异步执行查询并返回单行结果
     * 优化：使用异步方式执行查询，适用于可以并行执行的场景
     * 
     * @param sql SQL 查询语句
     * @param params 查询参数
     * @return CompletableFuture 包装的查询结果
     */
    public CompletableFuture<Map<String, Object>> queryOneAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> queryOne(sql, params), asyncExecutor);
    }
    
    /**
     * 异步执行查询并返回所有结果
     * 优化：使用异步方式执行查询，适用于可以并行执行的场景
     * 
     * @param sql SQL 查询语句
     * @param params 查询参数
     * @return CompletableFuture 包装的查询结果列表
     */
    public CompletableFuture<List<Map<String, Object>>> queryListAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> queryList(sql, params), asyncExecutor);
    }
    
    /**
     * 批量并行查询
     * 优化：并行执行多个查询，显著提升性能
     * 
     * @param queries 查询列表，每个元素包含 SQL 和参数
     * @return 查询结果列表，顺序与输入一致
     */
    public List<Map<String, Object>> queryBatchParallel(List<QueryRequest> queries) {
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (QueryRequest query : queries) {
            futures.add(queryOneAsync(query.getSql(), query.getParams()));
        }
        
        List<Map<String, Object>> results = new ArrayList<>(queries.size());
        for (CompletableFuture<Map<String, Object>> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                log.error("批量并行查询失败", e);
                results.add(null);
            }
        }
        return results;
    }
    
    /**
     * 查询请求封装类
     */
    public static class QueryRequest {
        private final String sql;
        private final Object[] params;
        
        public QueryRequest(String sql, Object... params) {
            this.sql = sql;
            this.params = params;
        }
        
        public String getSql() {
            return sql;
        }
        
        public Object[] getParams() {
            return params;
        }
    }
    
    /**
     * 构建请求头
     * 优化：使用 setBearerAuth 方法，更符合Spring规范，添加压缩支持
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(d1Config.getApiToken());
        // 优化：添加压缩支持（已在 RestTemplate 拦截器中添加，这里作为备用）
        headers.add("Accept-Encoding", "gzip, deflate, br");
        return headers;
    }
}
