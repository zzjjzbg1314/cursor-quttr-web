package com.example.cursorquitterweb.musicmv.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Keeps the isolated module off by default while making the five-variable
 * IntelliJ/server configuration sufficient. Explicit enable flags always win.
 */
public final class MusicMvAutoEnableEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {
    static final String PROPERTY_SOURCE_NAME = "musicMvDerivedEnablement";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        String accountId = trim(environment.getProperty("MUSIC_MV_CLOUDFLARE_ACCOUNT_ID"));
        String databaseId = trim(environment.getProperty("MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID"));
        String apiToken = trim(environment.getProperty("MUSIC_MV_CLOUDFLARE_API_TOKEN"));
        if (accountId.isEmpty() || databaseId.isEmpty() || apiToken.isEmpty()) {
            return;
        }
        Map<String, Object> derived = new LinkedHashMap<String, Object>();
        if (trim(environment.getProperty("MUSIC_MV_ENABLED")).isEmpty()) {
            derived.put("MUSIC_MV_ENABLED", "true");
        }
        if (trim(environment.getProperty("MUSIC_MV_D1_ENABLED")).isEmpty()) {
            derived.put("MUSIC_MV_D1_ENABLED", "true");
        }
        if (!derived.isEmpty()) {
            environment.getPropertySources().addLast(
                    new MapPropertySource(PROPERTY_SOURCE_NAME, derived));
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
