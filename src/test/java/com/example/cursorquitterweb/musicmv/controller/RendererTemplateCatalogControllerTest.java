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

    @Test void titleSynchronizationRequiresDedicatedToken() throws Exception {
        mockMvc.perform(post("/internal/music-mv/v1/templates/tpl_1/titles/localize"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/music-mv/v1/templates/tpl_1/titles/localize?dryRun=true&expectedRevision=7")
            .header("X-Music-Mv-Template-Sync-Token","sync-only")
            .contentType("application/json").content("{\"titles\":{\"it\":\"Titolo\"},\"expectedSource\":{\"en\":\"Original\",\"zh-CN\":\"原中文\"}}"))
            .andExpect(status().isOk());
    }

    @Test void descriptionSynchronizationRequiresTokenAndRevisionForWrites() throws Exception {
        String path="/internal/music-mv/v1/templates/tpl_1/descriptions/localize";
        mockMvc.perform(post(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).header("X-Music-Mv-Template-Sync-Token","sync-only"))
            .andExpect(status().isOk());
        mockMvc.perform(post(path+"?dryRun=false").header("X-Music-Mv-Template-Sync-Token","sync-only"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TEMPLATE_DESCRIPTION_REVISION_REQUIRED"));
        mockMvc.perform(post(path+"?dryRun=false&expectedRevision=7").header("X-Music-Mv-Template-Sync-Token","sync-only"))
            .andExpect(status().isOk());
    }

    @BeforeEach
    void setUp() {
        RendererTemplateCatalogController controller = new RendererTemplateCatalogController(
                new TemplateSyncAuthenticationService("sync-only"),
                mock(MusicMvTemplateCatalogService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MusicMvExceptionHandler()).build();
    }

    @Test void 精选仅允许同步凭证写入并校验三态() throws Exception {
        String body = "{\"templateId\":\"123456789\",\"status\":\"synced\",\"value\":true,"
                + "\"source\":\"capcut_native_isExportCharge\",\"appVersion\":\"9.3.0\",\"checkedAt\":\"2026-09-01T00:00:00Z\"}";
        String path = "/internal/music-mv/v1/templates/tpl_1/capcut-featured";
        mockMvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).header("X-Music-Mv-Template-Sync-Token", "sync-only")
                .contentType("application/json").content(body)).andExpect(status().isOk());
        mockMvc.perform(post(path).header("X-Music-Mv-Template-Sync-Token", "sync-only")
                .contentType("application/json").content(body.replace("synced", "unknown"))).andExpect(status().isBadRequest());
        mockMvc.perform(post(path).header("X-Music-Mv-Template-Sync-Token", "sync-only")
                .contentType("application/json").content(body.replace("capcut_native_isExportCharge", "manual"))).andExpect(status().isBadRequest());
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
