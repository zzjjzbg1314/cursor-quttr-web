package com.example.cursorquitterweb.util;

import com.example.cursorquitterweb.database.d1.CloudflareD1Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cloudflare D1 数据库工具类
 * 封装常用的 D1 数据库操作
 */
@Component
public class CloudflareD1Util {
    
    private static final Logger logger = LoggerFactory.getLogger(CloudflareD1Util.class);
    
    @Autowired
    private CloudflareD1Client d1Client;
    
    /**
     * 执行查询并返回单行结果
     * 
     * @param sql SQL 查询语句
     * @param params 查询参数
     * @return 单行结果，如果没有结果返回 null
     */
    public Map<String, Object> queryOne(String sql, Object... params) {
        CloudflareD1Client.D1QueryResult result = d1Client.query(sql, params);
        List<Map<String, Object>> rows = result.getRows();
        if (rows != null && !rows.isEmpty()) {
            return rows.get(0);
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
        CloudflareD1Client.D1QueryResult result = d1Client.query(sql, params);
        return result.getRows();
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
            logger.warn("Failed to parse int value: {}", value);
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
            logger.warn("Failed to parse long value: {}", value);
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
            logger.warn("Failed to parse UUID value: {}", value);
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
        CloudflareD1Client.D1ExecuteResult result = d1Client.execute(sql, params);
        return result.getChanges();
    }
    
    /**
     * 执行插入操作并返回最后插入的行 ID
     * 
     * @param sql SQL INSERT 语句
     * @param params 参数
     * @return 最后插入的行 ID
     */
    public long executeInsert(String sql, Object... params) {
        CloudflareD1Client.D1ExecuteResult result = d1Client.execute(sql, params);
        return result.getLastRowId();
    }
    
    /**
     * 批量执行 SQL 语句
     * 
     * @param sqls SQL 语句列表
     * @return 批量执行结果
     */
    public CloudflareD1Client.D1BatchResult batchExecute(List<String> sqls) {
        return d1Client.batch(sqls);
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
     * 执行事务（批量操作）
     * 
     * @param sqls SQL 语句列表
     * @return 是否全部成功
     */
    public boolean executeTransaction(List<String> sqls) {
        try {
            CloudflareD1Client.D1BatchResult result = batchExecute(sqls);
            return result != null && result.getResults() != null;
        } catch (Exception e) {
            logger.error("Transaction failed", e);
            return false;
        }
    }
}
