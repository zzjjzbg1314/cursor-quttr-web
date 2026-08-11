package com.example.cursorquitterweb.musicmv.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public final class RowUtils {
    private RowUtils() {
    }

    public static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Long lng(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    public static Integer integer(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    public static Double dbl(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.valueOf(String.valueOf(value));
    }

    public static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static LocalDateTime date(Map<String, Object> row, String key) {
        String value = str(row, key);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
    }
}
