package com.example.cursorquitterweb.musicmv.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.cursorquitterweb.musicmv.service.MusicMvD1SchemaInitializer;
import com.example.cursorquitterweb.musicmv.service.TemplateSyncAuthenticationService;

class MusicMvD1SchemaControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MusicMvD1SchemaController controller = new MusicMvD1SchemaController(
                new TemplateSyncAuthenticationService("sync-only"),
                mock(MusicMvD1SchemaInitializer.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MusicMvExceptionHandler()).build();
    }

    @Test
    void rejectsRendererCredentialOnSchemaInitialization() throws Exception {
        mockMvc.perform(post("/internal/music-mv/v1/templates/schema/initialize")
                        .header("X-Music-Mv-Renderer-Token", "sync-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedDatabaseId\":\"dedicated-d1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TEMPLATE_SYNC_TOKEN"));
    }

    @Test
    void requiresExpectedDatabaseId() throws Exception {
        mockMvc.perform(post("/internal/music-mv/v1/templates/schema/initialize")
                        .header("X-Music-Mv-Template-Sync-Token", "sync-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedDatabaseId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
