package com.example.cursorquitterweb.musicmv.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.cursorquitterweb.musicmv.service.MusicMvTemplateCatalogService;
import com.example.cursorquitterweb.musicmv.service.TemplateSyncAuthenticationService;

class RendererTemplateCatalogControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RendererTemplateCatalogController controller = new RendererTemplateCatalogController(
                new TemplateSyncAuthenticationService("sync-only"),
                mock(MusicMvTemplateCatalogService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MusicMvExceptionHandler()).build();
    }

    @Test
    void rejectsRendererTokenOnDedicatedTemplateSyncPath() throws Exception {
        mockMvc.perform(get("/internal/music-mv/v1/templates/migration-readiness")
                        .header("X-Music-Mv-Renderer-Token", "sync-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TEMPLATE_SYNC_TOKEN"));
    }

    @Test
    void rejectsOldSharedTemplateHeader() throws Exception {
        mockMvc.perform(get("/internal/music-mv/v1/templates/migration-readiness")
                        .header("X-Renderer-Token", "sync-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TEMPLATE_SYNC_TOKEN"));
    }
}
