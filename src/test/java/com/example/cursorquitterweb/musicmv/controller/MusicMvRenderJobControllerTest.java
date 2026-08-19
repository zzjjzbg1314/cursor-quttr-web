package com.example.cursorquitterweb.musicmv.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private MusicMvAuthService auth;
    private MusicMvRenderJobService service;

    @BeforeEach
    void setUp() {
        MusicMvRenderClientAuthenticationService authentication =
                new MusicMvRenderClientAuthenticationService("isolated-client-token");
        auth = mock(MusicMvAuthService.class);
        service = mock(MusicMvRenderJobService.class);
        MusicMvRenderJobController controller = new MusicMvRenderJobController(
                authentication, auth, service);
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

    @Test
    void deletesOnlyThroughAuthenticatedUserScope() throws Exception {
        when(auth.requireUserId(any())).thenReturn("usr_owner");
        java.util.Map<String, Object> response = new java.util.LinkedHashMap<String, Object>();
        response.put("jobId", "mvr_completed");
        response.put("deleted", Boolean.TRUE);
        when(service.delete("usr_owner", "mvr_completed")).thenReturn(response);

        mockMvc.perform(delete("/api/music-mv/v1/render-jobs/mvr_completed")
                        .header("X-Music-Mv-Client-Token", "isolated-client-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(service).delete("usr_owner", "mvr_completed");
    }
}
