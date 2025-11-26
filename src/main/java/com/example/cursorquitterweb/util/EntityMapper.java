package com.example.cursorquitterweb.util;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity 和 Map 之间的转换工具类
 * 用于将数据库查询结果（Map）转换为 Entity，或将 Entity 转换为数据库插入/更新用的 Map
 */
public class EntityMapper {
    
    /**
     * 从 Map 中获取 UUID 值
     */
    public static UUID getUUID(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID) {
            return (UUID) value;
        }
        if (value instanceof String) {
            try {
                return UUID.fromString((String) value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * 从 Map 中获取字符串值
     */
    public static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 从 Map 中获取 OffsetDateTime 值
     */
    public static OffsetDateTime getOffsetDateTime(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime) {
            return (OffsetDateTime) value;
        }
        if (value instanceof String) {
            try {
                return OffsetDateTime.parse((String) value);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * 从 Map 中获取整数值
     */
    public static Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * 从 Map 中获取长整数值
     */
    public static Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * 从 Map 中获取布尔值
     */
    public static Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return null;
    }
    
    /**
     * 将 UUID 转换为字符串（用于数据库存储）
     */
    public static String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }
    
    /**
     * 将 OffsetDateTime 转换为字符串（用于数据库存储）
     */
    public static String offsetDateTimeToString(OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }
    
    /**
     * 将值放入 Map（处理 null 值）
     */
    public static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            // 特殊处理 UUID 和 OffsetDateTime
            if (value instanceof UUID) {
                map.put(key, uuidToString((UUID) value));
            } else if (value instanceof OffsetDateTime) {
                map.put(key, offsetDateTimeToString((OffsetDateTime) value));
            } else {
                map.put(key, value);
            }
        }
    }
}

