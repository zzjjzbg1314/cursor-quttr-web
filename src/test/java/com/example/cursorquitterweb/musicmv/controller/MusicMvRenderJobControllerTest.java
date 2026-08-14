package com.example.cursorquitterweb.musicmv.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderJobService;

class MusicMvRenderJobControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MusicMvRenderClientAuthenticationService authentication =
                new MusicMvRenderClientAuthenticationService("isolated-client-token");
        MusicMvRenderJobController controller = new MusicMvRenderJobController(
                authentication, mock(MusicMvAuthService.class), mock(MusicMvRenderJobService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MusicMvExceptionHandler())
                .build();
    }

    @Test
    void rejectsMissingDedicatedClientTokenOnDedicatedPath() throws Exception {
        mockMvc.perform(get("/api/music-mv/v1/render-jobs/mvr_1")
                        .header("X-Music-Mv-Client-Id", "website-backend"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MV_RENDER_CLIENT_TOKEN"));
    }

    @Test
    void doesNotAcceptExistingBackendAuthorizationHeaderAsMusicMvCredential() throws Exception {
        mockMvc.perform(get("/api/music-mv/v1/render-jobs/mvr_1")
                        .header("Authorization", "Bearer isolated-client-token")
                        .header("X-Music-Mv-Client-Id", "website-backend"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MV_RENDER_CLIENT_TOKEN"));
    }
}
