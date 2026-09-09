package com.example.cursorquitterweb.musicmv.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import com.example.cursorquitterweb.musicmv.service.MusicMvTemplateCatalogService;

class MusicMvHistoricalTemplateControllerTest {
    @Test
    void bindsBothPathIdentifiersAndRequiresClientAuthentication() throws Exception {
        MusicMvTemplateCatalogService service = mock(MusicMvTemplateCatalogService.class);
        when(service.publishedVersionDetail("tpl_1", "accepted"))
                .thenReturn(Collections.singletonMap("selectedVersion", "accepted"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MusicMvTemplateCatalogController(
                new MusicMvRenderClientAuthenticationService("test-token"), service))
                .setControllerAdvice(new MusicMvExceptionHandler()).build();
        mvc.perform(get("/api/music-mv/v1/templates/tpl_1/versions/accepted"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        mvc.perform(get("/api/music-mv/v1/templates/tpl_1/versions/accepted")
                .header("X-Music-Mv-Client-Token", "test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selectedVersion").value("accepted"));
        verify(service).publishedVersionDetail("tpl_1", "accepted");
    }
}
