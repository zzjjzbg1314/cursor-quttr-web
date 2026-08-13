package com.example.cursorquitterweb.musicmv.aimusic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.http.HttpStatus;

@Service
@ConditionalOnExpression("'${music-mv.enabled:false}' == 'true' and "
        + "'${music-mv.ai-music.kie.enabled:true}' == 'true'")
public class KieAiMusicProvider implements AiMusicProvider {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public KieAiMusicProvider(
            ObjectMapper objectMapper,
            @Value("${music-mv.ai-music.kie.base-url:https://api.kie.ai}") String baseUrl,
            @Value("${music-mv.ai-music.kie.api-key:}") String apiKey,
            @Value("${music-mv.ai-music.kie.model:V5_5}") String model
    ) {
        this(createRestTemplate(), objectMapper, baseUrl, apiKey, model);
    }

    KieAiMusicProvider(RestTemplate restTemplate, ObjectMapper objectMapper,
                       String baseUrl, String apiKey, String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = blank(model) ? "V5_5" : model.trim();
    }

    @Override
    public String providerCode() {
        return "kie";
    }

    @Override
    public String defaultModel() { return model; }

    @Override
    public String webhookPath() { return "/api/music-mv/v1/provider-webhooks/kie/music"; }

    @Override
    public Submission submit(GenerateSongCommand command) {
        ensureConfigured();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("prompt", command.getPrompt());
        body.put("customMode", Boolean.valueOf(command.isCustomMode()));
        body.put("instrumental", Boolean.valueOf(command.isInstrumental()));
        body.put("model", command.getModel());
        body.put("callBackUrl", command.getCallbackUrl());
        if (command.isCustomMode()) {
            put(body, "style", command.getStyle());
            put(body, "title", command.getTitle());
            put(body, "negativeTags", command.getNegativeTags());
            put(body, "vocalGender", command.getVocalGender());
        }

        Map<String, Object> response = exchange(HttpMethod.POST, "/api/v1/generate", body);
        String taskId = text(node(response).path("data"), "taskId", "task_id");
        if (blank(taskId)) {
            throw providerError("KIE_RESPONSE_INVALID",
                    "KIE accepted the request but returned no task id", false);
        }
        return new Submission(taskId, response);
    }

    @Override
    public TaskSnapshot query(String providerTaskId) {
        ensureConfigured();
        Map<String, Object> response = exchange(HttpMethod.GET,
                "/api/v1/generate/record-info?taskId=" + providerTaskId, null);
        JsonNode data = node(response).path("data");
        TaskSnapshot snapshot = new TaskSnapshot();
        String responseTaskId = text(data, "taskId", "task_id");
        snapshot.setProviderTaskId(blank(responseTaskId) ? providerTaskId : responseTaskId);
        snapshot.setStatus(normalizeStatus(text(data, "status")));
        snapshot.setErrorCode(text(data, "errorCode", "error_code"));
        snapshot.setErrorMessage(text(data, "errorMessage", "error_message"));
        snapshot.setRetryable(isRetryable(text(data, "status"), snapshot.getErrorCode()));
        snapshot.setCandidates(parseCandidates(data.path("response").path("sunoData")));
        snapshot.setRaw(response);
        return snapshot;
    }

    public TaskSnapshot parseCallback(Map<String, Object> payload) {
        JsonNode root = node(payload);
        JsonNode data = root.path("data");
        String callbackType = text(data, "callbackType", "callback_type");
        TaskSnapshot snapshot = new TaskSnapshot();
        snapshot.setProviderTaskId(text(data, "task_id", "taskId"));
        snapshot.setStatus(normalizeCallbackStatus(callbackType, root.path("code").asInt(500)));
        boolean failed = "failed".equals(snapshot.getStatus());
        snapshot.setErrorCode(failed && root.has("code") ? root.path("code").asText() : null);
        snapshot.setErrorMessage(failed ? text(root, "msg", "message") : null);
        snapshot.setRetryable("failed".equals(snapshot.getStatus())
                && isRetryable(callbackType, snapshot.getErrorCode()));
        snapshot.setCandidates(parseCandidates(data.path("data")));
        snapshot.setRaw(payload);
        return snapshot;
    }

    private Map<String, Object> exchange(HttpMethod method, String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> response = restTemplate.exchange(baseUrl + path, method,
                    new HttpEntity<Map<String, Object>>(body, headers), String.class);
            Map<String, Object> parsed = objectMapper.readValue(response.getBody(),
                    new TypeReference<Map<String, Object>>() { });
            int code = node(parsed).path("code").asInt(200);
            if (code != 200) throw mappedError(code, node(parsed).path("msg").asText("KIE request failed"));
            return parsed;
        } catch (ApiException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            throw providerError("KIE_HTTP_ERROR",
                    "KIE request failed with HTTP " + exception.getRawStatusCode(),
                    exception.getRawStatusCode() >= 500 || exception.getRawStatusCode() == 429);
        } catch (ResourceAccessException exception) {
            throw providerError("AI_MUSIC_SUBMISSION_UNKNOWN",
                    "KIE request timed out; submission outcome is unknown", true);
        } catch (Exception exception) {
            throw providerError("KIE_RESPONSE_INVALID", "Unable to parse KIE response", false);
        }
    }

    private List<Candidate> parseCandidates(JsonNode values) {
        List<Candidate> result = new ArrayList<Candidate>();
        if (!values.isArray()) return result;
        for (JsonNode value : values) {
            Candidate candidate = new Candidate();
            candidate.setProviderAudioId(text(value, "id", "audioId", "audio_id"));
            candidate.setTitle(text(value, "title"));
            candidate.setLyrics(text(value, "prompt", "lyrics"));
            candidate.setStyle(text(value, "tags", "style"));
            if (value.has("duration") && value.path("duration").isNumber()) {
                candidate.setDurationSeconds(Double.valueOf(value.path("duration").asDouble()));
            }
            candidate.setAudioUrl(text(value, "audioUrl", "audio_url"));
            candidate.setStreamUrl(text(value, "streamAudioUrl", "stream_audio_url"));
            candidate.setImageUrl(text(value, "imageUrl", "image_url"));
            candidate.setRaw(objectMapper.convertValue(value,
                    new TypeReference<Map<String, Object>>() { }));
            if (!blank(candidate.getProviderAudioId())) result.add(candidate);
        }
        return result;
    }

    private String normalizeStatus(String status) {
        String normalized = upper(status);
        if ("SUCCESS".equals(normalized)) return "completed";
        if ("TEXT_SUCCESS".equals(normalized) || "FIRST_SUCCESS".equals(normalized)) return "generating";
        if ("PENDING".equals(normalized) || blank(normalized)) return "queued";
        return "failed";
    }

    private String normalizeCallbackStatus(String callbackType, int code) {
        if (code != 200 || "error".equalsIgnoreCase(callbackType)) return "failed";
        if ("complete".equalsIgnoreCase(callbackType)) return "completed";
        return "generating";
    }

    private boolean isRetryable(String status, String errorCode) {
        String normalized = upper(status);
        return "CREATE_TASK_FAILED".equals(normalized)
                || "GENERATE_AUDIO_FAILED".equals(normalized)
                || "CALLBACK_EXCEPTION".equals(normalized)
                || "500".equals(errorCode) || "501".equals(errorCode) || "531".equals(errorCode);
    }

    private ApiException mappedError(int code, String message) {
        if (code == 402 || code == 429) {
            return providerError("KIE_CREDITS_EXHAUSTED", message, false);
        }
        if (code == 430 || code == 405) {
            return providerError("KIE_RATE_LIMITED", message, true);
        }
        return providerError("KIE_PROVIDER_ERROR", message, code >= 500);
    }

    private ApiException providerError(String code, String message, boolean retryable) {
        return new ApiException(HttpStatus.BAD_GATEWAY, code, message, retryable, null);
    }

    private void ensureConfigured() {
        if (blank(apiKey)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "KIE_NOT_CONFIGURED", "MUSIC_MV_KIE_API_KEY is not configured", false, null);
        }
    }

    private JsonNode node(Map<String, Object> value) {
        return objectMapper.valueToTree(value == null ? Collections.emptyMap() : value);
    }

    private String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                String result = value.asText();
                if (!blank(result)) return result;
            }
        }
        return null;
    }

    private void put(Map<String, Object> map, String key, String value) {
        if (!blank(value)) map.put(key, value);
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static RestTemplate createRestTemplate() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(15000).setConnectionRequestTimeout(15000)
                .setSocketTimeout(120000).build();
        CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config)
                .disableAutomaticRetries().disableCookieManagement().build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
    }
}
