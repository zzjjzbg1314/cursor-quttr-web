package com.example.cursorquitterweb.musicmv.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.repository.MusicMvAuthRepository;
import com.example.cursorquitterweb.musicmv.service.MusicMvOidcIdentityService.VerifiedIdentity;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvAuthService {
    public static final String SESSION_COOKIE = "music_mv_session";
    private static final Pattern ANONYMOUS_CLIENT_ID = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MusicMvAuthRepository repository;
    private final MusicMvOidcIdentityService oidc;
    private final int sessionDays;

    public MusicMvAuthService(MusicMvAuthRepository repository,
                              MusicMvOidcIdentityService oidc,
                              @Value("${music-mv.auth.session-days:30}") int sessionDays) {
        this.repository = repository;
        this.oidc = oidc;
        this.sessionDays = Math.max(1, Math.min(sessionDays, 90));
    }

    public Map<String, Object> providers() {
        return oidc.publicProviders();
    }

    public Map<String, Object> login(String provider, String idToken, String requestedDisplayName,
                                     String anonymousClientId, HttpServletRequest request,
                                     HttpServletResponse response) {
        VerifiedIdentity identity = oidc.verify(provider, idToken, requestedDisplayName);
        Map<String, Object> user = repository.findByIdentity(identity.getProvider(), identity.getSubject());
        String displayName = displayName(identity, requestedDisplayName);
        String userId;
        if (user == null) {
            userId = id("usr");
            repository.createUserAndIdentity(userId, id("identity"), "story-" + shortId(),
                    displayName, locale(request), identity);
        } else {
            userId = RowUtils.str(user, "user_id");
            repository.touchIdentity(userId, identity, displayName);
        }

        String anonymous = validatedAnonymousClientId(anonymousClientId, false);
        if (anonymous != null) repository.claimAnonymousWork(userId, anonymous);
        repository.revokeExpiredSessions(userId);

        String rawSessionToken = randomToken();
        repository.createSession(id("session"), userId, sha256(rawSessionToken), sessionDays);
        setSessionCookie(response, rawSessionToken, request, sessionDays * 24 * 60 * 60);

        Map<String, Object> current = repository.findByIdentity(identity.getProvider(), identity.getSubject());
        return sessionView(current);
    }

    public Map<String, Object> currentSession(HttpServletRequest request) {
        Map<String, Object> user = sessionUser(request);
        if (user == null) {
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("authenticated", Boolean.FALSE);
            response.put("user", null);
            return response;
        }
        repository.touchSession(RowUtils.str(user, "session_id"));
        return sessionView(user);
    }

    public String effectiveClientId(HttpServletRequest request, String suppliedClientId) {
        Map<String, Object> user = sessionUser(request);
        if (user != null) return RowUtils.str(user, "user_id");
        String clientId = validatedAnonymousClientId(suppliedClientId, true);
        if (clientId.startsWith("usr_")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MUSIC_MV_LOGIN_REQUIRED",
                    "Sign in to access this workspace");
        }
        return clientId;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = cookie(request, SESSION_COOKIE);
        if (token != null) repository.revokeSession(sha256(token));
        setSessionCookie(response, "", request, 0);
    }

    private Map<String, Object> sessionUser(HttpServletRequest request) {
        String token = cookie(request, SESSION_COOKIE);
        if (token == null || token.length() < 32 || token.length() > 256) return null;
        return repository.findBySessionTokenHash(sha256(token));
    }

    private Map<String, Object> sessionView(Map<String, Object> user) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("authenticated", Boolean.TRUE);
        Map<String, Object> userView = new LinkedHashMap<String, Object>();
        userView.put("id", RowUtils.str(user, "user_id"));
        userView.put("displayName", RowUtils.str(user, "display_name"));
        userView.put("handle", RowUtils.str(user, "handle"));
        userView.put("avatarUrl", RowUtils.str(user, "avatar_url"));
        userView.put("email", RowUtils.str(user, "email"));
        userView.put("locale", RowUtils.str(user, "locale"));
        view.put("user", userView);
        return view;
    }

    private String validatedAnonymousClientId(String value, boolean required) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            if (!required) return null;
            throw new ApiException(HttpStatus.BAD_REQUEST, "MUSIC_MV_CLIENT_ID_REQUIRED",
                    "A browser workspace id is required");
        }
        if (!ANONYMOUS_CLIENT_ID.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MUSIC_MV_CLIENT_ID_INVALID",
                    "The browser workspace id is invalid");
        }
        return normalized;
    }

    private String displayName(VerifiedIdentity identity, String requested) {
        String value = trim(identity.getDisplayName());
        if (value == null) value = trim(requested);
        if (value == null && identity.getEmail() != null) value = identity.getEmail().split("@", 2)[0];
        if (value == null) value = "StoryAI Listener";
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    private String locale(HttpServletRequest request) {
        Locale locale = request.getLocale();
        return locale == null ? "en" : locale.toLanguageTag();
    }

    private void setSessionCookie(HttpServletResponse response, String value,
                                  HttpServletRequest request, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, value)
                .httpOnly(true).secure(isSecure(request)).sameSite("Lax")
                .path("/").maxAge(maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private boolean isSecure(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return trim(cookie.getValue());
        return null;
    }

    private static String id(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String trim(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
