package com.example.cursorquitterweb.musicmv.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** StoryAI 专用短期签名凭证，与长期会话绑定，不保存个人资料。 */
@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvAccessTokenService {
    public static final String COOKIE = "music_mv_access_v2";
    public static final long TTL_SECONDS = 900;
    private final byte[] key;
    private final Clock clock;

    @Autowired
    public MusicMvAccessTokenService(@Value("${music-mv.auth.access-signing-key:}") String secret) {
        this(secret, Clock.systemUTC());
    }

    MusicMvAccessTokenService(String secret, Clock clock) {
        this.clock = clock;
        if (secret == null || secret.isEmpty()) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
            org.slf4j.LoggerFactory.getLogger(MusicMvAccessTokenService.class).warn(
                    "StoryAI uses a process-local access signing key; configure MUSIC_MV_AUTH_ACCESS_SIGNING_KEY for multiple instances");
        } else {
            key = secret.getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) throw new IllegalArgumentException("StoryAI access signing key requires at least 32 bytes");
        }
    }

    String issue(String userId, String sessionHash, long sessionExpiry) {
        if (userId == null || !userId.matches("[A-Za-z0-9_-]{1,128}")) return null;
        long expiry = Math.min(clock.instant().getEpochSecond() + TTL_SECONDS, sessionExpiry);
        if (expiry <= clock.instant().getEpochSecond()) return null;
        String payload = "storyai-v2." + userId + "." + sessionHash + "." + expiry;
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    String verify(String token, String sessionHash) {
        if (token == null || token.length() > 512) return null;
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 5 || !"storyai-v2".equals(parts[0]) || !sessionHash.equals(parts[2])) return null;
            String payload = token.substring(0, token.lastIndexOf('.'));
            if (!MessageDigest.isEqual(sign(payload), Base64.getUrlDecoder().decode(parts[4]))) return null;
            long expiry = Long.parseLong(parts[3]);
            long now = clock.instant().getEpochSecond();
            return expiry > now && expiry <= now + TTL_SECONDS ? parts[1] : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }
}
