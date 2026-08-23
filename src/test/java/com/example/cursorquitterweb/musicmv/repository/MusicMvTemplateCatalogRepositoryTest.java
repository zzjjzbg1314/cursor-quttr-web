package com.example.cursorquitterweb.musicmv.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest;
import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1QueryResult;
import com.example.cursorquitterweb.musicmv.service.D1Statement;
import com.fasterxml.jackson.databind.ObjectMapper;

class MusicMvTemplateCatalogRepositoryTest {
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
        request.getSlots().add(slot);
        return request;
    }

    private String hash(char value) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 64; index++) text.append(value);
        return text.toString();
    }

    private static class CapturingD1 extends D1DatabaseClient {
        private List<D1Statement> statements = new ArrayList<D1Statement>();
        CapturingD1() { super(new ObjectMapper()); }
        @Override public List<D1QueryResult> batch(List<D1Statement> values) {
            statements = values;
            return new ArrayList<D1QueryResult>();
        }
    }
}
