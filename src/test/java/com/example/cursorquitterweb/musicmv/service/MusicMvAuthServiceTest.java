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
}
