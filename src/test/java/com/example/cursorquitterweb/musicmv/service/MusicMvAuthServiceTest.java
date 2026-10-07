package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.cursorquitterweb.musicmv.repository.MusicMvAuthRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicMvAuthServiceTest {
    private static final String LONG_TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static String tokenHash() {
        try { byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(LONG_TOKEN.getBytes(java.nio.charset.StandardCharsets.UTF_8)); StringBuilder value = new StringBuilder(); for(byte item: hash) value.append(String.format("%02x", item & 255)); return value.toString(); } catch(Exception e) { throw new RuntimeException(e); }
    }
    private static MusicMvAccessTokenService access() { return new MusicMvAccessTokenService("test-key-with-at-least-32-random-bytes"); }
    private static org.springframework.mock.web.MockHttpServletResponse response() { return new org.springframework.mock.web.MockHttpServletResponse(); }
    @Test
    void anonymousWorkspaceRemainsAvailableBeforeSignIn() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        MusicMvAuthService service = new MusicMvAuthService(repository,
                mock(MusicMvOidcIdentityService.class), 30, access());

        assertEquals("web_12345678", service.effectiveClientId(
                new MockHttpServletRequest(), "web_12345678"));
    }

    @Test
    void reservedUserWorkspaceCannotBeForgedWithoutSession() {
        MusicMvAuthService service = new MusicMvAuthService(mock(MusicMvAuthRepository.class),
                mock(MusicMvOidcIdentityService.class), 30, access());

        ApiException error = assertThrows(ApiException.class,
                () -> service.effectiveClientId(new MockHttpServletRequest(), "usr_12345678"));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        assertEquals("MUSIC_MV_LOGIN_REQUIRED", error.getCode());
    }

    @Test
    void signedInSessionOverridesCallerSuppliedWorkspace() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        Map<String, Object> user = new LinkedHashMap<String, Object>();
        user.put("user_id", "usr_signed_in");
        user.put("session_id", "session_1");
        user.put("expires_at", "2035-01-01 00:00:00");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        MusicMvAuthService service = new MusicMvAuthService(repository,
                mock(MusicMvOidcIdentityService.class), 30, access());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE,
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));

        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE, LONG_TOKEN), new javax.servlet.http.Cookie(MusicMvAccessTokenService.COOKIE, access().issue("usr_signed_in", "session_1", java.time.Instant.now().getEpochSecond()+3600, tokenHash()).token));
        assertEquals("usr_signed_in", service.effectiveClientId(request, "web_attacker1"));
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void protectedMusicApiRequiresSignedInSession() {
        MusicMvAuthService service = new MusicMvAuthService(mock(MusicMvAuthRepository.class),
                mock(MusicMvOidcIdentityService.class), 30, access());

        ApiException error = assertThrows(ApiException.class,
                () -> service.requireUserId(new MockHttpServletRequest()));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        assertEquals("AUTH_REQUIRED", error.getCode());
    }

    @Test
    void protectedMusicApiUsesSessionUserOnly() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        Map<String, Object> user = new LinkedHashMap<String, Object>();
        user.put("user_id", "usr_signed_in");
        user.put("session_id", "session_1");
        user.put("expires_at", "2035-01-01 00:00:00");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        MusicMvAuthService service = new MusicMvAuthService(repository,
                mock(MusicMvOidcIdentityService.class), 30, access());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE,
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));

        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE, LONG_TOKEN), new javax.servlet.http.Cookie(MusicMvAccessTokenService.COOKIE, access().issue("usr_signed_in", "session_1", java.time.Instant.now().getEpochSecond()+3600, tokenHash()).token));
        assertEquals("usr_signed_in", service.requireUserId(request));
        org.mockito.Mockito.verifyNoInteractions(repository);
    }
    @Test
    void sessionActivityWritesOnlyWhenDueAndRevocationStillTakesEffect() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        Map<String, Object> user = new LinkedHashMap<String, Object>();
        user.put("user_id", "usr_signed_in");
        user.put("session_id", "session_1");
        user.put("expires_at", "2035-01-01 00:00:00");
        user.put("session_touch_due", 0);
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        MusicMvAuthService service = new MusicMvAuthService(repository,
                mock(MusicMvOidcIdentityService.class), 30, access());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE,
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));
        assertEquals(Boolean.TRUE, service.currentSession(request, response()).get("authenticated"));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).touchSession(anyString());
        user.put("session_touch_due", 1);
        service.currentSession(request, response());
        org.mockito.Mockito.verify(repository).touchSession("session_1");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(null);
        assertEquals(Boolean.FALSE, service.currentSession(request, response()).get("authenticated"));
        assertThrows(ApiException.class, () -> service.requireUserId(request));
    }

    @Test
    void sessionResponseIncludesUtcExpiryWithoutRenewingIt() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        Map<String, Object> user = new LinkedHashMap<String, Object>();
        user.put("user_id", "usr_signed_in");
        user.put("session_id", "login-one");
        user.put("expires_at", "2026-10-28 12:00:00");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        MusicMvAuthService service = new MusicMvAuthService(repository, mock(MusicMvOidcIdentityService.class), 30, access());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE,
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));
        Map<String, Object> response = service.currentSession(request, response());
        assertEquals("2026-10-28T12:00:00Z", response.get("expiresAt"));
        String loginId = (String) response.get("loginId");
        org.junit.jupiter.api.Assertions.assertTrue(loginId.matches("[a-f0-9]{64}"));
        assertEquals(loginId, service.currentSession(request, response()).get("loginId"));
        user.put("session_id", "login-two");
        org.junit.jupiter.api.Assertions.assertNotEquals(loginId, service.currentSession(request, response()).get("loginId"));
        org.junit.jupiter.api.Assertions.assertNotNull(java.time.Instant.parse((String) response.get("serverTime")));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).createSession(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void distinguishesInvalidSessionFromFirstAnonymousVisit() {
        MusicMvAuthService service = new MusicMvAuthService(mock(MusicMvAuthRepository.class), mock(MusicMvOidcIdentityService.class), 30, access());
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(null, service.currentSession(request, response()).get("reason"));
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE, "invalid"));
        assertEquals("session_ended", service.currentSession(request, response()).get("reason"));
    }

    @Test
    void logoutRevokesOnlyCurrentTokenAndClearsSecureCookie() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        MusicMvAuthService service = new MusicMvAuthService(repository, mock(MusicMvOidcIdentityService.class), 30, access());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        String token = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE, token));
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        service.logout(request, response);
        org.mockito.ArgumentCaptor<String> hash = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(repository).revokeSession(hash.capture());
        assertEquals(64, hash.getValue().length());
        String cookie = response.getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertTrue(cookie.contains("Max-Age=0") && cookie.contains("HttpOnly") && cookie.contains("Secure") && cookie.contains("SameSite=Lax"));
    }

    @Test
    void loginReturnsStoredSessionExpiryAndSetsThirtyDayCookie() throws Exception {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        MusicMvOidcIdentityService oidc = mock(MusicMvOidcIdentityService.class);
        java.lang.reflect.Constructor<MusicMvOidcIdentityService.VerifiedIdentity> constructor =
                MusicMvOidcIdentityService.VerifiedIdentity.class.getDeclaredConstructor(String.class, String.class, String.class, boolean.class, String.class, String.class);
        constructor.setAccessible(true);
        MusicMvOidcIdentityService.VerifiedIdentity identity = constructor.newInstance("google", "subject", null, false, "QA", null);
        when(oidc.verify("google", "id-token", null)).thenReturn(identity);
        Map<String, Object> user = new LinkedHashMap<String, Object>(); user.put("user_id", "usr_test");
        when(repository.findByIdentity("google", "subject")).thenReturn(user);
        Map<String, Object> session = new LinkedHashMap<String, Object>(user); session.put("session_id", "session_1"); session.put("expires_at", "2026-10-28 12:00:00");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(session);
        MusicMvAuthService service = new MusicMvAuthService(repository, oidc, 30, access());
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        Map<String, Object> result = service.login("google", "id-token", null, null, new MockHttpServletRequest(), response);
        assertEquals("2026-10-28T12:00:00Z", result.get("expiresAt"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=2592000"));
        org.mockito.Mockito.verify(repository).createSession(anyString(), org.mockito.ArgumentMatchers.eq("usr_test"), anyString(), org.mockito.ArgumentMatchers.eq(30));
        when(repository.findBySessionTokenHash(anyString())).thenReturn(null);
        org.springframework.mock.web.MockHttpServletResponse rejectedResponse = new org.springframework.mock.web.MockHttpServletResponse();
        assertThrows(ApiException.class, () -> service.login("google", "id-token", null, null, new MockHttpServletRequest(), rejectedResponse));
        assertEquals(null, rejectedResponse.getHeader("Set-Cookie"));
    }
    @Test
    void longCookieNeedsRefreshAndCannotExecuteProtectedBusiness() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        MusicMvAuthService service = new MusicMvAuthService(repository, mock(MusicMvOidcIdentityService.class), 30, access());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE, "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));
        assertEquals("AUTH_ACCESS_EXPIRED", assertThrows(ApiException.class, () -> service.requireUserId(request)).getCode());
        assertEquals("AUTH_ACCESS_EXPIRED", assertThrows(ApiException.class, () -> service.effectiveClientId(request, "web_attacker1")).getCode());
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void sessionRefreshIssuesSecureAccessAndLogoutClearsBothCookies() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        Map<String, Object> user = new LinkedHashMap<String, Object>();
        user.put("user_id", "usr_one"); user.put("session_id", "session_one"); user.put("expires_at", "2035-01-01 00:00:00");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        MusicMvAuthService service = new MusicMvAuthService(repository, mock(MusicMvOidcIdentityService.class), 30, access());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE, "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));
        org.springframework.mock.web.MockHttpServletResponse refreshed = response();
        Map<String, Object> view = service.currentSession(request, refreshed);
        String cookie = refreshed.getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertTrue(cookie.startsWith("music_mv_access=") && cookie.contains("HttpOnly") && cookie.contains("Secure") && cookie.contains("SameSite=Lax") && cookie.contains("Max-Age=120"));
        org.junit.jupiter.api.Assertions.assertNotNull(java.time.Instant.parse((String) view.get("accessExpiresAt")));
        String raw = cookie.substring(cookie.indexOf('=')+1, cookie.indexOf(';'));
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE, LONG_TOKEN), new javax.servlet.http.Cookie(MusicMvAccessTokenService.COOKIE, raw));
        org.mockito.Mockito.clearInvocations(repository);
        assertEquals("usr_one", service.requireUserId(request));
        org.mockito.Mockito.verifyNoInteractions(repository);
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE, "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));
        org.springframework.mock.web.MockHttpServletResponse cleared = response(); service.logout(request, cleared);
        assertEquals(2, cleared.getHeaders("Set-Cookie").size());
        for (String value : cleared.getHeaders("Set-Cookie")) org.junit.jupiter.api.Assertions.assertTrue(value.contains("Max-Age=0"));
        when(repository.findBySessionTokenHash(anyString())).thenReturn(null);
        assertEquals(Boolean.FALSE, service.currentSession(request, response()).get("authenticated"));
    }

    @Test
    void downloadFallbackVerifiesLongSessionAndRejectsRevokedOwner() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        MusicMvAuthService service = new MusicMvAuthService(repository, mock(MusicMvOidcIdentityService.class), 30, access());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE, LONG_TOKEN));
        Map<String, Object> owner = new LinkedHashMap<String, Object>(); owner.put("user_id", "usr_one");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(owner);
        assertEquals("usr_one", service.requireDownloadUserId(request));
        when(repository.findBySessionTokenHash(anyString())).thenReturn(null);
        assertThrows(ApiException.class, () -> service.requireDownloadUserId(request));
    }

}
