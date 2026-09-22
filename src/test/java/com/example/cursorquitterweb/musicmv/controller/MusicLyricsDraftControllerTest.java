package com.example.cursorquitterweb.musicmv.controller;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.HttpStatus;
import com.example.cursorquitterweb.musicmv.service.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
class MusicLyricsDraftControllerTest {
 @Test void usesSessionOwnerAndRejectsAnonymous() throws Exception {
  MusicMvAuthService auth=mock(MusicMvAuthService.class);MusicLyricsDraftService service=mock(MusicLyricsDraftService.class);
  MockMvc mvc=MockMvcBuilders.standaloneSetup(new MusicLyricsDraftController(auth,new MusicMvRenderClientAuthenticationService(""),service)).setControllerAdvice(new MusicMvExceptionHandler()).build();
  when(auth.requireUserId(any())).thenReturn("session_owner");when(service.list("session_owner")).thenReturn(new ObjectMapper().createObjectNode());
  mvc.perform(get("/api/music-mv/v1/lyric-drafts").header("X-Music-Mv-Client-Id","forged_owner")).andExpect(status().isOk());verify(service).list("session_owner");
  when(auth.requireUserId(any())).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Sign in"));
  mvc.perform(get("/api/music-mv/v1/lyric-drafts")).andExpect(status().isUnauthorized());
 }
}
