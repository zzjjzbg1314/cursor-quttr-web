package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicMvAuthenticationIsolationTest {
    @Test
    void clientAndRendererCredentialsAreIndependent() {
        MusicMvRenderClientAuthenticationService client =
                new MusicMvRenderClientAuthenticationService("client-only");
        RendererAuthenticationService renderer =
                new RendererAuthenticationService("renderer-only");

        assertDoesNotThrow(() -> client.requireAuthorized("client-only"));
        assertDoesNotThrow(() -> renderer.requireAuthorized("renderer-only"));

        ApiException clientRejectsRenderer = assertThrows(ApiException.class,
                () -> client.requireAuthorized("renderer-only"));
        ApiException rendererRejectsClient = assertThrows(ApiException.class,
                () -> renderer.requireAuthorized("client-only"));
        assertEquals("INVALID_MV_RENDER_CLIENT_TOKEN", clientRejectsRenderer.getCode());
        assertEquals("INVALID_RENDERER_TOKEN", rendererRejectsClient.getCode());
    }
}
