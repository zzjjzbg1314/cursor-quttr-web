package com.example.cursorquitterweb.musicmv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;

/** 网站与管理端共用的平级主题清单；推荐只用于排序。 */
public final class TemplateTopics {
    private static final JsonNode CONFIG;
    static {
        try (InputStream input = TemplateTopics.class.getResourceAsStream("/template-topics.json")) {
            CONFIG = new ObjectMapper().readTree(input);
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    private TemplateTopics() { }
    public static String key(String value) {
        if (value == null) return null;
        for (JsonNode item : CONFIG.path("items")) {
            if (value.equals(item.path("key").asText()) || value.equals(item.path("nameZh").asText())) return item.path("key").asText();
        }
        return CONFIG.path("aliases").path(value).asText(null);
    }
    public static String name(String value) {
        String key = key(value);
        for (JsonNode item : CONFIG.path("items")) if (item.path("key").asText().equals(key)) return item.path("nameZh").asText();
        return value;
    }
    public static List<String> names() {
        List<String> result = new ArrayList<>();
        for (JsonNode item : CONFIG.path("items")) result.add(item.path("nameZh").asText());
        return Collections.unmodifiableList(result);
    }
    public static List<Map<String, Object>> items() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : CONFIG.path("items")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", row.path("key").asText()); item.put("name", row.path("nameZh").asText());
            item.put("nameZh", row.path("nameZh").asText()); item.put("nameEn", row.path("nameEn").asText());
            item.put("selectable", true); item.put("level", 1); item.put("sortOrder", row.path("sortOrder").asInt());
            item.put("children", Collections.emptyList()); result.add(item);
        }
        return result;
    }
    public static Map<String, String> aliases() {
        Map<String, String> result = new LinkedHashMap<>();
        CONFIG.path("aliases").fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }
}
