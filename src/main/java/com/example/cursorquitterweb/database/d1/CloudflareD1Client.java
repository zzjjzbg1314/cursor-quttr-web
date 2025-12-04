package com.example.cursorquitterweb.database.d1;

import com.example.cursorquitterweb.config.CloudflareD1Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloudflare D1 HTTP API 客户端
 * 用于通过 REST API 访问 Cloudflare D1 数据库
 */
@Component
public class CloudflareD1Client {
    
    private static final Logger logger = LoggerFactory.getLogger(CloudflareD1Client.class);
    
    @Autowired
    private CloudflareD1Config d1Config;
    
    @Autowired
    private RestTemplate restTemplate;
    
    private final ObjectMapper objectMapper;
    
    public CloudflareD1Client() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 执行 SQL 查询（SELECT）
     */
    public D1QueryResult query(String sql, Object... params) {
        try {
            String url = d1Config.getApiEndpoint() + "/query";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sql", sql);
            if (params != null && params.length > 0) {
                List<Object> paramList = new ArrayList<>();
                for (Object param : params) {
                    paramList.add(param);
                }
                requestBody.put("params", paramList);
            }
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            long queryStartTime = System.currentTimeMillis();
            logger.debug("Executing D1 query: {}", sql);
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
            long queryDuration = System.currentTimeMillis() - queryStartTime;
            
            if (queryDuration > 1000) {
                logger.warn("⚠️ D1查询耗时较长: {}ms | SQL: {}", queryDuration, sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
            } else {
                logger.debug("D1查询耗时: {}ms", queryDuration);
            }
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return parseQueryResult(response.getBody());
            } else {
                // 检查是否是表已存在的错误（对于 CREATE TABLE 语句）
                if (response.getStatusCode() == HttpStatus.BAD_REQUEST && isTableAlreadyExistsError(response.getBody(), sql)) {
                    logger.debug("Table already exists, ignoring error for: {}", sql);
                    return new D1QueryResult(); // 返回空结果
                }
                throw new RuntimeException("D1 API error: " + response.getStatusCode() + " - " + response.getBody());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 处理 HTTP 错误响应
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST && isTableAlreadyExistsError(e.getResponseBodyAsString(), sql)) {
                logger.debug("Table already exists, ignoring error for: {}", sql);
                return new D1QueryResult(); // 返回空结果
            }
            logger.error("Error executing D1 query: {}", sql, e);
            throw new RuntimeException("Failed to execute D1 query", e);
        } catch (Exception e) {
            logger.error("Error executing D1 query: {}", sql, e);
            throw new RuntimeException("Failed to execute D1 query", e);
        }
    }
    
    /**
     * 执行 SQL 语句（INSERT, UPDATE, DELETE, CREATE TABLE 等）
     * 注意：D1 API 使用 /query 端点执行所有 SQL 语句
     */
    public D1ExecuteResult execute(String sql, Object... params) {
        try {
            // D1 API 使用 /query 端点执行所有 SQL 语句
            String url = d1Config.getApiEndpoint() + "/query";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sql", sql);
            if (params != null && params.length > 0) {
                List<Object> paramList = new ArrayList<>();
                for (Object param : params) {
                    paramList.add(param);
                }
                requestBody.put("params", paramList);
            }
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            long executeStartTime = System.currentTimeMillis();
            logger.debug("Executing D1 statement: {}", sql);
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
            long executeDuration = System.currentTimeMillis() - executeStartTime;
            
            if (executeDuration > 1000) {
                logger.warn("⚠️ D1执行耗时较长: {}ms | SQL: {}", executeDuration, sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
            } else {
                logger.debug("D1执行耗时: {}ms", executeDuration);
            }
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return parseExecuteResultFromQuery(response.getBody());
            } else {
                // 检查是否是表已存在的错误（对于 CREATE TABLE 语句）
                if (response.getStatusCode() == HttpStatus.BAD_REQUEST && isTableAlreadyExistsError(response.getBody(), sql)) {
                    logger.debug("Table already exists, ignoring error for: {}", sql);
                    return new D1ExecuteResult(); // 返回空结果
                }
                throw new RuntimeException("D1 API error: " + response.getStatusCode() + " - " + response.getBody());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 处理 HTTP 错误响应
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST && isTableAlreadyExistsError(e.getResponseBodyAsString(), sql)) {
                logger.debug("Table already exists, ignoring error for: {}", sql);
                return new D1ExecuteResult(); // 返回空结果
            }
            logger.error("Error executing D1 statement: {}", sql, e);
            throw new RuntimeException("Failed to execute D1 statement", e);
        } catch (Exception e) {
            logger.error("Error executing D1 statement: {}", sql, e);
            throw new RuntimeException("Failed to execute D1 statement", e);
        }
    }
    
    /**
     * 批量执行 SQL 语句
     * 注意：D1 API 使用 /query 端点执行批量操作
     */
    public D1BatchResult batch(List<String> sqls) {
        try {
            // D1 API 使用 /query 端点执行批量操作
            String url = d1Config.getApiEndpoint() + "/query";
            
            List<Map<String, Object>> statements = new ArrayList<>();
            for (String sql : sqls) {
                Map<String, Object> statement = new HashMap<>();
                statement.put("sql", sql);
                statements.add(statement);
            }
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sql", String.join("; ", sqls)); // 使用分号连接多个 SQL 语句
            // 注意：D1 API 可能不支持批量参数，这里简化处理
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            logger.debug("Executing D1 batch: {} statements", sqls.size());
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return parseBatchResultFromQuery(response.getBody());
            } else {
                throw new RuntimeException("D1 API error: " + response.getStatusCode() + " - " + response.getBody());
            }
        } catch (Exception e) {
            logger.error("Error executing D1 batch", e);
            throw new RuntimeException("Failed to execute D1 batch", e);
        }
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(d1Config.getApiToken());
        return headers;
    }
    
    private D1QueryResult parseQueryResult(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode resultArray = root.get("result");
        
        D1QueryResult queryResult = new D1QueryResult();
        if (resultArray != null && resultArray.isArray() && resultArray.size() > 0) {
            JsonNode firstResult = resultArray.get(0);
            // D1 API 返回格式：result[0].results 包含查询结果
            if (firstResult.has("results") && firstResult.get("results").isArray()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (JsonNode row : firstResult.get("results")) {
                    Map<String, Object> rowMap = new HashMap<>();
                    row.fields().forEachRemaining(entry -> {
                        rowMap.put(entry.getKey(), parseValue(entry.getValue()));
                    });
                    rows.add(rowMap);
                }
                queryResult.setRows(rows);
            }
        }
        
        return queryResult;
    }
    
    /**
     * 从 /query 端点响应中解析执行结果
     * D1 API 的 /query 端点返回格式：
     * {
     *   "result": [{
     *     "results": [...],  // 查询结果（如果有）
     *     "success": true,
     *     "meta": {
     *       "changes": 0,
     *       "last_row_id": 0,
     *       ...
     *     }
     *   }]
     * }
     */
    private D1ExecuteResult parseExecuteResultFromQuery(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode resultArray = root.get("result");
        
        D1ExecuteResult executeResult = new D1ExecuteResult();
        if (resultArray != null && resultArray.isArray() && resultArray.size() > 0) {
            JsonNode firstResult = resultArray.get(0);
            if (firstResult.has("meta")) {
                JsonNode meta = firstResult.get("meta");
                if (meta.has("changes")) {
                    executeResult.setChanges(meta.get("changes").asInt());
                }
                if (meta.has("last_row_id")) {
                    executeResult.setLastRowId(meta.get("last_row_id").asLong());
                }
            }
        }
        
        return executeResult;
    }
    
    /**
     * 从 /query 端点响应中解析批量执行结果
     */
    private D1BatchResult parseBatchResultFromQuery(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode resultArray = root.get("result");
        
        D1BatchResult batchResult = new D1BatchResult();
        if (resultArray != null && resultArray.isArray()) {
            List<D1ExecuteResult> results = new ArrayList<>();
            for (JsonNode item : resultArray) {
                D1ExecuteResult executeResult = new D1ExecuteResult();
                if (item.has("meta")) {
                    JsonNode meta = item.get("meta");
                    if (meta.has("changes")) {
                        executeResult.setChanges(meta.get("changes").asInt());
                    }
                    if (meta.has("last_row_id")) {
                        executeResult.setLastRowId(meta.get("last_row_id").asLong());
                    }
                }
                results.add(executeResult);
            }
            batchResult.setResults(results);
        }
        
        return batchResult;
    }
    
    private Object parseValue(JsonNode node) {
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
     * 检查是否是表已存在的错误
     */
    private boolean isTableAlreadyExistsError(String responseBody, String sql) {
        if (responseBody == null || sql == null) {
            return false;
        }
        
        // 检查 SQL 是否是 CREATE TABLE 语句
        String sqlUpper = sql.trim().toUpperCase();
        if (!sqlUpper.startsWith("CREATE TABLE")) {
            return false;
        }
        
        // 检查响应中是否包含表已存在的错误
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("errors") && root.get("errors").isArray()) {
                for (JsonNode error : root.get("errors")) {
                    if (error.has("message")) {
                        String message = error.get("message").asText();
                        if (message != null && (message.contains("already exists") || 
                            message.contains("SQLITE_ERROR") && message.contains("already exists"))) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to parse error response", e);
        }
        
        return false;
    }
    
    /**
     * D1 查询结果
     */
    public static class D1QueryResult {
        private List<Map<String, Object>> rows = new ArrayList<>();
        
        public List<Map<String, Object>> getRows() {
            return rows;
        }
        
        public void setRows(List<Map<String, Object>> rows) {
            this.rows = rows;
        }
    }
    
    /**
     * D1 执行结果
     */
    public static class D1ExecuteResult {
        private int changes;
        private long lastRowId;
        
        public int getChanges() {
            return changes;
        }
        
        public void setChanges(int changes) {
            this.changes = changes;
        }
        
        public long getLastRowId() {
            return lastRowId;
        }
        
        public void setLastRowId(long lastRowId) {
            this.lastRowId = lastRowId;
        }
    }
    
    /**
     * D1 批量执行结果
     */
    public static class D1BatchResult {
        private List<D1ExecuteResult> results = new ArrayList<>();
        
        public List<D1ExecuteResult> getResults() {
            return results;
        }
        
        public void setResults(List<D1ExecuteResult> results) {
            this.results = results;
        }
    }
}

