package com.example.cursorquitterweb.musicmv.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
    private static final String LEGACY_ACCESS_COOKIE = "music_mv_access";

    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> renewing = new ConcurrentHashMap<>();
    private final MusicMvAccessTokenService accessTokens;

    public MusicMvAuthService(MusicMvAuthRepository repository, MusicMvOidcIdentityService oidc, int sessionDays) {
        this(repository, oidc, sessionDays, new MusicMvAccessTokenService(""));
    }

    @Autowired
    public MusicMvAuthService(MusicMvAuthRepository repository,
                              MusicMvOidcIdentityService oidc,
                              @Value("${music-mv.auth.session-days:30}") int sessionDays,
                              MusicMvAccessTokenService accessTokens) {
        this.repository = repository;
        this.oidc = oidc;
        this.sessionDays = Math.max(1, Math.min(sessionDays, 90));
        this.accessTokens = accessTokens;
    }

    public Map<String, Object> providers() {
        return oidc.publicProviders();
    }

    public Map<String, Object> login(String provider, String idToken, String requestedDisplayName,
                                     String anonymousClientId, HttpServletRequest request,
                                     HttpServletResponse response) {
        if (!"google".equals(provider)) throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_CHALLENGE_REQUIRED", "Start sign-in again.");
        return finishLogin(oidc.verify(provider, idToken, requestedDisplayName), requestedDisplayName,
                anonymousClientId, null, request, response);
    }

    public Map<String, Object> finishLogin(VerifiedIdentity identity, String requestedDisplayName,
            String anonymousClientId, String targetUserId, HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> user = repository.findByIdentity(identity.getProvider(), identity.getSubject());
        String displayName = displayName(identity, requestedDisplayName);
        if (user == null) user = repository.resolveNewIdentity(id("usr"), id("identity"), "story-" + shortId(),
                displayName, locale(request), identity, targetUserId);
        if (user == null) throw new ApiException(HttpStatus.CONFLICT, targetUserId == null ? "AUTH_LINK_REQUIRED" : "AUTH_IDENTITY_IN_USE", "Sign in with your original method, then link this method in your account menu.");
        String userId = RowUtils.str(user, "user_id");
        if (targetUserId != null && !targetUserId.equals(userId))
            throw new ApiException(HttpStatus.CONFLICT, "AUTH_IDENTITY_IN_USE", "This sign-in method belongs to another account. No accounts have been merged.");
        if (user.get("status") != null && !"active".equals(RowUtils.str(user, "status"))) throw loginRequired();
        repository.touchIdentity(userId, identity, displayName);
        if (targetUserId != null) return currentSession(request, response);

        String anonymous = validatedAnonymousClientId(anonymousClientId, false);
        if (anonymous != null && !anonymous.startsWith("usr_")) repository.claimAnonymousWork(userId, anonymous);
        repository.revokeExpiredSessions(userId);

        String rawSessionToken = randomToken();
        repository.createSession(id("session"), userId, sha256(rawSessionToken), sessionDays);
        Map<String, Object> current = repository.findBySessionTokenHash(sha256(rawSessionToken));
        if (current == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Sign-in could not be confirmed. Please sign in again.");
        }
        setSessionCookie(response, rawSessionToken, request, sessionDays * 24 * 60 * 60);
        setCookie(response, LEGACY_ACCESS_COOKIE, "", request, 0);
        issueAccess(current, rawSessionToken, request, response);
        return sessionView(current);
    }

    public Map<String, Object> currentSession(HttpServletRequest request, HttpServletResponse servletResponse) {
        Map<String, Object> user = databaseSessionUser(request);
        if (user == null) {
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("authenticated", Boolean.FALSE);
            response.put("user", null);
            setCookie(servletResponse, MusicMvAccessTokenService.COOKIE, "", request, 0);
            if (cookie(request, SESSION_COOKIE) != null) response.put("reason", "session_ended");
            setCookie(servletResponse, LEGACY_ACCESS_COOKIE, "", request, 0);
            return response;
        }
        if (RowUtils.bool(user, "session_touch_due")) {
            repository.touchSession(RowUtils.str(user, "session_id"));
        }
        setCookie(servletResponse, LEGACY_ACCESS_COOKIE, "", request, 0);
        issueAccess(user, cookie(request, SESSION_COOKIE), request, servletResponse);
        return sessionView(user);
    }

    public String effectiveClientId(HttpServletRequest request, String suppliedClientId) {
        Map<String, Object> user = sessionUser(request);
        String userId = user == null ? null : RowUtils.str(user, "user_id");
        if (userId != null) return userId;
        if (cookie(request, SESSION_COOKIE) != null || cookie(request, LEGACY_ACCESS_COOKIE) != null || cookie(request, MusicMvAccessTokenService.COOKIE) != null) throw loginRequired();
        String clientId = validatedAnonymousClientId(suppliedClientId, true);
        if (clientId.startsWith("usr_")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MUSIC_MV_LOGIN_REQUIRED",
                    "Sign in to access this workspace");
        }
        return clientId;
    }

    /** 普通读取本地验签；写操作实时校验会话，不能信任浏览器工作区标识。 */
    public String requireUserId(HttpServletRequest request) {
        Map<String, Object> user = sessionUser(request);
        String userId = user == null ? null : RowUtils.str(user, "user_id");
        if (userId != null) return userId;
        throw loginRequired();
    }

    /** 下载与普通业务接口使用同一会话校验。 */
    public String requireDownloadUserId(HttpServletRequest request) {
        return requireUserId(request);
    }

    private ApiException loginRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Sign in to create and access your music");
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        setCookie(response, MusicMvAccessTokenService.COOKIE, "", request, 0);
        String token = cookie(request, SESSION_COOKIE);
        if (token != null) repository.revokeSession(sha256(token));
        setSessionCookie(response, "", request, 0);
        setCookie(response, LEGACY_ACCESS_COOKIE, "", request, 0);
    }

    public String requireFreshUserId(HttpServletRequest request) {
        markPrivateResponse();
        Map<String, Object> user = databaseSessionUser(request);
        String id = user == null ? null : RowUtils.str(user, "user_id");
        if (id == null) throw loginRequired();
        return id;
    }

    private Map<String, Object> sessionUser(HttpServletRequest request) {
        markPrivateResponse();
        String token = cookie(request, SESSION_COOKIE);
        if (token == null || token.length() < 32 || token.length() > 256) return null;
        boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        if (read) {
            String id = accessTokens.verify(cookie(request, MusicMvAccessTokenService.COOKIE), sha256(token));
            if (id != null) {
                Map<String, Object> user = new LinkedHashMap<String, Object>();
                user.put("user_id", id);
                return user;
            }
        }
        Map<String, Object> user = read ? renewSession(token) : databaseSessionUser(request);
        RequestAttributes attributes =
                RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            HttpServletResponse response = ((ServletRequestAttributes) attributes).getResponse();
            if (response != null) {
                response.setHeader("Cache-Control", "no-store");
                if (user != null) issueAccess(user, token, request, response);
                else setCookie(response, MusicMvAccessTokenService.COOKIE, "", request, 0);
            }
        }
        return user;
    }

    // 仅合并同一进程内正在进行的续期查询，不缓存结果或延迟敏感操作的撤销检查。
    private Map<String, Object> renewSession(String token) {
        String hash = sha256(token);
        if (renewing.size() >= 1024) return repository.findBySessionTokenHash(hash);
        CompletableFuture<Map<String, Object>> pending = new CompletableFuture<>();
        CompletableFuture<Map<String, Object>> existing = renewing.putIfAbsent(hash, pending);
        if (existing != null) {
            try { return existing.join(); }
            catch (CompletionException exception) {
                if (exception.getCause() instanceof RuntimeException) throw (RuntimeException) exception.getCause();
                throw exception;
            }
        }
        try {
            Map<String, Object> user = repository.findBySessionTokenHash(hash);
            pending.complete(user);
            return user;
        } catch (RuntimeException | Error exception) {
            pending.completeExceptionally(exception);
            throw exception;
        } finally {
            renewing.remove(hash, pending);
        }
    }

    private void markPrivateResponse() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            HttpServletResponse response = ((ServletRequestAttributes) attributes).getResponse();
            if (response != null) response.setHeader("Cache-Control", "no-store");
        }
    }

    private Map<String, Object> databaseSessionUser(HttpServletRequest request) {
        String token = cookie(request, SESSION_COOKIE);
        if (token == null || token.length() < 32 || token.length() > 256) return null;
        return repository.findBySessionTokenHash(sha256(token));
    }

    private void issueAccess(Map<String, Object> user, String sessionToken,
                             HttpServletRequest request, HttpServletResponse response) {
        String expiresAt = RowUtils.str(user, "expires_at");
        if (sessionToken == null || expiresAt == null) return;
        long expiry = LocalDateTime.parse(expiresAt.replace(' ', 'T')).toEpochSecond(ZoneOffset.UTC);
        String access = accessTokens.issue(RowUtils.str(user, "user_id"), sha256(sessionToken), expiry);
        if (access != null) setCookie(response, MusicMvAccessTokenService.COOKIE, access, request,
                Math.min(MusicMvAccessTokenService.TTL_SECONDS, Math.max(0, expiry - Instant.now().getEpochSecond())));
    }

    private Map<String, Object> sessionView(Map<String, Object> user) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("authenticated", Boolean.TRUE);
        // 仅用于界面按登录会话去重，不返回 Cookie 或可用于认证的令牌。
        String sessionId = RowUtils.str(user, "session_id");
        if (sessionId != null) view.put("loginId", sha256("welcome-reminder:" + sessionId));
        String expiresAt = RowUtils.str(user, "expires_at");
        if (expiresAt != null) {
            view.put("expiresAt", LocalDateTime.parse(expiresAt.replace(' ', 'T'))
                    .toInstant(ZoneOffset.UTC).toString());
            view.put("serverTime", Instant.now().toString());
        }
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
        setCookie(response, SESSION_COOKIE, value, request, maxAge);
    }

    private void setCookie(HttpServletResponse response, String name, String value, HttpServletRequest request, long maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
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
