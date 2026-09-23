package com.example.cursorquitterweb.musicmv.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.cursorquitterweb.musicmv.aimusic.AiVoiceService;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiVoiceControllerTest {
    private final MusicMvAuthService auth = mock(MusicMvAuthService.class);
    private final AiVoiceService voices = mock(AiVoiceService.class);
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new AiVoiceController(voices, auth,
                new MusicMvRenderClientAuthenticationService("")))
                .setControllerAdvice(new MusicMvExceptionHandler()).build();
    }
    @Test void anonymousReadsReturn401WithoutStackTrace() throws Exception {
        when(auth.requireUserId(any())).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Sign in"));
        MockMvc mvc = mvc();
        for (String path : new String[]{"/api/music-mv/v1/voices", "/api/music-mv/v1/voices/missing"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
        verifyNoInteractions(voices);
    }
    @Test void ownedVoiceErrorsKeepTheirStatusWithoutStackTrace() throws Exception {
        when(auth.requireUserId(any())).thenReturn("owner");
        when(voices.get("owner", "foreign")).thenThrow(new ApiException(HttpStatus.NOT_FOUND,"VOICE_NOT_FOUND","Voice not found"));
        mvc().perform(get("/api/music-mv/v1/voices/foreign").header("X-Music-Mv-Client-Id","forged"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VOICE_NOT_FOUND"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        verify(voices).get("owner", "foreign");
    }
    @Test void invalidVoiceRequestReturns400WithoutStackTrace() throws Exception {
        mvc().perform(post("/api/music-mv/v1/voices").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        verifyNoInteractions(voices);
    }
}
