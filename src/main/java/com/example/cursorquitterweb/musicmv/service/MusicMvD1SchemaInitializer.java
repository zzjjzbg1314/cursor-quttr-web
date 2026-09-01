package com.example.cursorquitterweb.musicmv.service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;

/**
 * Explicit, idempotent schema bootstrap for the dedicated Music MV D1.
 *
 * It never runs at application startup. A caller must pass the dedicated
 * expected database id in the request. The controller is protected by the
 * local-only default authentication rule. Existing project tables are left
 * untouched; only the tables owned by this module are created or reconciled.
 */
@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvD1SchemaInitializer {
    static final int SCHEMA_VERSION = 12;
    static final long ENABLED_CATEGORY_COUNT = 24L;
    private static final int BATCH_SIZE = 20;
    private static final String SCHEMA_KEY = "core";
    private static final String D1_RESERVED_TABLE = "_cf_KV";

    private static final Set<String> KNOWN_TABLES = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    "music_mv_schema_metadata",
                    "music_mv_users",
                    "music_mv_user_identities",
                    "music_mv_user_sessions",
                    "music_mv_user_assets",
                    "music_mv_projects",
                    "music_mv_project_assets",
                    "template_categories",
                    "templates",
                    "template_translations",
                    "template_source_metadata",
                    "template_category_items",
                    "template_collections",
                    "template_collection_items",
                    "template_collection_relations",
                    "renderer_nodes",
                    "template_versions",
                    "template_slots",
                    "template_browser_scenes",
                    "template_browser_parity_validations",
                    "template_media",
                    "template_runtime_packages",
                    "template_resource_assets",
                    "template_version_resource_refs",
                    "ai_music_jobs",
                    "ai_music_provider_attempts",
                    "ai_music_candidates",
                    "ai_music_job_events",
                    "music_mv_render_jobs",
                    "music_mv_render_job_events",
                    "template_validation_records")));

    private final D1DatabaseClient d1;
    private final Resource schemaResource;

    public MusicMvD1SchemaInitializer(
            D1DatabaseClient d1,
            @Value("classpath:db/music-mv-d1-schema.sql") Resource schemaResource) {
        this.d1 = d1;
        this.schemaResource = schemaResource;
    }

    public Map<String, Object> initialize(String requestedExpectedDatabaseId) {
        requireSafeTarget(requestedExpectedDatabaseId);
        SchemaSource source = loadSchema();
        Set<String> existingTables = existingTables();
        Set<String> existingOwnedTables = new LinkedHashSet<String>(existingTables);
        existingOwnedTables.retainAll(KNOWN_TABLES);

        reconcileAiMusicOwnership(existingTables);
        reconcileCapCutTemplateIdentity(existingTables);
        reconcileTemplateTaxonomyColumns(existingTables);
        int batches = applyStatements(source.statements);
        Set<String> reconciledTables = existingTables();
        reconcileTemplateTaxonomyData(reconciledTables);
        backfillTemplateCategoryItems(reconciledTables);
        d1.query("INSERT INTO music_mv_schema_metadata "
                        + "(schema_key,schema_version,schema_sha256,applied_at,updated_at) "
                        + "VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT(schema_key) DO UPDATE SET "
                        + "schema_version=excluded.schema_version,schema_sha256=excluded.schema_sha256,"
                        + "updated_at=CURRENT_TIMESTAMP",
                SCHEMA_KEY, Integer.valueOf(SCHEMA_VERSION), source.sha256);

        Map<String, Object> verification = verify(source.sha256);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("status", existingOwnedTables.isEmpty() ? "initialized" : "reconciled");
        response.put("databaseId", d1.getDatabaseId());
        response.put("schemaVersion", Integer.valueOf(SCHEMA_VERSION));
        response.put("schemaSha256", source.sha256);
        response.put("statementCount", Integer.valueOf(source.statements.size()));
        response.put("batchCount", Integer.valueOf(batches));
        response.put("tableCount", verification.get("tableCount"));
        response.put("categoryCount", verification.get("categoryCount"));
        response.put("coexistingProjectTableCount",
                Integer.valueOf(existingTables.size() - existingOwnedTables.size()));
        response.put("ready", Boolean.TRUE);
        return response;
    }

    private void requireSafeTarget(String requestedExpectedDatabaseId) {
        if (!d1.isConfigured()) {
            throw new ApiException(HttpStatus.CONFLICT, "MUSIC_MV_D1_NOT_CONFIGURED",
                    "Independent Music MV D1 is not fully configured");
        }
        String actual = trim(d1.getDatabaseId());
        String requested = trim(requestedExpectedDatabaseId);
        if (requested.isEmpty() || !requested.equals(actual)) {
            throw new ApiException(HttpStatus.CONFLICT, "MUSIC_MV_D1_DATABASE_ID_MISMATCH",
                    "The requested D1 database id must match the configured database exactly");
        }
    }

    private Set<String> existingTables() {
        D1QueryResult result = d1.query("SELECT name FROM sqlite_master "
                + "WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'_cf_KV' ORDER BY name");
        Set<String> tables = new LinkedHashSet<String>();
        for (Map<String, Object> row : result.getRows()) {
            String name = RowUtils.str(row, "name");
            if (name != null && !name.trim().isEmpty()
                    && !D1_RESERVED_TABLE.equals(name.trim())) {
                tables.add(name.trim());
            }
        }
        return tables;
    }

    private int applyStatements(List<D1Statement> statements) {
        int batchCount = 0;
        for (int start = 0; start < statements.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, statements.size());
            d1.batch(new ArrayList<D1Statement>(statements.subList(start, end)));
            batchCount++;
        }
        return batchCount;
    }

    /**
     * D1/SQLite cannot add a NOT NULL foreign-key column to an existing table
     * without rebuilding it. For the v3 -> v4 upgrade we add a nullable column,
     * backfill rows that had already been claimed by a real user, and let every
     * new application write populate it. Anonymous legacy rows intentionally
     * remain unowned and are therefore invisible to authenticated libraries.
     */
    private void reconcileAiMusicOwnership(Set<String> existingTables) {
        if (!existingTables.contains("ai_music_jobs")) return;
        boolean hasUserId = false;
        for (Map<String, Object> row : d1.query("PRAGMA table_info(ai_music_jobs)").getRows()) {
            if ("user_id".equals(RowUtils.str(row, "name"))) {
                hasUserId = true;
                break;
            }
        }
        if (!hasUserId) {
            d1.query("ALTER TABLE ai_music_jobs ADD COLUMN user_id TEXT");
        }
        d1.query("UPDATE ai_music_jobs SET user_id=client_id "
                + "WHERE user_id IS NULL AND client_id LIKE 'usr\\_%' ESCAPE '\\'");
    }

    private void reconcileCapCutTemplateIdentity(Set<String> existingTables) {
        if (!existingTables.contains("templates")) return;
        for (Map<String, Object> row : d1.query("PRAGMA table_info(templates)").getRows()) {
            if ("capcut_template_id".equals(RowUtils.str(row, "name"))) return;
        }
        d1.query("ALTER TABLE templates ADD COLUMN capcut_template_id TEXT");
    }

    private void reconcileTemplateTaxonomyColumns(Set<String> existingTables) {
        if (!existingTables.contains("template_categories")) return;
        Set<String> columns = new LinkedHashSet<String>();
        for (Map<String, Object> row : d1.query("PRAGMA table_info(template_categories)").getRows()) {
            columns.add(RowUtils.str(row, "name"));
        }
        if (!columns.contains("parent_key")) {
            d1.query("ALTER TABLE template_categories ADD COLUMN parent_key TEXT");
        }
        if (!columns.contains("level")) {
            d1.query("ALTER TABLE template_categories ADD COLUMN level INTEGER NOT NULL DEFAULT 2");
        }
        if (!columns.contains("slug_path")) {
            d1.query("ALTER TABLE template_categories ADD COLUMN slug_path TEXT NOT NULL DEFAULT ''");
        }
        if (!columns.contains("is_selectable")) {
            d1.query("ALTER TABLE template_categories ADD COLUMN is_selectable INTEGER NOT NULL DEFAULT 1");
        }
    }

    private void reconcileTemplateTaxonomyData(Set<String> existingTables) {
        if (!existingTables.contains("template_categories")) return;
        if (existingTables.contains("templates")) {
            String[][] migrations = new String[][] {
                    {"baby-growth", "baby-kids"},
                    {"love", "couples"},
                    {"wedding-anniversary", "anniversary"},
                    {"inspiration", "motivation"},
                    {"breakup", "farewell-breakup"},
                    {"party-festival", "holidays-parties"},
                    {"gaming-anime", "hobbies-interests"}
            };
            for (String[] migration : migrations) {
                d1.query("UPDATE templates SET category_key=?,updated_at=CURRENT_TIMESTAMP "
                                + "WHERE category_key=?",
                        migration[1], migration[0]);
            }
        }
        String[][] reused = new String[][] {
                {"birthday", "celebrations", "celebrations/birthday", "生日", "Birthday", "11"},
                {"family", "relationships", "relationships/family", "家庭", "Family", "21"},
                {"friendship", "relationships", "relationships/friendship", "友情", "Friendship", "24"},
                {"school-life", "life-stories", "life-stories/school-life", "校园生活", "School Life", "33"},
                {"healing", "emotions-messages", "emotions-messages/healing", "疗愈", "Healing", "42"}
        };
        for (String[] category : reused) {
            d1.query("UPDATE template_categories SET parent_key=?,level=2,slug_path=?,"
                            + "is_selectable=1,name_zh=?,name_en=?,sort_order=?,enabled=1,"
                            + "updated_at=CURRENT_TIMESTAMP WHERE category_key=?",
                    category[1], category[2], category[3], category[4],
                    Integer.valueOf(category[5]), category[0]);
        }
        d1.query("UPDATE template_categories SET enabled=0,is_selectable=0,updated_at=CURRENT_TIMESTAMP "
                + "WHERE category_key IN ('baby-growth','love','wedding-anniversary','inspiration',"
                + "'breakup','party-festival','gaming-anime')");
        // Collection tables remain dormant for backward compatibility. New
        // catalog code uses template_category_items instead.
    }

    private void backfillTemplateCategoryItems(Set<String> existingTables) {
        if (!existingTables.contains("templates")
                || !existingTables.contains("template_category_items")) return;
        d1.query("INSERT OR IGNORE INTO template_category_items "
                        + "(template_id,category_key,is_primary,source,confidence,evidence_json,created_at,updated_at) "
                        + "SELECT template_id,category_key,1,'legacy',1.0,'[{\"field\":\"legacyCategory\"}]',"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM templates WHERE deleted_at IS NULL");
        if (existingTables.contains("template_source_metadata")) {
            d1.query("INSERT OR IGNORE INTO template_source_metadata "
                            + "(template_id,source_title,source_description,source_category,source_search_keyword,"
                            + "source_hashtags_json,source_url,classifier_version,classification_locked,created_at,updated_at) "
                            + "SELECT template_id,'','','','', '[]',CASE WHEN capcut_template_id IS NULL THEN '' "
                            + "ELSE 'https://www.capcut.com/template-detail/' || capcut_template_id END,"
                            + "'source-rules-v1',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM templates "
                            + "WHERE deleted_at IS NULL");
        }
    }

    private void backfillCollectionMembership(Set<String> existingTables) {
        if (!existingTables.contains("templates")
                || !existingTables.contains("template_collection_items")) return;
        d1.query("DELETE FROM template_collection_items WHERE source='taxonomy-backfill'");
        String[][] rules = new String[][] {
                // The category itself is enough evidence for these broad,
                // category-backed discovery collections. More specific
                // collections still require an explicit keyword match.
                {"family-birthday", "birthday"},
                {"birthday-for-mom", "birthday", "%mom%", "%mother%", "%妈妈%", "%母亲%"},
                {"birthday-for-dad", "birthday", "%dad%", "%father%", "%爸爸%", "%父亲%"},
                {"baby-first-year", "baby-kids", "%first year%", "%第一年%"},
                {"family-year-in-review", "recap", "%family%", "%家庭%"},
                {"wedding-story", "wedding"},
                {"graduation-memories", "graduation"},
                {"friendship-memories", "friendship"}
        };
        for (String[] rule : rules) {
            StringBuilder condition = new StringBuilder();
            List<Object> params = new ArrayList<Object>();
            params.add(rule[0]);
            params.add(rule[1]);
            for (int index = 2; index < rule.length; index++) {
                if (condition.length() > 0) condition.append(" OR ");
                condition.append("lower(t.tags_json) LIKE ?");
                params.add(rule[index].toLowerCase());
            }
            if (condition.length() == 0) condition.append("1=1");
            d1.query("INSERT OR IGNORE INTO template_collection_items "
                            + "(collection_key,template_id,sort_order,source,created_at,updated_at) "
                            + "SELECT ?,t.template_id,0,'taxonomy-backfill',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP "
                            + "FROM templates t WHERE t.deleted_at IS NULL AND t.category_key=? AND ("
                            + condition + ")",
                    params.toArray(new Object[params.size()]));
        }
    }

    private Map<String, Object> verify(String expectedSha256) {
        Set<String> missing = new LinkedHashSet<String>(KNOWN_TABLES);
        missing.removeAll(existingTables());
        if (!missing.isEmpty()) {
            throw verificationFailed("Missing schema tables after initialization", missing);
        }

        Map<String, Object> categoryRow = d1.query(
                "SELECT COUNT(*) AS category_count FROM template_categories WHERE enabled=1").firstRow();
        long categoryCount = number(categoryRow, "category_count");
        Map<String, Object> metadata = d1.query(
                "SELECT schema_version,schema_sha256 FROM music_mv_schema_metadata WHERE schema_key=?",
                SCHEMA_KEY).firstRow();
        long schemaVersion = number(metadata, "schema_version");
        String schemaSha256 = metadata == null ? null : RowUtils.str(metadata, "schema_sha256");
        if (categoryCount != ENABLED_CATEGORY_COUNT || schemaVersion != SCHEMA_VERSION
                || !expectedSha256.equals(schemaSha256)) {
            throw verificationFailed("Schema metadata or category seed verification failed",
                    Collections.<String>emptySet());
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("tableCount", Integer.valueOf(KNOWN_TABLES.size()));
        result.put("categoryCount", Long.valueOf(categoryCount));
        return result;
    }

    private ApiException verificationFailed(String message, Set<String> missing) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        if (!missing.isEmpty()) {
            details.put("missingTables", sorted(missing));
        }
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                "MUSIC_MV_D1_SCHEMA_VERIFICATION_FAILED", message, true, details);
    }

    private long number(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return -1L;
        }
        Object value = row.get(key);
        return value instanceof Number
                ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private SchemaSource loadSchema() {
        byte[] bytes;
        try (InputStream input = schemaResource.getInputStream()) {
            bytes = readAll(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Read Music MV D1 schema failed", exception);
        }
        String sql = new String(bytes, StandardCharsets.UTF_8);
        List<String> rawStatements = splitStatements(removeLineComments(sql));
        List<D1Statement> statements = new ArrayList<D1Statement>();
        for (String raw : rawStatements) {
            String statement = raw.trim();
            if (!statement.isEmpty()) {
                statements.add(new D1Statement(statement, Collections.<Object>emptyList()));
            }
        }
        if (statements.isEmpty()) {
            throw new IllegalStateException("Music MV D1 schema contains no statements");
        }
        return new SchemaSource(statements, sha256(bytes));
    }

    private String removeLineComments(String sql) {
        StringBuilder result = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new java.io.ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().startsWith("--")) {
                    result.append(line).append('\n');
                }
            }
        } catch (IOException impossible) {
            throw new IllegalStateException("Read in-memory schema failed", impossible);
        }
        return result.toString();
    }

    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        for (int index = 0; index < sql.length(); index++) {
            char character = sql.charAt(index);
            if (character == '\'' && inSingleQuote && index + 1 < sql.length()
                    && sql.charAt(index + 1) == '\'') {
                current.append(character).append(sql.charAt(++index));
                continue;
            }
            if (character == '\'') {
                inSingleQuote = !inSingleQuote;
            }
            if (character == ';' && !inSingleQuote) {
                statements.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (inSingleQuote) {
            throw new IllegalStateException("Music MV D1 schema contains an unterminated string");
        }
        if (current.toString().trim().length() > 0) {
            statements.add(current.toString());
        }
        return statements;
    }

    private byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder();
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private List<String> sorted(Set<String> values) {
        List<String> result = new ArrayList<String>(values);
        Collections.sort(result);
        return result;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class SchemaSource {
        private final List<D1Statement> statements;
        private final String sha256;

        private SchemaSource(List<D1Statement> statements, String sha256) {
            this.statements = statements;
            this.sha256 = sha256;
        }
    }
}
