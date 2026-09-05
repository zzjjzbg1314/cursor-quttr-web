package com.example.cursorquitterweb.musicmv.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void syncCleanupRequiresDedicatedCredentialAndValidManifest() throws Exception {
        String body = "{\"manifestSha256\":\"" + new String(new char[64]).replace('\0', 'a')
                + "\",\"mediaRoles\":[\"cover\",\"browser_parity_reference\"]}";
        mockMvc.perform(post("/internal/music-mv/v1/templates/tpl_1/versions/tplver_1/sync-complete")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/music-mv/v1/templates/tpl_1/versions/tplver_1/sync-complete")
                        .header("X-Music-Mv-Template-Sync-Token", "sync-only")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/music-mv/v1/templates/tpl_1/versions/tplver_1/sync-complete")
                        .header("X-Music-Mv-Template-Sync-Token", "sync-only")
                        .contentType("application/json").content("{\"mediaRoles\":[]}"))
                .andExpect(status().isBadRequest());
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
