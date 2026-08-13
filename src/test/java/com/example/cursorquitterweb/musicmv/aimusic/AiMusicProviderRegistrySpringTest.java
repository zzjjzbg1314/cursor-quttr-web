package com.example.cursorquitterweb.musicmv.aimusic;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

class AiMusicProviderRegistrySpringTest {
    @Test
    void springCreatesAllEnabledProvidersAndTheRegistry() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "music-mv.enabled=true",
                    "music-mv.ai-music.sunoapi.enabled=true",
                    "music-mv.ai-music.kie.enabled=true");
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(SunoApiAiMusicProvider.class);
            context.registerBean(KieAiMusicProvider.class);
            context.registerBean(AiMusicProviderRegistry.class);
            context.refresh();

            AiMusicProviderRegistry registry = context.getBean(AiMusicProviderRegistry.class);
            assertNotNull(registry.require("sunoapi"));
            assertNotNull(registry.require("kie"));
        }
    }
}
