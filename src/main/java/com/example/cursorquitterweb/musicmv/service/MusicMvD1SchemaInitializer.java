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
    static final int SCHEMA_VERSION = 1;
    private static final int BATCH_SIZE = 20;
    private static final String SCHEMA_KEY = "core";
    private static final String D1_RESERVED_TABLE = "_cf_KV";

    private static final Set<String> KNOWN_TABLES = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    "music_mv_schema_metadata",
                    "template_categories",
                    "templates",
                    "template_translations",
                    "renderer_nodes",
                    "template_versions",
                    "template_slots",
                    "template_media",
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

        int batches = applyStatements(source.statements);
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
        if (categoryCount != 12L || schemaVersion != SCHEMA_VERSION
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
