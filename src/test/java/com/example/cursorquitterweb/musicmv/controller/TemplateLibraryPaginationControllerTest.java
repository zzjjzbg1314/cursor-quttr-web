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

class TemplateLibraryPaginationControllerTest {
    @Test void 分页和摘要都要求凭证并传递服务端筛选() throws Exception {
        MusicMvTemplateCatalogService service=mock(MusicMvTemplateCatalogService.class);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new MusicMvTemplateCatalogController(
                new MusicMvRenderClientAuthenticationService("client"),service))
                .setControllerAdvice(new MusicMvExceptionHandler()).build();
        for(String path:new String[]{"page","overview"})
            mvc.perform(get("/api/music-mv/v1/admin/templates/"+path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        when(service.adminPage(2,"lyrics","published","tpl_123")).thenReturn(Collections.singletonMap("pageSize",20));
        mvc.perform(get("/api/music-mv/v1/admin/templates/page").header("X-Music-Mv-Client-Token","client")
                .param("page","2").param("category","lyrics").param("status","published").param("q","tpl_123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pageSize").value(20));
        when(service.adminOverview()).thenReturn(Collections.singletonMap("total",166));
        mvc.perform(get("/api/music-mv/v1/admin/templates/overview").header("X-Music-Mv-Client-Token","client"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(166));
    }
}
