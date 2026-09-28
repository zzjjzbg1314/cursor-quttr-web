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
    @Test
    void anonymousWorkspaceRemainsAvailableBeforeSignIn() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        MusicMvAuthService service = new MusicMvAuthService(repository,
                mock(MusicMvOidcIdentityService.class), 30);

        assertEquals("web_12345678", service.effectiveClientId(
                new MockHttpServletRequest(), "web_12345678"));
    }

    @Test
    void reservedUserWorkspaceCannotBeForgedWithoutSession() {
        MusicMvAuthService service = new MusicMvAuthService(mock(MusicMvAuthRepository.class),
                mock(MusicMvOidcIdentityService.class), 30);

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
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        MusicMvAuthService service = new MusicMvAuthService(repository,
                mock(MusicMvOidcIdentityService.class), 30);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE,
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));

        assertEquals("usr_signed_in", service.effectiveClientId(request, "web_attacker1"));
    }

    @Test
    void protectedMusicApiRequiresSignedInSession() {
        MusicMvAuthService service = new MusicMvAuthService(mock(MusicMvAuthRepository.class),
                mock(MusicMvOidcIdentityService.class), 30);

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
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        MusicMvAuthService service = new MusicMvAuthService(repository,
                mock(MusicMvOidcIdentityService.class), 30);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE,
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));

        assertEquals("usr_signed_in", service.requireUserId(request));
    }
    @Test
    void sessionActivityWritesOnlyWhenDueAndRevocationStillTakesEffect() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        Map<String, Object> user = new LinkedHashMap<String, Object>();
        user.put("user_id", "usr_signed_in");
        user.put("session_id", "session_1");
        user.put("session_touch_due", 0);
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        MusicMvAuthService service = new MusicMvAuthService(repository,
                mock(MusicMvOidcIdentityService.class), 30);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE,
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));
        assertEquals(Boolean.TRUE, service.currentSession(request).get("authenticated"));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).touchSession(anyString());
        user.put("session_touch_due", 1);
        service.currentSession(request);
        org.mockito.Mockito.verify(repository).touchSession("session_1");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(null);
        assertEquals(Boolean.FALSE, service.currentSession(request).get("authenticated"));
        assertThrows(ApiException.class, () -> service.requireUserId(request));
    }

    @Test
    void sessionResponseIncludesUtcExpiryWithoutRenewingIt() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        Map<String, Object> user = new LinkedHashMap<String, Object>();
        user.put("user_id", "usr_signed_in");
        user.put("expires_at", "2026-10-28 12:00:00");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        MusicMvAuthService service = new MusicMvAuthService(repository, mock(MusicMvOidcIdentityService.class), 30);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE,
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));
        Map<String, Object> response = service.currentSession(request);
        assertEquals("2026-10-28T12:00:00Z", response.get("expiresAt"));
        org.junit.jupiter.api.Assertions.assertNotNull(java.time.Instant.parse((String) response.get("serverTime")));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).createSession(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void distinguishesInvalidSessionFromFirstAnonymousVisit() {
        MusicMvAuthService service = new MusicMvAuthService(mock(MusicMvAuthRepository.class), mock(MusicMvOidcIdentityService.class), 30);
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(null, service.currentSession(request).get("reason"));
        request.setCookies(new javax.servlet.http.Cookie(MusicMvAuthService.SESSION_COOKIE, "invalid"));
        assertEquals("session_ended", service.currentSession(request).get("reason"));
    }

    @Test
    void logoutRevokesOnlyCurrentTokenAndClearsSecureCookie() {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        MusicMvAuthService service = new MusicMvAuthService(repository, mock(MusicMvOidcIdentityService.class), 30);
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
        Map<String, Object> session = new LinkedHashMap<String, Object>(user); session.put("expires_at", "2026-10-28 12:00:00");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(session);
        MusicMvAuthService service = new MusicMvAuthService(repository, oidc, 30);
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
}
