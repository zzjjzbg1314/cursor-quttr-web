package com.example.cursorquitterweb.musicmv.controller;

import static org.mockito.Mockito.mock;
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

class MusicMvInputAssetControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MusicMvInputAssetController controller = new MusicMvInputAssetController(
                new MusicMvRenderClientAuthenticationService("isolated-client-token"),
                mock(MusicMvAuthService.class),
                mock(MusicMvInputAssetStorageService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MusicMvExceptionHandler()).build();
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
