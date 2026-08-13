package com.example.cursorquitterweb.musicmv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

class MusicMvSchemaIsolationTest {
    @Test
    void schemaContainsOnlyMusicMvCatalogAndRenderDomainTables() throws IOException {
        InputStream input = getClass().getResourceAsStream("/db/music-mv-d1-schema.sql");
        assertThat(input).isNotNull();
        String schema;
        try {
            schema = StreamUtils.copyToString(input, StandardCharsets.UTF_8).toLowerCase();
        } finally {
            input.close();
        }

        assertThat(schema).contains("create table if not exists templates");
        assertThat(schema).contains("create table if not exists template_versions");
        assertThat(schema).contains("create table if not exists music_mv_render_jobs");
        assertThat(schema).contains("create table if not exists renderer_nodes");
        assertThat(schema).contains("create table if not exists template_categories");
        assertThat(schema).contains("create table if not exists template_translations");
        assertThat(schema).contains("create table if not exists template_slots");
        assertThat(schema).contains("create table if not exists template_media");
        assertThat(schema).contains("create table if not exists music_mv_schema_metadata");
        assertThat(schema).contains("create table if not exists ai_music_jobs");
        assertThat(schema).contains("create table if not exists ai_music_provider_attempts");
        assertThat(schema).contains("create table if not exists ai_music_candidates");
        assertThat(schema).contains("create table if not exists ai_music_job_events");

        assertThat(schema).doesNotContain("create table if not exists users");
        assertThat(schema).doesNotContain("create table if not exists posts");
        assertThat(schema).doesNotContain("create table if not exists comments");
        assertThat(schema).doesNotContain("create table if not exists articles");
    }
}
