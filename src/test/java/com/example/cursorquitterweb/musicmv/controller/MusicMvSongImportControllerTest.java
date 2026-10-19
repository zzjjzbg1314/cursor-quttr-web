package com.example.cursorquitterweb.musicmv.controller;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.HttpStatus;
import com.example.cursorquitterweb.musicmv.service.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class MusicMvSongImportControllerTest {
    @Test void anonymousImportGetsStructured401AndNeverFetchesExternalAudio()throws Exception {
        MusicMvAuthService auth=mock(MusicMvAuthService.class);SunoSongImportService suno=mock(SunoSongImportService.class);MusicMvSongImportService files=mock(MusicMvSongImportService.class);
        when(auth.requireUserId(any())).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Sign in"));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new MusicMvSongImportController(mock(MusicMvRenderClientAuthenticationService.class),auth,files,suno)).setControllerAdvice(new MusicMvExceptionHandler()).build();
        mvc.perform(post("/api/music-mv/v1/songs/import/suno").param("url","https://suno.com/s/example")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mvc.perform(post("/api/music-mv/v1/songs/import/prepare").param("fileName","song.wav").param("sizeBytes","44")).andExpect(status().isUnauthorized());verifyNoInteractions(suno,files);
    }
    @Test void authenticatedImportUsesServerIdentityAndMapsSunoFailure()throws Exception {
        MusicMvAuthService auth=mock(MusicMvAuthService.class);SunoSongImportService suno=mock(SunoSongImportService.class);MusicMvSongImportService files=mock(MusicMvSongImportService.class);
        when(auth.requireUserId(any())).thenReturn("owner");when(suno.importSong(eq("owner"),anyString(),anyString())).thenThrow(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"SUNO_IMPORT_UNAVAILABLE","Use audio file"));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new MusicMvSongImportController(mock(MusicMvRenderClientAuthenticationService.class),auth,files,suno)).setControllerAdvice(new MusicMvExceptionHandler()).build();
        mvc.perform(post("/api/music-mv/v1/songs/import/suno").param("url","https://suno.com/s/example").param("userId","victim")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SUNO_IMPORT_UNAVAILABLE"));verify(suno).importSong(eq("owner"),anyString(),anyString());
    }
}
