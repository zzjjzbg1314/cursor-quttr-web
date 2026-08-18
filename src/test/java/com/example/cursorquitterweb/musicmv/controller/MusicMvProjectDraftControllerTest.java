package com.example.cursorquitterweb.musicmv.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvProjectDraftService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicMvProjectDraftControllerTest {
    private MockMvc mockMvc;
    private MusicMvAuthService auth;
    private MusicMvProjectDraftService projects;

    @BeforeEach
    void setUp() {
        auth = mock(MusicMvAuthService.class);
        projects = mock(MusicMvProjectDraftService.class);
        MusicMvProjectDraftController controller = new MusicMvProjectDraftController(
                new MusicMvRenderClientAuthenticationService(""), auth, projects);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MusicMvExceptionHandler()).build();
    }

    @Test
    void returnsNotFoundEnvelopeAfterProjectWasDeleted() throws Exception {
        when(auth.requireUserId(org.mockito.ArgumentMatchers.any())).thenReturn("usr_project_owner");
        when(projects.get(eq("usr_project_owner"), eq("mvp_deleted_project")))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                        "MUSIC_MV_PROJECT_NOT_FOUND", "Music video project was not found"));

        mockMvc.perform(get("/api/music-mv/v1/projects/mvp_deleted_project"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MUSIC_MV_PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(false));
    }
}
