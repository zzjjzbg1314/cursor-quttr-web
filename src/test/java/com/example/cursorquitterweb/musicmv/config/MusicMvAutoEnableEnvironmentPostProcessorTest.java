package com.example.cursorquitterweb.musicmv.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class MusicMvAutoEnableEnvironmentPostProcessorTest {
    @Test
    void derivesEnablementFromTheThreeCoreCloudflareValues() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MUSIC_MV_CLOUDFLARE_ACCOUNT_ID", "account-id")
                .withProperty("MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID", "database-id")
                .withProperty("MUSIC_MV_CLOUDFLARE_API_TOKEN", "secret-token");

        new MusicMvAutoEnableEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication());

        assertThat(environment.getProperty("MUSIC_MV_ENABLED")).isEqualTo("true");
        assertThat(environment.getProperty("MUSIC_MV_D1_ENABLED")).isEqualTo("true");
    }

    @Test
    void staysOffWhenConfigurationIsIncompleteAndRespectsExplicitDisable() {
        MockEnvironment incomplete = new MockEnvironment()
                .withProperty("MUSIC_MV_CLOUDFLARE_ACCOUNT_ID", "account-id");
        new MusicMvAutoEnableEnvironmentPostProcessor().postProcessEnvironment(
                incomplete, new SpringApplication());
        assertThat(incomplete.getProperty("MUSIC_MV_ENABLED")).isNull();

        MockEnvironment disabled = new MockEnvironment()
                .withProperty("MUSIC_MV_CLOUDFLARE_ACCOUNT_ID", "account-id")
                .withProperty("MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID", "database-id")
                .withProperty("MUSIC_MV_CLOUDFLARE_API_TOKEN", "secret-token")
                .withProperty("MUSIC_MV_ENABLED", "false");
        new MusicMvAutoEnableEnvironmentPostProcessor().postProcessEnvironment(
                disabled, new SpringApplication());
        assertThat(disabled.getProperty("MUSIC_MV_ENABLED")).isEqualTo("false");
    }
}
