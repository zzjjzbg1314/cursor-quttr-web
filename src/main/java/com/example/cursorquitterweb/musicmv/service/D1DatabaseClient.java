package com.example.cursorquitterweb.musicmv.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class D1DatabaseClient {
    private final RestTemplate restTemplate = CloudflareRestTemplateFactory.create();
    private final ObjectMapper objectMapper;

    @Value("${music-mv.d1.enabled:false}")
    private boolean enabled;

    @Value("${music-mv.d1.base-url:https://api.cloudflare.com/client/v4}")
    private String baseUrl;

    @Value("${music-mv.d1.account-id:}")
    private String accountId;

    @Value("${music-mv.d1.database-id:}")
    private String databaseId;

    @Value("${music-mv.d1.api-token:}")
    private String apiToken;

    public D1DatabaseClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConfigured() {
        return enabled && !isBlank(accountId) && !isBlank(databaseId) && !isBlank(apiToken);
    }

    /**
     * Non-secret identifier used by the explicit schema safety gate. Never
     * expose the account id or API token through controller responses.
     */
    public String getDatabaseId() {
        return databaseId;
    }

    public D1QueryResult query(String sql, Object... params) {
        return query(sql, Arrays.asList(params));
    }

    public D1QueryResult query(String sql, List<Object> params) {
        ensureConfigured();

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("sql", sql);
        body.put("params", params == null ? new ArrayList<Object>() : params);

        List<D1QueryResult> results = execute(body);
        if (results.isEmpty()) {
            return new D1QueryResult(new ArrayList<Map<String, Object>>(), null);
        }
        return results.get(0);
    }

    public List<D1QueryResult> batch(List<D1Statement> statements) {
        ensureConfigured();
        if (statements == null || statements.isEmpty()) {
            throw new IllegalArgumentException("D1 batch requires at least one statement");
        }
        List<Map<String, Object>> batch = new ArrayList<Map<String, Object>>();
        for (D1Statement statement : statements) {
            if (statement == null || statement.getSql() == null || statement.getSql().trim().isEmpty()) {
                throw new IllegalArgumentException("D1 batch contains an empty statement");
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("sql", statement.getSql());
            item.put("params", statement.getParams());
            batch.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("batch", batch);
        return execute(body);
    }

    private List<D1QueryResult> execute(Map<String, Object> body) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        String url = baseUrl + "/accounts/" + accountId + "/d1/database/" + databaseId + "/query";
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<Map<String, Object>>(body, headers),
                    String.class
            );
            return parse(response.getBody());
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException(
                    "Cloudflare D1 request failed. Check MUSIC_MV_CLOUDFLARE_ACCOUNT_ID, "
                            + "MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID and "
                            + "MUSIC_MV_CLOUDFLARE_API_TOKEN. "
                            + "Account ID is the 32-character Cloudflare account id, not the D1 database UUID. "
                            + "URL=" + url + ", response=" + e.getResponseBodyAsString(),
                    e
            );
        }
    }

    private List<D1QueryResult> parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false)) {
                throw new IllegalStateException("D1 query failed: " + root.path("errors").toString());
            }

            JsonNode resultNode = root.path("result");
            List<D1QueryResult> results = new ArrayList<D1QueryResult>();
            if (resultNode.isArray()) {
                for (int index = 0; index < resultNode.size(); index++) {
                    results.add(parseResult(resultNode.get(index), index));
                }
            } else if (!resultNode.isMissingNode() && !resultNode.isNull()) {
                results.add(parseResult(resultNode, 0));
            }
            return results;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Parse D1 response failed", e);
        }
    }

    private D1QueryResult parseResult(JsonNode resultNode, int index) {
        if (resultNode.has("success") && !resultNode.path("success").asBoolean(false)) {
            throw new IllegalStateException("D1 statement " + index + " failed: "
                    + resultNode.path("error").toString());
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        JsonNode rowsNode = resultNode.path("results");
        if (rowsNode.isArray()) {
            for (JsonNode row : rowsNode) {
                rows.add(objectMapper.convertValue(row, Map.class));
            }
        }

        Long lastRowId = null;
        JsonNode lastRowIdNode = resultNode.path("meta").path("last_row_id");
        if (lastRowIdNode.isNumber()) {
            lastRowId = lastRowIdNode.asLong();
        }
        return new D1QueryResult(rows, lastRowId);
    }

    private void ensureConfigured() {
        if (!enabled) {
            throw new IllegalStateException("D1 is disabled");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("D1 is enabled but account-id, database-id or api-token is missing");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
