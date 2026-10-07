package com.example.cursorquitterweb.musicmv.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 短期凭证只携带身份，余额和权限变化不作为长期缓存。 */
@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvAccessTokenService {
    public static final String COOKIE = "music_mv_access";
    public static final long LIFETIME_SECONDS = 120;
    private static final String ISSUER = "storyai-session";
    private static final String AUDIENCE = "music-mv-api";
    private final byte[] key;
    private final ObjectMapper mapper;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public MusicMvAccessTokenService(@Value("${music-mv.auth.access-signing-key:}") String key) {
        this(key, Clock.systemUTC());
    }

    MusicMvAccessTokenService(String key, Clock clock) {
        if (key == null || key.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("MUSIC_MV_AUTH_ACCESS_SIGNING_KEY must contain at least 32 random bytes; configure the same key on all instances");
        }
        this.key = key.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        this.mapper = new ObjectMapper();
    }

    public Issued issue(String userId, String sessionId, long sessionExpiresAt, String sessionHash) {
        long now = clock.instant().getEpochSecond();
        long exp = Math.min(now + LIFETIME_SECONDS, sessionExpiresAt);
        if (userId == null || userId.isEmpty() || sessionId == null || sessionId.isEmpty() || sessionHash == null || !sessionHash.matches("[a-f0-9]{64}") || exp <= now) {
            throw new IllegalArgumentException("Cannot issue access token for an invalid session");
        }
        try {
            Map<String, Object> claims = new LinkedHashMap<String, Object>();
            claims.put("iss", ISSUER); claims.put("aud", AUDIENCE);
            claims.put("sub", userId); claims.put("sid", sessionId);
            claims.put("sth", sessionHash);
            claims.put("iat", now); claims.put("exp", exp);
            String unsigned = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
                    + "." + encode(mapper.writeValueAsBytes(claims));
            return new Issued(unsigned + "." + encode(sign(unsigned)), exp, exp - now);
        } catch (Exception e) { throw new IllegalStateException("Cannot sign access token", e); }
    }

    public String verifyUserId(String token, String sessionHash) {
        if (sessionHash == null || token == null || token.length() > 2048) return null;
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return null;
            if (!MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), Base64.getUrlDecoder().decode(parts[2]))) return null;
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long now = clock.instant().getEpochSecond();
            if (!MessageDigest.isEqual(sessionHash.getBytes(StandardCharsets.US_ASCII), claims.path("sth").asText("").getBytes(StandardCharsets.US_ASCII))) return null;
            if (!"HS256".equals(header.path("alg").asText()) || !"JWT".equals(header.path("typ").asText())
                    || !ISSUER.equals(claims.path("iss").asText()) || !AUDIENCE.equals(claims.path("aud").asText())
                    || !claims.path("iat").isIntegralNumber() || !claims.path("exp").isIntegralNumber()) return null;
            long iat = claims.path("iat").asLong(), exp = claims.path("exp").asLong();
            if (iat < 0 || iat > now || exp > now + LIFETIME_SECONDS || exp <= now || exp <= iat || exp - iat > LIFETIME_SECONDS) return null;
            String userId = claims.path("sub").asText(""), sessionId = claims.path("sid").asText("");
            if (!userId.matches("usr_[A-Za-z0-9_-]{1,128}") || sessionId.isEmpty() || sessionId.length() > 128) return null;
            return userId;
        } catch (Exception invalid) { return null; }
    }

    private byte[] sign(String unsigned) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII));
    }
    private static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static final class Issued {
        public final String token;
        public final long expiresAt, maxAge;
        private Issued(String token, long expiresAt, long maxAge) {
            this.token = token; this.expiresAt = expiresAt; this.maxAge = maxAge;
        }
    }
}
