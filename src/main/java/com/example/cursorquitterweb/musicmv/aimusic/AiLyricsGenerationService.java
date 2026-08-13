package com.example.cursorquitterweb.musicmv.aimusic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.LyricsCandidate;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.LyricsSnapshot;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.Submission;
import com.example.cursorquitterweb.musicmv.dto.AiMusicLyricsCreateRequest;
import com.example.cursorquitterweb.musicmv.support.ApiException;

/** Stateless provider-neutral lyrics generation facade for the website. */
@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class AiLyricsGenerationService {
    private final AiMusicProviderRegistry providers;
    private final String defaultProvider;
    private final String publicBaseUrl;
    private final byte[] taskTokenSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiLyricsGenerationService(
            AiMusicProviderRegistry providers,
            @Value("${music-mv.ai-music.provider:sunoapi}") String defaultProvider,
            @Value("${music-mv.public-base-url:}") String publicBaseUrl,
            @Value("${music-mv.ai-music.task-token-secret:}") String taskTokenSecret
    ) {
        this.providers = providers;
        this.defaultProvider = normalize(defaultProvider);
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.taskTokenSecret = trim(taskTokenSecret).getBytes(StandardCharsets.UTF_8);
    }

    public Map<String, Object> create(String clientId, AiMusicLyricsCreateRequest request,
                                      String requestBaseUrl) {
        String owner = requireId(clientId);
        ensureTokenSecret();
        AiMusicProvider provider = providers.require(defaultProvider);
        if (!provider.supportsLyrics()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_LYRICS_PROVIDER_UNAVAILABLE",
                    "The configured AI music provider does not support lyrics generation",
                    false, null);
        }
        String base = publicBaseUrl.isEmpty() ? trimTrailingSlash(requestBaseUrl) : publicBaseUrl;
        String callbackUrl = base + provider.lyricsWebhookPath();
        Submission submission = provider.submitLyrics(request.getPrompt().trim(), callbackUrl);
        return view(handle(provider.providerCode(), submission.getProviderTaskId(), owner),
                "queued", null, null, false, new ArrayList<LyricsCandidate>());
    }

    public Map<String, Object> get(String clientId, String taskId) {
        String owner = requireId(clientId);
        TaskHandle handle = decode(taskId, owner);
        AiMusicProvider provider = providers.require(handle.providerCode);
        if (!provider.supportsLyrics()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_LYRICS_PROVIDER_UNAVAILABLE",
                    "The configured AI music provider does not support lyrics generation",
                    false, null);
        }
        LyricsSnapshot snapshot = provider.queryLyrics(handle.providerTaskId);
        return view(taskId, snapshot.getStatus(), snapshot.getErrorCode(),
                snapshot.getErrorMessage(), snapshot.isRetryable(), snapshot.getCandidates());
    }

    private Map<String, Object> view(String taskId, String status, String errorCode,
                                     String errorMessage, boolean retryable,
                                     List<LyricsCandidate> candidates) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("taskId", taskId);
        result.put("status", status);
        result.put("errorCode", errorCode);
        result.put("errorMessage", errorMessage);
        result.put("retryable", Boolean.valueOf(retryable));
        List<Map<String, Object>> options = new ArrayList<Map<String, Object>>();
        int index = 0;
        for (LyricsCandidate candidate : candidates) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("optionId", "lyrics_" + (++index));
            item.put("title", candidate.getTitle());
            item.put("text", candidate.getText());
            item.put("status", candidate.getStatus());
            item.put("errorMessage", candidate.getErrorMessage());
            options.add(item);
        }
        result.put("options", options);
        return result;
    }

    private String handle(String providerCode, String providerTaskId, String clientId) {
        String payload = normalize(providerCode) + "\n" + providerTaskId + "\n" + clientId;
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] token = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, token, 0, iv.length);
            System.arraycopy(ciphertext, 0, token, iv.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect lyrics task", exception);
        }
    }

    private TaskHandle decode(String taskId, String clientId) {
        ensureTokenSecret();
        try {
            byte[] token = Base64.getUrlDecoder().decode(trim(taskId));
            if (token.length <= 28) throw invalidTask();
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[token.length - iv.length];
            System.arraycopy(token, 0, iv, 0, iv.length);
            System.arraycopy(token, iv.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            String payload = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\n", -1);
            if (parts.length != 3 || !clientId.equals(parts[2])
                    || trim(parts[0]).isEmpty() || trim(parts[1]).isEmpty()) throw invalidTask();
            return new TaskHandle(normalize(parts[0]), parts[1]);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidTask();
        }
    }

    private SecretKeySpec encryptionKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(taskTokenSecret), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive lyrics task key", exception);
        }
    }

    private String requireId(String value) {
        String normalized = trim(value);
        if (!normalized.matches("^[A-Za-z0-9_-]{8,128}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_MUSIC_CLIENT_ID_INVALID",
                    "Identifier is invalid", false, null);
        }
        return normalized;
    }

    private void ensureTokenSecret() {
        if (taskTokenSecret.length < 16) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_LYRICS_TASK_SECURITY_NOT_CONFIGURED",
                    "AI lyrics task protection is not configured", false, null);
        }
    }

    private ApiException invalidTask() {
        return new ApiException(HttpStatus.NOT_FOUND, "AI_LYRICS_TASK_NOT_FOUND",
                "Lyrics task was not found", false, null);
    }

    private static String normalize(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String trimTrailingSlash(String value) {
        String result = trim(value);
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }

    private static final class TaskHandle {
        private final String providerCode;
        private final String providerTaskId;

        private TaskHandle(String providerCode, String providerTaskId) {
            this.providerCode = providerCode;
            this.providerTaskId = providerTaskId;
        }
    }
}
