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
    void clientAndRendererCredentialsAreIndependent() {
        MusicMvRenderClientAuthenticationService client =
                new MusicMvRenderClientAuthenticationService("client-only");
        RendererAuthenticationService renderer =
                new RendererAuthenticationService("renderer-only");
        TemplateSyncAuthenticationService templateSync =
                new TemplateSyncAuthenticationService("template-sync-only");

        assertDoesNotThrow(() -> client.requireAuthorized("client-only"));
        assertDoesNotThrow(() -> renderer.requireAuthorized("renderer-only"));
        assertDoesNotThrow(() -> templateSync.requireAuthorized("template-sync-only"));

        ApiException clientRejectsRenderer = assertThrows(ApiException.class,
                () -> client.requireAuthorized("renderer-only"));
        ApiException rendererRejectsClient = assertThrows(ApiException.class,
                () -> renderer.requireAuthorized("client-only"));
        ApiException syncRejectsRenderer = assertThrows(ApiException.class,
                () -> templateSync.requireAuthorized("renderer-only"));
        assertEquals("INVALID_MV_RENDER_CLIENT_TOKEN", clientRejectsRenderer.getCode());
        assertEquals("INVALID_RENDERER_TOKEN", rendererRejectsClient.getCode());
        assertEquals("INVALID_TEMPLATE_SYNC_TOKEN", syncRejectsRenderer.getCode());
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
