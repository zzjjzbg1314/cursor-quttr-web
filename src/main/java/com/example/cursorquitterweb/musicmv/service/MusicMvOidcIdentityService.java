package com.example.cursorquitterweb.musicmv.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvOidcIdentityService {
    private static final long CLOCK_SKEW_SECONDS = 60L;
    private static final long JWKS_CACHE_SECONDS = 21600L;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final ProviderConfig google;
    private final ProviderConfig apple;
    private final Map<String, CachedKeys> keyCache = new ConcurrentHashMap<String, CachedKeys>();

    public MusicMvOidcIdentityService(
            ObjectMapper objectMapper,
            @Value("${music-mv.auth.google.client-id:}") String googleClientId,
            @Value("${music-mv.auth.google.jwks-url:https://www.googleapis.com/oauth2/v3/certs}") String googleJwksUrl,
            @Value("${music-mv.auth.apple.client-id:}") String appleClientId,
            @Value("${music-mv.auth.apple.redirect-uri:}") String appleRedirectUri,
            @Value("${music-mv.auth.apple.jwks-url:https://appleid.apple.com/auth/keys}") String appleJwksUrl
    ) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(requestFactory);
        this.google = new ProviderConfig("google", trim(googleClientId), trim(googleJwksUrl),
                asList("accounts.google.com", "https://accounts.google.com"), "");
        this.apple = new ProviderConfig("apple", trim(appleClientId), trim(appleJwksUrl),
                Collections.singletonList("https://appleid.apple.com"), trim(appleRedirectUri));
    }

    public Map<String, Object> publicProviders() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("google", providerView(google));
        result.put("apple", providerView(apple));
        return result;
    }

    public VerifiedIdentity verify(String requestedProvider, String idToken, String requestedDisplayName) {
        ProviderConfig provider = provider(requestedProvider);
        if (!provider.configured()) {
            throw new ApiException(HttpStatus.CONFLICT, "MUSIC_MV_LOGIN_PROVIDER_NOT_CONFIGURED",
                    capitalize(provider.code) + " sign-in is not configured");
        }
        try {
            String[] parts = idToken == null ? new String[0] : idToken.trim().split("\\.");
            if (parts.length != 3) {
                throw invalidToken();
            }
            JsonNode header = decodeJson(parts[0]);
            JsonNode claims = decodeJson(parts[1]);
            if (!"RS256".equals(header.path("alg").asText())) {
                throw invalidToken();
            }
            String keyId = header.path("kid").asText();
            if (keyId.isEmpty() || !verifySignature(provider, keyId,
                    (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII),
                    Base64.getUrlDecoder().decode(parts[2]))) {
                throw invalidToken();
            }
            validateClaims(provider, claims);
            String subject = claims.path("sub").asText();
            if (subject.isEmpty() || subject.length() > 255) {
                throw invalidToken();
            }
            String email = text(claims, "email", 320);
            boolean emailVerified = bool(claims.get("email_verified"));
            String displayName = text(claims, "name", 120);
            if (displayName == null) {
                displayName = bounded(requestedDisplayName, 120);
            }
            String avatarUrl = httpsUrl(text(claims, "picture", 2048));
            return new VerifiedIdentity(provider.code, subject, email, emailVerified,
                    displayName, avatarUrl);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    private Map<String, Object> providerView(ProviderConfig provider) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("configured", Boolean.valueOf(provider.configured()));
        if (provider.configured()) {
            view.put("clientId", provider.clientId);
            if (!provider.redirectUri.isEmpty()) {
                view.put("redirectUri", provider.redirectUri);
            }
        }
        return view;
    }

    private ProviderConfig provider(String value) {
        String normalized = trim(value).toLowerCase(Locale.ROOT);
        if ("google".equals(normalized)) return google;
        if ("apple".equals(normalized)) return apple;
        throw new ApiException(HttpStatus.BAD_REQUEST, "MUSIC_MV_LOGIN_PROVIDER_INVALID",
                "Sign-in provider is invalid");
    }

    private void validateClaims(ProviderConfig provider, JsonNode claims) {
        String issuer = claims.path("iss").asText();
        if (!provider.issuers.contains(issuer) || !audienceContains(claims.get("aud"), provider.clientId)) {
            throw invalidToken();
        }
        long now = Instant.now().getEpochSecond();
        long expiresAt = claims.path("exp").asLong(0L);
        long issuedAt = claims.path("iat").asLong(0L);
        if (expiresAt <= now - CLOCK_SKEW_SECONDS || issuedAt > now + CLOCK_SKEW_SECONDS) {
            throw invalidToken();
        }
    }

    private boolean verifySignature(ProviderConfig provider, String keyId,
                                    byte[] signedContent, byte[] signatureBytes) throws Exception {
        PublicKey key = keys(provider, false).get(keyId);
        if (key == null) key = keys(provider, true).get(keyId);
        if (key == null) return false;
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(signedContent);
        return verifier.verify(signatureBytes);
    }

    private Map<String, PublicKey> keys(ProviderConfig provider, boolean forceRefresh) throws Exception {
        CachedKeys cached = keyCache.get(provider.code);
        long now = Instant.now().getEpochSecond();
        if (!forceRefresh && cached != null && cached.expiresAt > now) return cached.keys;
        synchronized (keyCache) {
            cached = keyCache.get(provider.code);
            if (!forceRefresh && cached != null && cached.expiresAt > now) return cached.keys;
            JsonNode response = restTemplate.getForObject(provider.jwksUrl, JsonNode.class);
            Map<String, PublicKey> keys = new LinkedHashMap<String, PublicKey>();
            if (response != null && response.path("keys").isArray()) {
                for (JsonNode item : response.path("keys")) {
                    if (!"RSA".equals(item.path("kty").asText())) continue;
                    String kid = item.path("kid").asText();
                    String modulus = item.path("n").asText();
                    String exponent = item.path("e").asText();
                    if (kid.isEmpty() || modulus.isEmpty() || exponent.isEmpty()) continue;
                    RSAPublicKeySpec spec = new RSAPublicKeySpec(
                            new BigInteger(1, Base64.getUrlDecoder().decode(modulus)),
                            new BigInteger(1, Base64.getUrlDecoder().decode(exponent)));
                    keys.put(kid, KeyFactory.getInstance("RSA").generatePublic(spec));
                }
            }
            CachedKeys fresh = new CachedKeys(Collections.unmodifiableMap(keys), now + JWKS_CACHE_SECONDS);
            keyCache.put(provider.code, fresh);
            return fresh.keys;
        }
    }

    private JsonNode decodeJson(String value) throws Exception {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(value));
    }

    private boolean audienceContains(JsonNode audience, String expected) {
        if (audience == null) return false;
        if (audience.isTextual()) return expected.equals(audience.asText());
        if (audience.isArray()) {
            for (JsonNode item : audience) if (expected.equals(item.asText())) return true;
        }
        return false;
    }

    private boolean bool(JsonNode value) {
        if (value == null) return false;
        return value.isBoolean() ? value.asBoolean() : "true".equalsIgnoreCase(value.asText());
    }

    private String text(JsonNode claims, String key, int max) {
        JsonNode value = claims.get(key);
        return value == null || value.isNull() ? null : bounded(value.asText(), max);
    }

    private String bounded(String value, int max) {
        String normalized = trim(value);
        if (normalized.isEmpty()) return null;
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String httpsUrl(String value) {
        return value != null && value.startsWith("https://") ? value : null;
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "MUSIC_MV_IDENTITY_TOKEN_INVALID",
                "The identity token is invalid or expired");
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static List<String> asList(String first, String second) {
        List<String> values = new ArrayList<String>();
        values.add(first);
        values.add(second);
        return Collections.unmodifiableList(values);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ProviderConfig {
        private final String code;
        private final String clientId;
        private final String jwksUrl;
        private final List<String> issuers;
        private final String redirectUri;

        private ProviderConfig(String code, String clientId, String jwksUrl,
                               List<String> issuers, String redirectUri) {
            this.code = code;
            this.clientId = clientId;
            this.jwksUrl = jwksUrl;
            this.issuers = issuers;
            this.redirectUri = redirectUri;
        }

        private boolean configured() {
            return !clientId.isEmpty() && !jwksUrl.isEmpty();
        }
    }

    private static final class CachedKeys {
        private final Map<String, PublicKey> keys;
        private final long expiresAt;

        private CachedKeys(Map<String, PublicKey> keys, long expiresAt) {
            this.keys = keys;
            this.expiresAt = expiresAt;
        }
    }

    public static final class VerifiedIdentity {
        private final String provider;
        private final String subject;
        private final String email;
        private final boolean emailVerified;
        private final String displayName;
        private final String avatarUrl;

        private VerifiedIdentity(String provider, String subject, String email,
                                 boolean emailVerified, String displayName, String avatarUrl) {
            this.provider = provider;
            this.subject = subject;
            this.email = email;
            this.emailVerified = emailVerified;
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
        }

        public String getProvider() { return provider; }
        public String getSubject() { return subject; }
        public String getEmail() { return email; }
        public boolean isEmailVerified() { return emailVerified; }
        public String getDisplayName() { return displayName; }
        public String getAvatarUrl() { return avatarUrl; }
    }
}
