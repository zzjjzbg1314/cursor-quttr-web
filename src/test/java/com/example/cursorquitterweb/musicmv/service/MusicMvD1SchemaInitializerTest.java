package com.example.cursorquitterweb.musicmv.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class MusicMvD1SchemaInitializerTest {
    private static final String DATABASE_ID = "dedicated-music-mv-d1";

    @Test
    void initializesEmptyDedicatedDatabaseAndVerifiesSchema() {
        // Every real D1 contains the reserved _cf_KV storage table. It is not
        // an application table and must not make a fresh database look dirty.
        CapturingD1 d1 = new CapturingD1(DATABASE_ID, Arrays.asList("_cf_KV"));
        MusicMvD1SchemaInitializer initializer = initializer(d1);

        Map<String, Object> result = initializer.initialize(DATABASE_ID);

        assertThat(result.get("status")).isEqualTo("initialized");
        assertThat(result.get("databaseId")).isEqualTo(DATABASE_ID);
        assertThat(result.get("schemaVersion")).isEqualTo(14);
        assertThat(result.get("categoryCount")).isEqualTo(11L);
        assertThat(result.get("ready")).isEqualTo(Boolean.TRUE);
        assertThat(d1.statements).anyMatch(statement -> statement.contains(
                "CREATE TABLE IF NOT EXISTS music_mv_schema_metadata"));
        assertThat(d1.statements).anyMatch(statement -> statement.contains(
                "INSERT OR IGNORE INTO template_categories"));
        assertThat(d1.queries).anyMatch(statement -> statement.contains(
                "UPDATE templates SET category_key=?"));
        assertThat(d1.queries).anyMatch(statement -> statement.contains(
                "INSERT OR IGNORE INTO template_category_items"));
        assertThat(d1.metadataSha256).hasSize(64);
    }

    @Test
    void remainsIdempotentForExistingMusicMvSchema() {
        CapturingD1 d1 = new CapturingD1(DATABASE_ID, CapturingD1.knownTables());
        MusicMvD1SchemaInitializer initializer = initializer(d1);

        Map<String, Object> result = initializer.initialize(DATABASE_ID);

        assertThat(d1.queries).contains("ALTER TABLE ai_music_jobs ADD COLUMN provider_synced_at TEXT",
                "ALTER TABLE ai_music_jobs ADD COLUMN status_refresh_at TEXT",
                "ALTER TABLE ai_music_jobs ADD COLUMN status_refresh_until TEXT",
                "ALTER TABLE ai_music_jobs ADD COLUMN status_refresh_token TEXT");
        assertThat(result.get("status")).isEqualTo("reconciled");
        assertThat(result.get("ready")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void preservesExistingProjectTablesWhileAddingMusicMvSchema() {
        CapturingD1 d1 = new CapturingD1(DATABASE_ID, Arrays.asList("users", "templates"));
        MusicMvD1SchemaInitializer initializer = initializer(d1);

        Map<String, Object> result = initializer.initialize(DATABASE_ID);

        assertThat(result.get("status")).isEqualTo("reconciled");
        assertThat(result.get("coexistingProjectTableCount")).isEqualTo(1);
        assertThat(d1.statements).anyMatch(statement -> statement.contains(
                "CREATE TABLE IF NOT EXISTS music_mv_schema_metadata"));
    }

    @Test
    void requiresExactRequestedDatabaseIdMatch() {
        CapturingD1 d1 = new CapturingD1(DATABASE_ID, Collections.<String>emptyList());

        assertThatThrownBy(() -> initializer(d1).initialize("another-database"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("MUSIC_MV_D1_DATABASE_ID_MISMATCH"));
    }

    @Test
    void migratesRealSqliteWithoutLosingTemplatesAndKeepsHistoryOnRepeat() throws Exception {
        CapturingD1 d1 = new CapturingD1(DATABASE_ID, CapturingD1.knownTables());
        initializer(d1).initialize(DATABASE_ID);
        java.nio.file.Path payload = java.nio.file.Files.createTempFile("topic-migration", ".json");
        try {
            new ObjectMapper().writeValue(payload.toFile(), d1.boundQueries);
            Process process = new ProcessBuilder("python3", "src/test/resources/musicmv/topic-migration-check.py", payload.toString()).redirectErrorStream(true).start();
            assertThat(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            String output = new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue()).withFailMessage(output).isZero();
        } finally { java.nio.file.Files.deleteIfExists(payload); }
    }

    private MusicMvD1SchemaInitializer initializer(CapturingD1 d1) {
        return new MusicMvD1SchemaInitializer(d1,
                new ClassPathResource("db/music-mv-d1-schema.sql"));
    }

    private static final class CapturingD1 extends D1DatabaseClient {
        private final String databaseId;
        private final List<String> initialTables;
        private final List<String> statements = new ArrayList<String>();
        private final List<String> queries = new ArrayList<String>();
        private final List<Map<String, Object>> boundQueries = new ArrayList<>();
        private boolean applied;
        private String metadataSha256;

        private CapturingD1(String databaseId, List<String> initialTables) {
            super(new ObjectMapper());
            this.databaseId = databaseId;
            this.initialTables = new ArrayList<String>(initialTables);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String getDatabaseId() {
            return databaseId;
        }

        @Override
        public D1QueryResult query(String sql, Object... params) {
            queries.add(sql);
            Map<String, Object> bound = new LinkedHashMap<>(); bound.put("sql", sql); bound.put("params", Arrays.asList(params)); boundQueries.add(bound);
            if (sql.contains("sqlite_master")) {
                return rows(tableRows(applied ? knownTables() : initialTables));
            }
            if (sql.startsWith("INSERT INTO music_mv_schema_metadata")) {
                metadataSha256 = String.valueOf(params[2]);
                return rows(Collections.<Map<String, Object>>emptyList());
            }
            if (sql.startsWith("PRAGMA table_info(ai_music_jobs)")) {
                Map<String, Object> column = new LinkedHashMap<String, Object>();
                column.put("name", "user_id");
                return rows(Arrays.asList(column));
            }
            if (sql.startsWith("PRAGMA table_info(templates)")) {
                Map<String, Object> column = new LinkedHashMap<String, Object>();
                column.put("name", "capcut_template_id");
                return rows(Arrays.asList(column));
            }
            if (sql.startsWith("PRAGMA table_info(template_categories)")) {
                List<Map<String, Object>> columns = new ArrayList<Map<String, Object>>();
                for (String name : Arrays.asList("parent_key", "level", "slug_path", "is_selectable")) {
                    Map<String, Object> column = new LinkedHashMap<String, Object>();
                    column.put("name", name);
                    columns.add(column);
                }
                return rows(columns);
            }
            if (sql.startsWith("ALTER TABLE ai_music_jobs ADD COLUMN ")
                    || sql.startsWith("UPDATE ai_music_jobs SET user_id=client_id")) {
                return rows(Collections.<Map<String, Object>>emptyList());
            }
            if (sql.startsWith("DELETE FROM template_category_items WHERE category_key IN")
                    || sql.startsWith("CREATE TABLE IF NOT EXISTS template_taxonomy_history")
                    || sql.startsWith("INSERT OR IGNORE INTO template_taxonomy_history")
                    || sql.startsWith("SELECT t.template_id,t.category_key,t.status,h.old_category_key")
                    || sql.startsWith("UPDATE template_category_items SET is_primary=")
                    || sql.startsWith("UPDATE templates SET category_key=?")
                    || sql.startsWith("UPDATE template_categories SET parent_key=NULL")
                    || sql.startsWith("UPDATE template_categories SET enabled=0")
                    || sql.startsWith("DELETE FROM template_collection_items")
                    || sql.startsWith("INSERT OR IGNORE INTO template_collection_items")
                    || sql.startsWith("INSERT OR IGNORE INTO template_category_items")
                    || sql.startsWith("INSERT OR IGNORE INTO template_source_metadata")) {
                return rows(Collections.<Map<String, Object>>emptyList());
            }
            if (sql.contains("COUNT(*) AS category_count")) {
                return row("category_count", Long.valueOf(applied ? 11L : 0L));
            }
            if (sql.contains("FROM music_mv_schema_metadata")) {
                Map<String, Object> metadata = new LinkedHashMap<String, Object>();
                metadata.put("schema_version", Integer.valueOf(14));
                metadata.put("schema_sha256", metadataSha256);
                return rows(Arrays.asList(metadata));
            }
            throw new AssertionError("Unexpected SQL: " + sql);
        }

        @Override
        public List<D1QueryResult> batch(List<D1Statement> batch) {
            for (D1Statement statement : batch) {
                statements.add(statement.getSql());
                queries.add(statement.getSql());
                Map<String, Object> bound = new LinkedHashMap<>(); bound.put("sql", statement.getSql()); bound.put("params", statement.getParams()); boundQueries.add(bound);
            }
            applied = true;
            return Collections.emptyList();
        }

        private static List<String> knownTables() {
            return Arrays.asList(
                    "music_mv_schema_metadata", "template_categories", "templates",
                    "music_mv_users", "music_mv_user_identities", "music_mv_user_sessions",
                    "music_mv_user_assets", "music_mv_projects", "music_mv_project_assets",
                    "template_translations", "renderer_nodes", "template_versions",
                    "template_source_metadata", "template_category_items",
                    "template_collections", "template_collection_items", "template_collection_relations",
                    "template_slots", "template_browser_scenes",
                    "template_browser_parity_validations", "template_media", "template_runtime_packages",
                    "template_resource_assets", "template_version_resource_refs",
                    "music_mv_render_jobs",
                    "music_mv_render_job_events", "template_validation_records",
                    "ai_music_jobs", "ai_music_provider_attempts", "ai_music_candidates",
                    "ai_music_job_events");
        }

        private D1QueryResult row(String key, Object value) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put(key, value);
            return rows(Arrays.asList(row));
        }

        private D1QueryResult rows(List<Map<String, Object>> values) {
            return new D1QueryResult(values, null);
        }

        private List<Map<String, Object>> tableRows(List<String> tables) {
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            for (String table : tables) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("name", table);
                rows.add(row);
            }
            return rows;
        }
    }
}
