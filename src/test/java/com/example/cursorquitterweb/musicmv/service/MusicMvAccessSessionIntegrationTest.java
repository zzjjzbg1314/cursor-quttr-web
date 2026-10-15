package com.example.cursorquitterweb.musicmv.service;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.cursorquitterweb.musicmv.repository.MusicMvAuthRepository;
import com.example.cursorquitterweb.musicmv.billing.MusicBillingService;
import com.example.cursorquitterweb.musicmv.billing.MusicBillingController;
import com.example.cursorquitterweb.musicmv.controller.MusicMvAuthController;
import com.example.cursorquitterweb.musicmv.controller.MusicMvExceptionHandler;
import java.util.LinkedHashMap;
import java.util.Map;

class MusicMvAccessSessionIntegrationTest {
    @Test void eachOperationVerifiesSessionAndRevokedPostHasNoSideEffects() throws Exception {
        MusicMvAuthRepository repository = mock(MusicMvAuthRepository.class);
        MusicBillingService billing = mock(MusicBillingService.class);
        MusicMvRenderClientAuthenticationService client = mock(MusicMvRenderClientAuthenticationService.class);
        MusicMvAuthService auth = new MusicMvAuthService(repository, mock(MusicMvOidcIdentityService.class), 30);
        Map<String, Object> user = new LinkedHashMap<String, Object>();
        user.put("user_id", "usr_one"); user.put("session_id", "session_one"); user.put("expires_at", "2035-01-01 00:00:00");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(user);
        Map<String, Object> credits = new LinkedHashMap<String, Object>(); credits.put("remaining", 10);
        when(billing.credits("usr_one")).thenReturn(credits);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MusicMvAuthController(client,auth), new MusicBillingController(billing,auth,client))
                .setControllerAdvice(new MusicMvExceptionHandler()).build();
        Cookie session = new Cookie(MusicMvAuthService.SESSION_COOKIE,"abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG");
        MvcResult refreshed = mvc.perform(get("/api/music-mv/v1/auth/session").cookie(session))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessExpiresAt").doesNotExist()).andReturn();
        verify(repository,times(1)).findBySessionTokenHash(anyString()); clearInvocations(repository);
        String cookie = refreshed.getResponse().getHeader("Set-Cookie");
        Cookie access = new Cookie("music_mv_access",cookie.substring(cookie.indexOf('=')+1,cookie.indexOf(';')));
        mvc.perform(get("/api/music-mv/v1/billing/credits").cookie(session,access)).andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(10));
        verify(repository,atLeastOnce()).findBySessionTokenHash(anyString());
        when(repository.findBySessionTokenHash(anyString())).thenReturn(null);
        mvc.perform(post("/api/music-mv/v1/billing/checkout").cookie(session).contentType("application/json").content("{\"plan\":\"starter\",\"locale\":\"en\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        verify(billing,never()).checkout(anyString(),anyString(),anyString()); verify(repository,atLeastOnce()).findBySessionTokenHash(anyString());
        // 遗留短期 Cookie 不能绕过长期会话撤销。
        mvc.perform(get("/api/music-mv/v1/billing/credits").cookie(new Cookie(MusicMvAuthService.SESSION_COOKIE,"another-account-long-session-token-123456"),access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        verify(billing,times(1)).credits("usr_one");
        when(repository.findBySessionTokenHash(anyString())).thenReturn(null);
        mvc.perform(get("/api/music-mv/v1/auth/session").cookie(session,access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age=0")));
    }
}
