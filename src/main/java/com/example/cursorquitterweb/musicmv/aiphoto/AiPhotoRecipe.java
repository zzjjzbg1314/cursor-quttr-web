package com.example.cursorquitterweb.musicmv.aiphoto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 配方来自受保护的模板场景；摘要不依赖 JSON 键顺序。 */
public final class AiPhotoRecipe {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static JsonNode find(JsonNode scene, String slot) {
        JsonNode recipes = scene.path("aiPhotoRecipes");
        if (recipes.isMissingNode()) return null;
        if (!recipes.isObject()) throw error("AI_PHOTO_RECIPE_INVALID");
        JsonNode recipe = recipes.get(slot);
        if (recipe == null) return null;
        if (!"ai-photo-recipe-v1".equals(recipe.path("schemaVersion").asText())
                || recipe.path("revision").asText().isEmpty()
                || recipe.path("prompt").asText().trim().isEmpty()
                || recipe.path("prompt").asText().length() > 10000
                || !recipe.path("parameters").isObject()
                || recipe.path("model").asText().isEmpty()
                || recipe.path("provider").asText().isEmpty()) throw error("AI_PHOTO_RECIPE_INVALID");
        return recipe;
    }
    public static String hash(Object value) {
        try {
            Object canonical = canonical(JSON.valueToTree(value));
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(canonical));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b & 255));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException("AI photo fingerprint failed", e); }
    }
    private static Object canonical(JsonNode value) {
        if (value.isObject()) {
            Map<String,Object> map = new TreeMap<>();
            value.fields().forEachRemaining(e -> map.put(e.getKey(), canonical(e.getValue())));
            return map;
        }
        if (value.isArray()) {
            List<Object> list = new ArrayList<>(); value.forEach(v -> list.add(canonical(v))); return list;
        }
        return value;
    }
    public static String fingerprint(String owner, String template, String version, String slot,
                                     String sourceId, String sourceHash, JsonNode recipe) {
        return hash(Arrays.asList(owner, template, version, slot, sourceId, sourceHash, recipe));
    }
    public static ApiException error(String code) {
        return new ApiException(HttpStatus.CONFLICT, code,
                "This photo effect is not ready. Please try again later.");
    }
    private AiPhotoRecipe() {}
}
