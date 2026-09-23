package com.example.cursorquitterweb.musicmv.controller;
import com.example.cursorquitterweb.musicmv.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MusicLyricsChatControllerTest {
 @Test void requiresServiceTokenAndUserBeforeCallingModel(){
  MusicLyricsChatService service=mock(MusicLyricsChatService.class);MusicMvAuthService auth=mock(MusicMvAuthService.class);MusicMvRenderClientAuthenticationService token=mock(MusicMvRenderClientAuthenticationService.class);
  HttpServletRequest request=mock(HttpServletRequest.class);when(auth.requireUserId(request)).thenThrow(new IllegalStateException("unauthenticated"));
  MusicLyricsChatController controller=new MusicLyricsChatController(service,auth,token);
  assertThrows(IllegalStateException.class,()->controller.chat("token",new ObjectMapper().createObjectNode(),request));verify(token).requireAuthorized("token");verifyNoInteractions(service);
 }
 @Test void unauthenticatedRequestReturns401() throws Exception {
  MusicLyricsChatService service=mock(MusicLyricsChatService.class);
  MusicMvAuthService auth=mock(MusicMvAuthService.class);
  MusicMvRenderClientAuthenticationService token=mock(MusicMvRenderClientAuthenticationService.class);
  when(auth.requireUserId(any())).thenThrow(new com.example.cursorquitterweb.musicmv.support.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,"SIGN_IN_REQUIRED","Sign in"));
  org.springframework.test.web.servlet.MockMvc mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new MusicLyricsChatController(service,auth,token)).setControllerAdvice(new MusicMvExceptionHandler()).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/music-mv/v1/lyrics-chat").contentType("application/json").content("{\"message\":\"hello\"}"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("SIGN_IN_REQUIRED"));
  verifyNoInteractions(service);
 }
}
