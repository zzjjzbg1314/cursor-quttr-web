package com.example.cursorquitterweb.musicmv.aimusic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.support.ApiException;

@Service
@ConditionalOnExpression("'${music-mv.enabled:false}' == 'true' and "
        + "'${music-mv.ai-music.kie.enabled:true}' == 'true'")
public class KieWebhookVerifier {
    private final String hmacKey;
    private final long maxAgeSeconds;

    public KieWebhookVerifier(
            @Value("${music-mv.ai-music.kie.webhook-hmac-key:}") String hmacKey,
            @Value("${music-mv.ai-music.kie.webhook-max-age-seconds:300}") long maxAgeSeconds
    ) {
        this.hmacKey = hmacKey == null ? "" : hmacKey.trim();
        this.maxAgeSeconds = Math.max(30L, Math.min(3600L, maxAgeSeconds));
    }

    public String verify(Map<String, Object> payload, String timestamp, String signature) {
        if (hmacKey.isEmpty()) {
            throw unauthorized("KIE_WEBHOOK_NOT_CONFIGURED", "KIE webhook verification is not configured");
        }
        long seconds;
        try {
            seconds = Long.parseLong(timestamp);
        } catch (Exception exception) {
            throw unauthorized("KIE_WEBHOOK_TIMESTAMP_INVALID", "Invalid KIE webhook timestamp");
        }
        if (Math.abs(Instant.now().getEpochSecond() - seconds) > maxAgeSeconds) {
            throw unauthorized("KIE_WEBHOOK_EXPIRED", "KIE webhook timestamp is outside the allowed window");
        }
        String taskId = taskId(payload);
        if (blank(taskId)) {
            throw unauthorized("KIE_WEBHOOK_TASK_ID_MISSING", "KIE webhook task id is missing");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = Base64.getEncoder().encodeToString(mac.doFinal(
                    (taskId + "." + timestamp).getBytes(StandardCharsets.UTF_8)));
            byte[] supplied = signature == null ? new byte[0] : signature.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied)) {
                throw unauthorized("KIE_WEBHOOK_SIGNATURE_INVALID", "Invalid KIE webhook signature");
            }
            return taskId;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized("KIE_WEBHOOK_SIGNATURE_INVALID", "Unable to verify KIE webhook signature");
        }
    }

    @SuppressWarnings("unchecked")
    private String taskId(Map<String, Object> payload) {
        if (payload == null) return null;
        Object dataValue = payload.get("data");
        if (dataValue instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) dataValue;
            Object value = data.containsKey("task_id") ? data.get("task_id") : data.get("taskId");
            if (value != null) return String.valueOf(value);
        }
        Object direct = payload.containsKey("task_id") ? payload.get("task_id") : payload.get("taskId");
        return direct == null ? null : String.valueOf(direct);
    }

    private ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message, false, null);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
