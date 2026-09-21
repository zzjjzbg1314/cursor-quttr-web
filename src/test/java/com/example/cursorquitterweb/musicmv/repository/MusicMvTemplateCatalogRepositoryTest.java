package com.example.cursorquitterweb.musicmv.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest;
import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1QueryResult;
import com.example.cursorquitterweb.musicmv.service.D1Statement;
import com.fasterxml.jackson.databind.ObjectMapper;

class MusicMvTemplateCatalogRepositoryTest {
    @Test void 主题查询只返回有标签的公开上线模板而不回退分类() throws Exception {
        CapturingD1 client=new CapturingD1();
        new MusicMvTemplateCatalogRepository(client).templateCount("published","public",null,null,null,null,null,null,null,null,"birthday");
        String script=String.join("\n",
            "import sqlite3,json,sys",
            "db=sqlite3.connect(':memory:')",
            "db.executescript('CREATE TABLE templates(template_id TEXT,category_key TEXT,status TEXT,visibility TEXT,deleted_at TEXT,current_version_id TEXT); CREATE TABLE template_versions(version_id TEXT); CREATE TABLE template_categories(category_key TEXT,enabled INTEGER,is_selectable INTEGER); CREATE TABLE template_tag_items(template_id TEXT,tag_key TEXT);')",
            "db.executemany('INSERT INTO template_categories VALUES (?,1,1)', [('birthday',),('couples',)])",
            "db.executemany('INSERT INTO templates VALUES (?,?,?,?,?,NULL)', [('tagged','couples','published','public',None),('category-only','birthday','published','public',None),('draft','birthday','draft','public',None),('private','birthday','published','private',None),('deleted','birthday','published','public','now')])",
            "db.executemany('INSERT INTO template_tag_items VALUES (?,?)', [(x,'birthday') for x in ['tagged','draft','private','deleted']])",
            "assert db.execute(sys.argv[1],json.loads(sys.argv[2])).fetchone()[0]==1",
            "db.execute(\"DELETE FROM template_tag_items WHERE template_id='tagged'\")",
            "assert db.execute(sys.argv[1],json.loads(sys.argv[2])).fetchone()[0]==0");
        Process process=new ProcessBuilder("python3","-c",script,client.sql,new ObjectMapper().writeValueAsString(client.params)).redirectErrorStream(true).start();
        assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));
        String output=new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.exitValue(),output);
    }
    @Test void 标签筛选在分页前执行且总数使用同一条件() {
        CapturingD1 client=new CapturingD1();
        MusicMvTemplateCatalogRepository repository=new MusicMvTemplateCatalogRepository(client);
        repository.templates("en","published","public",null,null,null,1,10,null,null,null,30,30,"birthday");
        String list=client.sql.substring(client.sql.indexOf("WHERE t.deleted_at IS NULL"),client.sql.indexOf("ORDER BY t.sort_order"));
        List<Object> params=new ArrayList<>(client.params.subList(1,client.params.size()-2));
        assertTrue(list.contains("tagged.tag_key=?"));
        assertTrue(list.contains("t.status=?") && list.contains("t.visibility=?"));
        assertFalse(list.contains("ti.category_key=?"));
        assertTrue(params.contains("birthday"));
        repository.templateCount("published","public",null,null,null,1,10,null,null,null,"birthday");
        assertEquals(list,client.sql.substring(client.sql.indexOf("WHERE t.deleted_at IS NULL")));
        assertEquals(params,client.params);
    }
    @Test void readyUpdateIsGuardedByAssetIdentityAndPreservesReadyTime() {
        CapturingD1 client=new CapturingD1();
        new MusicMvTemplateCatalogRepository(client).markMediaReadyIfCurrent("m","cloudflare_images","asset","hash","{}");
        assertEquals(Arrays.asList("{}","m","cloudflare_images","asset","hash","{}"),client.params);
        assertTrue(client.sql.contains("provider=? AND provider_asset_id=? AND source_sha256=?"));
        assertTrue(client.sql.contains("ready_at=COALESCE(ready_at,CURRENT_TIMESTAMP)"));
        assertTrue(client.sql.contains("status<>'ready' OR provider_details_json<>?"));
    }
    @Test void listAndCountShareSearchAndTechnicalPredicates() {
        CapturingD1 client=new CapturingD1();
        MusicMvTemplateCatalogRepository repository=new MusicMvTemplateCatalogRepository(client);
        repository.templates("en","published","public","wedding",null,"婚礼",1,10,1d,60d,"9:16",2,0);
        String list=client.sql.substring(client.sql.indexOf("WHERE t.deleted_at IS NULL"),client.sql.indexOf("ORDER BY t.sort_order"));
        List<Object> params=new ArrayList<>(client.params.subList(1,client.params.size()-2));
        assertTrue(client.sql.contains("t.template_id ASC"));
        repository.templateCount("published","public","wedding",null,"婚礼",1,10,1d,60d,"9:16");
        assertEquals(list,client.sql.substring(client.sql.indexOf("WHERE t.deleted_at IS NULL")));
        assertEquals(params,client.params);
    }
    @Test
    void readsOnlyFreshPublicVersionStateForCachedDetails() {
        CapturingD1 client = new CapturingD1();
        new MusicMvTemplateCatalogRepository(client).publicVersionStatus("template", "version");
        assertEquals(Arrays.asList("version", "template"), client.params);
        assertTrue(client.sql.contains("t.deleted_at IS NULL"));
        assertTrue(client.sql.contains("v.template_id=t.template_id AND v.version_id=?"));
        assertTrue(client.sql.contains("template_status") && client.sql.contains("version_status"));
        assertFalse(client.sql.contains("scene_json"));
    }

    @Test
    void batchesExactResourceQueriesAndPreservesMissingRows() {
        int[] calls = {0};
        D1DatabaseClient client = new D1DatabaseClient(new ObjectMapper()) {
            @Override public List<D1QueryResult> batch(List<D1Statement> statements) {
                calls[0]++;
                assertTrue(statements.size() <= 50);
                List<D1QueryResult> results = new ArrayList<>();
                for (D1Statement statement : statements) {
                    assertTrue(statement.getSql().contains("resource_id=? AND source_sha256=?"));
                    assertEquals(2, statement.getParams().size());
                    String id = String.valueOf(statement.getParams().get(0));
                    assertEquals("hash_" + id, statement.getParams().get(1));
                    results.add(new D1QueryResult(id.equals("r_50") ? java.util.Collections.emptyList()
                            : java.util.Collections.singletonList(java.util.Collections.singletonMap("resource_id", id)), null));
                }
                return results;
            }
        };
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);
        Map<String, String> requested = new java.util.LinkedHashMap<>();
        assertTrue(repository.templateResourceAssets(requested).isEmpty()); assertEquals(0, calls[0]);
        for (int i = 0; i < 51; i++) requested.put("r_" + i, "hash_r_" + i);
        Map<String, Map<String, Object>> result = repository.templateResourceAssets(requested);
        assertEquals(2, calls[0]); assertEquals(51, result.size());
        assertEquals("r_49", result.get("r_49").get("resource_id"));
        org.junit.jupiter.api.Assertions.assertNull(result.get("r_50"));
    }

    @Test
    void cleanupIsDelayedBoundedAndFailsClosedWithoutReferenceEvidence() {
        CapturingD1 client = new CapturingD1();
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);
        repository.cleanupCandidates();
        assertTrue(client.sql.contains("-24 hours"));
        assertTrue(client.sql.contains("LIMIT 50"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> repository.cleanupAssetReferenced("cloudflare_images", "old", "t", "v"));
        assertTrue(client.sql.contains("template_browser_scenes"));
        assertTrue(client.sql.contains("music_mv_projects"));
        assertTrue(client.sql.contains("music_mv_render_jobs"));
        assertTrue(client.sql.contains("status<>'ready'"));
    }
    @Test
    void staleMediaCleanupIsLimitedToTargetTemplateAndVersion() {
        CapturingD1 client = new CapturingD1();
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);
        repository.retainSynchronizedMedia("tpl_1", "tplver_1", Arrays.asList("cover", "browser_parity_reference"));
        assertEquals(2, client.statements.size());
        assertTrue(client.statements.get(0).getSql().startsWith("INSERT OR IGNORE INTO template_media_cleanup"));
        for (D1Statement item : client.statements) {
            assertTrue(item.getSql().contains("template_id=? AND version_id=? AND media_role NOT IN (?,?)"));
            assertEquals(placeholders(item.getSql()), item.getParams().size());
        }
    }

    @Test
    void replacedMediaIsQueuedAtomicallyBeforeOverwrite() {
        CapturingD1 client = new CapturingD1();
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);
        repository.upsertMedia("m", "t", "v", "cover", "cloudflare_images", "new", "ready",
                hash('a'), 1, 1, 1, null, "{}");
        assertEquals(2, client.statements.size());
        assertTrue(client.statements.get(0).getSql().contains("AND (provider<>? OR provider_asset_id<>?)"));
        assertTrue(client.statements.get(1).getSql().startsWith("INSERT INTO template_media"));
        for (D1Statement item : client.statements) assertEquals(placeholders(item.getSql()), item.getParams().size());
    }
    @Test
    void synchronizationUpdatesInPlaceWithoutDeletingVersionsOrProjects() {
        CapturingD1 client = new CapturingD1();
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);
        repository.replaceSynchronizedVersion(validPromotion(), "tplver_1", "[]", "{}", "{}");
        StringBuilder sql = new StringBuilder();
        for (D1Statement statement : client.statements) {
            assertEquals(placeholders(statement.getSql()), statement.getParams().size(), statement.getSql());
            sql.append(statement.getSql()).append('\n');
        }
        String batch = sql.toString();
        assertTrue(batch.contains("ON CONFLICT(version_id) DO UPDATE SET width=excluded.width"));
        assertTrue(batch.contains("validation_master_sha256=excluded.validation_master_sha256"));
        assertTrue(batch.contains("DELETE FROM template_validation_records WHERE version_id=?"));
        assertFalse(batch.contains("SET current_version_id="));
        assertFalse(batch.contains("SET status='published'"));
        assertFalse(batch.contains("DELETE FROM template_versions"));
        assertFalse(batch.contains("DELETE FROM templates"));
        assertFalse(batch.contains("music_mv_projects"));
    }

    @Test
    void promotionBatchHasOneBoundValueForEverySqlPlaceholder() {
        CapturingD1 client = new CapturingD1();
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);
        repository.promote(validPromotion(), "tplver_1", 1, "[]", "{}", "{}");

        assertFalse(client.statements.isEmpty());
        for (D1Statement statement : client.statements) {
            assertEquals(placeholders(statement.getSql()), statement.getParams().size(),
                    statement.getSql());
        }
    }

    @Test
    void forceDeletionDetachesProjectsAndRemovesRenderReferencesBeforeTemplateGraph() {
        CapturingD1 client = new CapturingD1();
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);

        repository.forceDeleteTemplate("tpl_1");

        StringBuilder sql = new StringBuilder();
        for (D1Statement statement : client.statements) {
            assertEquals(placeholders(statement.getSql()), statement.getParams().size(),
                    statement.getSql());
            sql.append(statement.getSql()).append('\n');
        }
        String batch = sql.toString();
        assertTrue(batch.contains("DELETE FROM music_mv_render_job_events"));
        assertTrue(batch.contains("DELETE FROM music_mv_render_jobs WHERE template_id=?"));
        assertTrue(batch.contains("UPDATE music_mv_projects SET template_id=NULL"));
        assertTrue(batch.contains("DELETE FROM template_browser_scenes"));
        assertTrue(batch.contains("DELETE FROM templates WHERE template_id=?"));
    }

    @Test
    void catalogAvailabilityDoesNotDependOnAWorkerHeartbeat() {
        CapturingD1 client = new CapturingD1();
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);

        repository.templates("en", "published", "public", null, null, null,
                null, null, null, null, null, 24, 0);

        assertTrue(client.sql.contains("v.source_availability AS source_availability"));
        assertTrue(client.sql.contains("$.runtimeDelivery.totalSizeBytes"));
        assertTrue(client.sql.contains("END AS runtime_package_size_bytes"));
        assertTrue(client.sql.contains("runtime_scene.version_id=t.current_version_id"));
        assertFalse(client.sql.contains("renderer_nodes"));
        assertFalse(client.sql.contains("last_seen_at"));
    }

    @Test
    void catalogPreviewFallsBackToBrowserParityReference() {
        CapturingD1 client = new CapturingD1();
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);

        repository.templates("en", "published", "public", null, null, null,
                null, null, null, null, null, 24, 0);

        assertTrue(client.sql.contains("'full_mv','browser_parity_reference'"));
        assertTrue(client.sql.contains("candidate.status='ready'"));
        assertTrue(client.sql.contains("candidate.media_role='full_mv'"));
    }

    @Test
    void templateDetailUsesOneEightStatementBatch() {
        CapturingD1 client = new CapturingD1();
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);

        repository.templateDetail("tpl_1");

        assertEquals(8, client.statements.size());
        for (D1Statement statement : client.statements) {
            assertEquals(1, placeholders(statement.getSql()));
            assertEquals(Arrays.<Object>asList("tpl_1"), statement.getParams());
        }
    }

    @Test
    void customerDetailRestrictsEveryVersionScopedRead() {
        CapturingD1 client = new CapturingD1();
        MusicMvTemplateCatalogRepository repository = new MusicMvTemplateCatalogRepository(client);
        for (String version : Arrays.asList(null, "accepted-old")) {
            repository.templateDetail("tpl_1", version);
            assertEquals(8, client.statements.size());
            for (int i = 0; i < 8; i++) {
                D1Statement statement = client.statements.get(i);
                assertEquals(i < 4 ? 1 : 2, placeholders(statement.getSql()));
                if (i >= 4) {
                    assertEquals(Arrays.<Object>asList("tpl_1", version == null ? "tpl_1" : version), statement.getParams());
                    assertTrue(statement.getSql().contains(version == null
                            ? "v.version_id=(SELECT current_version_id FROM templates WHERE template_id=?)"
                            : "v.version_id=?"));
                }
            }
        }
    }

    private int placeholders(String sql) {
        int count = 0;
        for (int index = 0; index < sql.length(); index++) if (sql.charAt(index) == '?') count++;
        return count;
    }

    private TemplatePromotionRequest validPromotion() {
        TemplatePromotionRequest request = new TemplatePromotionRequest();
        request.setTemplateId("tpl_1"); request.setSlug("one"); request.setCategoryKey("birthday");
        request.setCapcutTemplateId("7362454015088561426");
        request.setNameZh("一"); request.setNameEn("One");
        request.setWidth(1080); request.setHeight(1920); request.setFps(30d);
        request.setDurationSeconds(180d); request.setBaseDurationSeconds(13d); request.setCycleDurationSeconds(13d);
        request.setValidationRenderJobId("job_1"); request.setValidationMasterSha256(hash('a'));
        request.setDraftSnapshotSha256(hash('b')); request.setTimelineEvidenceSha256(hash('c'));
        request.setNativeRuntimeVersion("9.2"); request.setNativeRuntimeSha256(hash('d'));
        request.setRendererVersion("one"); request.setSourceNodeId("mac"); request.setSourceLocalKey("local");
        request.setSemanticIntegrity("exact"); request.setVideoEncodeCount(1); request.setIntermediateVideoCount(0);
        request.setExternalResourceReadCount(0); request.setMissingResourceCount(0); request.setValidationElapsedSeconds(1d);
        TemplatePromotionRequest.Slot slot = new TemplatePromotionRequest.Slot();
        slot.setSlotKey("photo_1"); slot.setSlotType("image"); slot.setDisplayName("Photo");
        slot.setTimelineOrder(0); slot.setCropPolicy("fill"); slot.setRepeatPolicy("cycle");
        request.setSlots(java.util.Collections.singletonList(slot));
        return request;
    }

    private String hash(char value) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 64; index++) text.append(value);
        return text.toString();
    }

    private static class CapturingD1 extends D1DatabaseClient {
        private String sql;
        private List<Object> params = new ArrayList<Object>();
        private List<D1Statement> statements = new ArrayList<D1Statement>();
        CapturingD1() { super(new ObjectMapper()); }
        @Override public D1QueryResult query(String sql, Object... params) {
            this.sql = sql;
            this.params = Arrays.asList(params);
            return new D1QueryResult(new ArrayList<Map<String, Object>>(), 0L);
        }
        @Override public D1QueryResult query(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
            return new D1QueryResult(new ArrayList<Map<String, Object>>(), 0L);
        }
        @Override public List<D1QueryResult> batch(List<D1Statement> values) {
            statements = values;
            List<D1QueryResult> results = new ArrayList<D1QueryResult>();
            for (int index = 0; index < values.size(); index++) {
                results.add(new D1QueryResult(new ArrayList<Map<String, Object>>(), 0L));
            }
            return results;
        }
    }
}
