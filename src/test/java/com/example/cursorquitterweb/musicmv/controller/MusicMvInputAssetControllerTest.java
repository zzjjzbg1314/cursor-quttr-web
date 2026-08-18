package com.example.cursorquitterweb.musicmv.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import com.example.cursorquitterweb.musicmv.service.MusicMvUserAssetService;

class MusicMvInputAssetControllerTest {
    private MockMvc mockMvc;
    private MusicMvInputAssetStorageService storage;

    @BeforeEach
    void setUp() {
        storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvInputAssetController controller = new MusicMvInputAssetController(
                new MusicMvRenderClientAuthenticationService("isolated-client-token"),
                mock(MusicMvAuthService.class),
                storage,
                mock(MusicMvUserAssetService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MusicMvExceptionHandler()).build();
    }

    @Test
    void reportsCloudUploadReadinessWithoutExposingStorageCredentials() throws Exception {
        when(storage.isCloudStorageConfigured()).thenReturn(true);
        when(storage.isCloudStorageRequired()).thenReturn(true);

        mockMvc.perform(get("/api/music-mv/v1/assets/readiness")
                        .header("X-Music-Mv-Client-Token", "isolated-client-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storage").value("r2"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.uploadAvailable").value(true))
                .andExpect(jsonPath("$.bucket").doesNotExist());
    }

    @Test
    void rejectsUploadWithoutDedicatedClientToken() throws Exception {
        mockMvc.perform(post("/api/music-mv/v1/assets")
                        .param("kind", "music")
                        .param("fileName", "song.mp3")
                        .param("sizeBytes", "3")
                        .header("X-Music-Mv-Client-Id", "website-user-1")
                        .contentType("audio/mpeg")
                        .content(new byte[] {1, 2, 3}))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MV_RENDER_CLIENT_TOKEN"));
    }
}
