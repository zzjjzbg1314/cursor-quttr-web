package com.example.cursorquitterweb.musicmv.aimusic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.support.ApiException;

class KieWebhookVerifierTest {
    @Test
    void verifiesDocumentedTaskTimestampHmacContract() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        Map<String, Object> payload = payload("task-123");
        KieWebhookVerifier verifier = new KieWebhookVerifier("webhook-secret", 300L);

        String taskId = verifier.verify(payload, timestamp,
                sign("task-123", timestamp, "webhook-secret"));

        assertThat(taskId).isEqualTo("task-123");
    }

    @Test
    void rejectsInvalidAndExpiredSignatures() throws Exception {
        KieWebhookVerifier verifier = new KieWebhookVerifier("webhook-secret", 30L);
        String current = Long.toString(Instant.now().getEpochSecond());

        assertThatThrownBy(() -> verifier.verify(payload("task-123"), current, "invalid"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("KIE_WEBHOOK_SIGNATURE_INVALID"));

        String expired = Long.toString(Instant.now().minusSeconds(120L).getEpochSecond());
        assertThatThrownBy(() -> verifier.verify(payload("task-123"), expired,
                sign("task-123", expired, "webhook-secret")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("KIE_WEBHOOK_EXPIRED"));
    }

    private Map<String, Object> payload(String taskId) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("task_id", taskId);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("data", data);
        return payload;
    }

    private String sign(String taskId, String timestamp, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(
                (taskId + "." + timestamp).getBytes(StandardCharsets.UTF_8)));
    }
}
