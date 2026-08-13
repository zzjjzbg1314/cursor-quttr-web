package com.example.cursorquitterweb.musicmv.aimusic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.LyricsCandidate;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.LyricsSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** sunoapi.org boundary; the rest of the application only sees AiMusicProvider. */
@Service
@ConditionalOnExpression("'${music-mv.enabled:false}' == 'true' and "
        + "'${music-mv.ai-music.sunoapi.enabled:true}' == 'true'")
public class SunoApiAiMusicProvider implements AiMusicProvider {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String callbackTokenSecret;
    private final String model;

    @Autowired
    public SunoApiAiMusicProvider(
            ObjectMapper objectMapper,
            @Value("${music-mv.ai-music.sunoapi.base-url:https://api.sunoapi.org}") String baseUrl,
            @Value("${music-mv.ai-music.sunoapi.api-key:}") String apiKey,
            @Value("${music-mv.ai-music.sunoapi.callback-token-secret:}")
                    String callbackTokenSecret,
            @Value("${music-mv.ai-music.sunoapi.model:V5_5}") String model
    ) {
        this(createRestTemplate(), objectMapper, baseUrl, apiKey, callbackTokenSecret, model);
    }

    SunoApiAiMusicProvider(RestTemplate restTemplate, ObjectMapper objectMapper,
                            String baseUrl, String apiKey, String callbackTokenSecret,
                            String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = trim(apiKey);
        this.callbackTokenSecret = trim(callbackTokenSecret);
        this.model = blank(model) ? "V5_5" : model.trim();
    }

    @Override
    public String providerCode() { return "sunoapi"; }

    @Override
    public String defaultModel() { return model; }

    @Override
    public String webhookPath() {
        return "/api/music-mv/v1/provider-webhooks/sunoapi/music";
    }

    @Override
    public boolean supportsLyrics() { return true; }

    @Override
    public Submission submitLyrics(String prompt, String callbackUrl) {
        ensureConfigured();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("prompt", prompt);
        body.put("callBackUrl", callbackUrl);
        Map<String, Object> response = exchange(HttpMethod.POST, "/api/v1/lyrics", body);
        String taskId = text(node(response).path("data"), "taskId", "task_id");
        if (blank(taskId)) {
            throw providerError("SUNOAPI_LYRICS_RESPONSE_INVALID",
                    "SunoAPI accepted the lyrics request but returned no task id", false);
        }
        return new Submission(taskId, response);
    }

    @Override
    public LyricsSnapshot queryLyrics(String providerTaskId) {
        ensureConfigured();
        String path = UriComponentsBuilder.fromPath("/api/v1/lyrics/record-info")
                .queryParam("taskId", providerTaskId).build().encode().toUriString();
        Map<String, Object> response = exchange(HttpMethod.GET, path, null);
        JsonNode data = node(response).path("data");
        LyricsSnapshot snapshot = new LyricsSnapshot();
        String responseTaskId = text(data, "taskId", "task_id");
        snapshot.setProviderTaskId(blank(responseTaskId) ? providerTaskId : responseTaskId);
        snapshot.setStatus(normalizeLyricsStatus(text(data, "status")));
        snapshot.setErrorCode(text(data, "errorCode", "error_code"));
        snapshot.setErrorMessage(text(data, "errorMessage", "error_message"));
        snapshot.setRetryable(isLyricsRetryable(text(data, "status"), snapshot.getErrorCode()));
        snapshot.setCandidates(parseLyricsCandidates(data.path("response").path("data")));
        snapshot.setRaw(response);
        return snapshot;
    }

    @Override
    public String callbackUrl(String publicBaseUrl, String jobId) {
        ensureConfigured();
        return UriComponentsBuilder.fromHttpUrl(publicBaseUrl + webhookPath())
                .queryParam("jobId", jobId)
                .queryParam("token", callbackToken(jobId))
                .build().encode().toUriString();
    }

    @Override
    public Submission submit(GenerateSongCommand command) {
        ensureConfigured();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        put(body, "prompt", command.getPrompt());
        body.put("customMode", Boolean.valueOf(command.isCustomMode()));
        body.put("instrumental", Boolean.valueOf(command.isInstrumental()));
        body.put("model", command.getModel());
        body.put("callBackUrl", command.getCallbackUrl());
        if (command.isCustomMode()) {
            put(body, "style", command.getStyle());
            put(body, "title", command.getTitle());
            put(body, "negativeTags", command.getNegativeTags());
            put(body, "vocalGender", command.getVocalGender());
            put(body, "styleWeight", command.getStyleWeight());
            put(body, "weirdnessConstraint", command.getWeirdnessConstraint());
        }

        Map<String, Object> response = exchange(HttpMethod.POST, "/api/v1/generate", body);
        String taskId = text(node(response).path("data"), "taskId", "task_id");
        if (blank(taskId)) {
            throw providerError("SUNOAPI_RESPONSE_INVALID",
                    "SunoAPI accepted the request but returned no task id", false);
        }
        return new Submission(taskId, response);
    }

    @Override
    public TaskSnapshot query(String providerTaskId) {
        ensureConfigured();
        String path = UriComponentsBuilder.fromPath("/api/v1/generate/record-info")
                .queryParam("taskId", providerTaskId).build().encode().toUriString();
        Map<String, Object> response = exchange(HttpMethod.GET, path, null);
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
        snapshot.setRetryable(failed && isRetryable(callbackType, snapshot.getErrorCode()));
        snapshot.setCandidates(parseCandidates(data.path("data")));
        snapshot.setRaw(payload);
        return snapshot;
    }

    public void verifyCallbackToken(String jobId, String suppliedToken) {
        if (blank(jobId) || blank(suppliedToken) || callbackTokenSecret.isEmpty()) {
            throw unauthorized("SUNOAPI_WEBHOOK_TOKEN_INVALID", "Invalid SunoAPI callback token");
        }
        byte[] expected = callbackToken(jobId).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw unauthorized("SUNOAPI_WEBHOOK_TOKEN_INVALID", "Invalid SunoAPI callback token");
        }
    }

    private String callbackToken(String jobId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(callbackTokenSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] digest = mac.doFinal(("music-mv:sunoapi:" + jobId)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect SunoAPI callback", exception);
        }
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
            if (code != 200) {
                throw mappedError(code,
                        node(parsed).path("msg").asText("SunoAPI request failed"));
            }
            return parsed;
        } catch (ApiException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            throw providerError("SUNOAPI_HTTP_ERROR",
                    "SunoAPI request failed with HTTP " + exception.getRawStatusCode(),
                    exception.getRawStatusCode() >= 500 || exception.getRawStatusCode() == 429);
        } catch (ResourceAccessException exception) {
            throw providerError("AI_MUSIC_SUBMISSION_UNKNOWN",
                    "SunoAPI request timed out; submission outcome is unknown", true);
        } catch (Exception exception) {
            throw providerError("SUNOAPI_RESPONSE_INVALID",
                    "Unable to parse SunoAPI response", false);
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

    private List<LyricsCandidate> parseLyricsCandidates(JsonNode values) {
        List<LyricsCandidate> result = new ArrayList<LyricsCandidate>();
        if (!values.isArray()) return result;
        for (JsonNode value : values) {
            LyricsCandidate candidate = new LyricsCandidate();
            candidate.setTitle(text(value, "title"));
            candidate.setText(text(value, "text", "lyrics"));
            candidate.setStatus(text(value, "status"));
            candidate.setErrorMessage(text(value, "errorMessage", "error_message"));
            if (!blank(candidate.getText())) result.add(candidate);
        }
        return result;
    }

    private String normalizeStatus(String status) {
        String normalized = upper(status);
        if ("SUCCESS".equals(normalized)) return "completed";
        if ("TEXT_SUCCESS".equals(normalized) || "FIRST_SUCCESS".equals(normalized)) {
            return "generating";
        }
        if ("PENDING".equals(normalized) || blank(normalized)) return "queued";
        return "failed";
    }

    private String normalizeCallbackStatus(String callbackType, int code) {
        if (code != 200 || "error".equalsIgnoreCase(callbackType)) return "failed";
        if ("complete".equalsIgnoreCase(callbackType)) return "completed";
        return "generating";
    }

    private String normalizeLyricsStatus(String status) {
        String normalized = upper(status);
        if ("SUCCESS".equals(normalized)) return "completed";
        if ("PENDING".equals(normalized) || blank(normalized)) return "queued";
        return "failed";
    }

    private boolean isLyricsRetryable(String status, String errorCode) {
        String normalized = upper(status);
        return "CREATE_TASK_FAILED".equals(normalized)
                || "GENERATE_LYRICS_FAILED".equals(normalized)
                || "CALLBACK_EXCEPTION".equals(normalized)
                || "451".equals(errorCode) || "455".equals(errorCode)
                || "500".equals(errorCode);
    }

    private boolean isRetryable(String status, String errorCode) {
        String normalized = upper(status);
        return "CREATE_TASK_FAILED".equals(normalized)
                || "GENERATE_AUDIO_FAILED".equals(normalized)
                || "CALLBACK_EXCEPTION".equals(normalized)
                || "451".equals(errorCode) || "455".equals(errorCode)
                || "500".equals(errorCode);
    }

    private ApiException mappedError(int code, String message) {
        if (code == 429) {
            return providerError("SUNOAPI_CREDITS_EXHAUSTED", message, false);
        }
        if (code == 405 || code == 430) {
            return providerError("SUNOAPI_RATE_LIMITED", message, true);
        }
        if (code == 455) {
            return providerError("SUNOAPI_MAINTENANCE", message, true);
        }
        return providerError("SUNOAPI_PROVIDER_ERROR", message, code >= 500);
    }

    private void ensureConfigured() {
        if (apiKey.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SUNOAPI_NOT_CONFIGURED",
                    "MUSIC_MV_SUNOAPI_API_KEY is not configured", false, null);
        }
        if (callbackTokenSecret.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "SUNOAPI_CALLBACK_SECURITY_NOT_CONFIGURED",
                    "SunoAPI callback protection is not configured", false, null);
        }
    }

    private ApiException providerError(String code, String message, boolean retryable) {
        return new ApiException(HttpStatus.BAD_GATEWAY, code, message, retryable, null);
    }

    private ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message, false, null);
    }

    private JsonNode node(Map<String, Object> value) {
        return objectMapper.valueToTree(value == null ? Collections.emptyMap() : value);
    }

    private String text(JsonNode value, String... keys) {
        for (String key : keys) {
            JsonNode item = value.path(key);
            if (!item.isMissingNode() && !item.isNull()) {
                String result = item.asText();
                if (!blank(result)) return result;
            }
        }
        return null;
    }

    private void put(Map<String, Object> target, String key, String value) {
        if (!blank(value)) target.put(key, value);
    }

    private void put(Map<String, Object> target, String key, Double value) {
        if (value != null) target.put(key, value);
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimTrailingSlash(String value) {
        String result = trim(value);
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private static RestTemplate createRestTemplate() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(15000).setConnectionRequestTimeout(15000)
                .setSocketTimeout(120000).build();
        CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config)
                .disableAutomaticRetries().disableCookieManagement().build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
    }
}
