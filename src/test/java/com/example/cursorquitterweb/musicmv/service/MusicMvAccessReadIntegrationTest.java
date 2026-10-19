package com.example.cursorquitterweb.musicmv.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import javax.servlet.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import com.example.cursorquitterweb.musicmv.repository.MusicMvAuthRepository;
import com.example.cursorquitterweb.musicmv.controller.MusicMvExceptionHandler;

class MusicMvAccessReadIntegrationTest {
    @RestController static class PrivateApi {
        private final MusicMvAuthService auth;
        PrivateApi(MusicMvAuthService auth) { this.auth=auth; }
        @GetMapping("/api/music-mv/v1/test-private") String read(HttpServletRequest request) { return auth.requireUserId(request); }
        @PostMapping("/api/music-mv/v1/test-private") String write(HttpServletRequest request) { return auth.requireUserId(request); }
    }
    @RestControllerAdvice(assignableTypes=PrivateApi.class) static class Errors {
        @ExceptionHandler(com.example.cursorquitterweb.musicmv.support.ApiException.class)
        org.springframework.http.ResponseEntity<String> error(com.example.cursorquitterweb.musicmv.support.ApiException exception) {
            return org.springframework.http.ResponseEntity.status(exception.getStatus()).body(exception.getCode());
        }
    }
    private Cookie access(MvcResult result) {
        String header=result.getResponse().getHeaders("Set-Cookie").stream()
            .filter(value -> value.startsWith(MusicMvAccessTokenService.COOKIE+"=")).findFirst().get();
        assertTrue(header.contains("HttpOnly")); assertTrue(header.contains("SameSite=Lax"));
        return new Cookie(MusicMvAccessTokenService.COOKIE,header.substring(header.indexOf('=')+1,header.indexOf(';')));
    }
    @Test void renewsInOriginalRequestThenReadsWithoutDatabaseAndWritesRevalidate() throws Exception {
        MusicMvAuthRepository repository=mock(MusicMvAuthRepository.class);
        MusicMvAuthService auth=new MusicMvAuthService(repository,mock(MusicMvOidcIdentityService.class),30);
        Map<String,Object> user=new HashMap<>();user.put("user_id","usr_one");user.put("expires_at","2035-01-01 00:00:00");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new PrivateApi(auth)).setControllerAdvice(new Errors()).build();
        Cookie session=new Cookie(MusicMvAuthService.SESSION_COOKIE,"abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG");
        Cookie access=access(mvc.perform(get("/api/music-mv/v1/test-private").cookie(session))
            .andExpect(status().isOk()).andExpect(content().string("usr_one")).andReturn());
        verify(repository,times(1)).findBySessionTokenHash(anyString());clearInvocations(repository);
        mvc.perform(get("/api/music-mv/v1/test-private").cookie(session,access)).andExpect(status().isOk());
        verifyNoInteractions(repository);
        com.example.cursorquitterweb.musicmv.aimusic.AiMusicGenerationService songs = mock(com.example.cursorquitterweb.musicmv.aimusic.AiMusicGenerationService.class);
        when(songs.list(eq("usr_one"), any(), any(), any(), any(), any())).thenReturn(Collections.singletonMap("items", Collections.emptyList()));
        MockMvc songMvc = MockMvcBuilders.standaloneSetup(new com.example.cursorquitterweb.musicmv.controller.AiMusicSongController(
            mock(MusicMvRenderClientAuthenticationService.class), auth, songs,
            mock(MusicMvInputAssetStorageService.class), mock(com.example.cursorquitterweb.musicmv.aimusic.AiVoiceService.class)))
            .setControllerAdvice(new MusicMvExceptionHandler()).build();
        songMvc.perform(get("/api/music-mv/v1/songs").cookie(session,access).header("X-Music-Mv-Client-Id","usr_attacker"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verify(songs).list(eq("usr_one"), any(), any(), any(), any(), any());
        verifyNoInteractions(repository);
        when(repository.findBySessionTokenHash(anyString())).thenReturn(null);
        mvc.perform(post("/api/music-mv/v1/test-private").cookie(session,access)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/music-mv/v1/test-private").cookie(access)).andExpect(status().isUnauthorized());
        Cookie other=new Cookie(MusicMvAuthService.SESSION_COOKIE,"another-account-long-session-token-123456");
        mvc.perform(get("/api/music-mv/v1/test-private").cookie(other,access)).andExpect(status().isUnauthorized());
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        access(mvc.perform(get("/api/music-mv/v1/test-private").cookie(session,new Cookie(MusicMvAccessTokenService.COOKIE,"expired-or-invalid")))
            .andExpect(status().isOk()).andReturn());
        org.springframework.mock.web.MockHttpServletResponse response=new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockHttpServletRequest request=new org.springframework.mock.web.MockHttpServletRequest();request.setCookies(session,access);
        auth.logout(request,response);
        assertTrue(response.getHeaders("Set-Cookie").stream().anyMatch(value -> value.startsWith(MusicMvAccessTokenService.COOKIE+"=") && value.contains("Max-Age=0")));
        verify(repository).revokeSession(anyString());
    }
}
