package com.example.cursorquitterweb.musicmv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.example.cursorquitterweb.musicmv.controller.MusicMvRenderJobController;
import com.example.cursorquitterweb.musicmv.controller.MusicMvTemplateCatalogController;
import com.example.cursorquitterweb.musicmv.controller.AiMusicSongController;
import com.example.cursorquitterweb.musicmv.aimusic.KieAiMusicProvider;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderJobService;
import com.example.cursorquitterweb.musicmv.service.MusicMvTemplateCatalogService;

class MusicMvModuleDisabledTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("music-mv.enabled=false");

    @Test
    void disabledModuleRegistersNoControllerRepositoryServiceOrRoute() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MusicMvRenderJobController.class);
            assertThat(context).doesNotHaveBean(AiMusicSongController.class);
            assertThat(context).doesNotHaveBean(KieAiMusicProvider.class);
            assertThat(context).doesNotHaveBean(MusicMvRenderJobService.class);
            assertThat(context).doesNotHaveBean(MusicMvTemplateCatalogController.class);
            assertThat(context).doesNotHaveBean(MusicMvTemplateCatalogService.class);
            assertThat(context).doesNotHaveBean(MusicMvTemplateCatalogRepository.class);
            assertThat(context).doesNotHaveBean(MusicMvRenderJobRepository.class);
            assertThat(context).doesNotHaveBean(D1DatabaseClient.class);

            RequestMappingHandlerMapping mappings = context.getBean(RequestMappingHandlerMapping.class);
            for (Map.Entry<?, ?> entry : mappings.getHandlerMethods().entrySet()) {
                String route = String.valueOf(entry.getKey());
                assertThat(route).doesNotContain("/api/music-mv/");
                assertThat(route).doesNotContain("/internal/music-mv/");
            }
        });
    }

    @Configuration
    @EnableWebMvc
    @ComponentScan(basePackages = "com.example.cursorquitterweb.musicmv")
    static class TestConfiguration {
    }
}
