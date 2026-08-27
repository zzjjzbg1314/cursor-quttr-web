package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicMvAuthenticationIsolationTest {
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void clientAndTemplateSyncCredentialsAreIndependent() {
        MusicMvRenderClientAuthenticationService client =
                new MusicMvRenderClientAuthenticationService("client-only");
        TemplateSyncAuthenticationService templateSync =
                new TemplateSyncAuthenticationService("template-sync-only");

        assertDoesNotThrow(() -> client.requireAuthorized("client-only"));
        assertDoesNotThrow(() -> templateSync.requireAuthorized("template-sync-only"));

        ApiException clientRejectsSync = assertThrows(ApiException.class,
                () -> client.requireAuthorized("template-sync-only"));
        ApiException syncRejectsClient = assertThrows(ApiException.class,
                () -> templateSync.requireAuthorized("client-only"));
        assertEquals("INVALID_MV_RENDER_CLIENT_TOKEN", clientRejectsSync.getCode());
        assertEquals("INVALID_TEMPLATE_SYNC_TOKEN", syncRejectsClient.getCode());
    }

    @Test
    void missingManualTokensAllowsLoopbackButRequiresRemotePairing() {
        TemplateSyncAuthenticationService templateSync =
                new TemplateSyncAuthenticationService("");

        MockHttpServletRequest local = new MockHttpServletRequest();
        local.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(local));
        assertDoesNotThrow(() -> templateSync.requireAuthorized(null));

        MockHttpServletRequest remote = new MockHttpServletRequest();
        remote.setRemoteAddr("203.0.113.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(remote));
        ApiException rejected = assertThrows(ApiException.class,
                () -> templateSync.requireAuthorized(null));
        assertEquals("TEMPLATE_SYNC_PAIRING_REQUIRED", rejected.getCode());
    }
}
