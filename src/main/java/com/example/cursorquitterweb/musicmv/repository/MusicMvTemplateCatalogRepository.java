package com.example.cursorquitterweb.musicmv.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest;
import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1Statement;
import com.example.cursorquitterweb.musicmv.support.IdUtils;

@Repository
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvTemplateCatalogRepository {
    private final D1DatabaseClient d1;

    public MusicMvTemplateCatalogRepository(D1DatabaseClient d1) {
        this.d1 = d1;
    }

    public List<Map<String, Object>> categories() {
        return d1.query("SELECT category_key, name_zh, name_en, sort_order "
                + "FROM template_categories WHERE enabled=1 ORDER BY sort_order, category_key").getRows();
    }

    public Map<String, Object> category(String categoryKey) {
        return d1.query("SELECT category_key, enabled FROM template_categories "
                + "WHERE category_key=? LIMIT 1", categoryKey).firstRow();
    }

    public List<Map<String, Object>> templates(String locale, String status, String visibility,
                                                String categoryKey, String keyword,
                                                int limit, int offset) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT t.template_id, t.slug, t.category_key, t.tags_json, t.status, ")
                .append("t.visibility, t.current_version_id, t.sort_order, t.revision, ")
                .append("t.created_at, t.updated_at, t.published_at, ")
                .append("COALESCE(req.name, en.name, zh.name, t.slug) AS display_name, ")
                .append("COALESCE(req.description, en.description, zh.description, '') AS description, ")
                .append("v.version_number, v.width, v.height, v.fps, v.duration_seconds, ")
                .append("v.cycle_duration_seconds, v.slot_count, v.validation_status, ")
                .append("CASE WHEN v.source_availability='available' AND rn.status='online' ")
                .append("AND rn.last_seen_at>=datetime('now','-90 seconds') ")
                .append("THEN 'available' ELSE 'unavailable' END AS source_availability, ")
                .append("cover.provider AS cover_provider, cover.provider_asset_id AS cover_asset_id, ")
                .append("cover.provider_details_json AS cover_provider_details_json, ")
                .append("preview.provider AS preview_provider, preview.provider_asset_id AS preview_asset_id, ")
                .append("preview.provider_details_json AS preview_provider_details_json ")
                .append("FROM templates t ")
                .append("LEFT JOIN template_translations req ON req.template_id=t.template_id AND req.locale=? ")
                .append("LEFT JOIN template_translations en ON en.template_id=t.template_id AND en.locale='en' ")
                .append("LEFT JOIN template_translations zh ON zh.template_id=t.template_id AND zh.locale='zh-CN' ")
                .append("LEFT JOIN template_versions v ON v.version_id=t.current_version_id ")
                .append("LEFT JOIN renderer_nodes rn ON rn.node_id=v.source_node_id ")
                .append("LEFT JOIN template_media cover ON cover.version_id=t.current_version_id ")
                .append("AND cover.media_role='cover' AND cover.status='ready' ")
                .append("LEFT JOIN template_media preview ON preview.version_id=t.current_version_id ")
                .append("AND preview.media_role='full_mv' AND preview.status='ready' ")
                .append("WHERE t.deleted_at IS NULL ");
        params.add(locale);
        if (status != null) { sql.append("AND t.status=? "); params.add(status); }
        if (visibility != null) { sql.append("AND t.visibility=? "); params.add(visibility); }
        if (categoryKey != null) { sql.append("AND t.category_key=? "); params.add(categoryKey); }
        if (keyword != null) {
            sql.append("AND (lower(t.slug) LIKE ? OR lower(COALESCE(req.name,en.name,zh.name,'')) LIKE ? ")
                    .append("OR lower(t.tags_json) LIKE ?) ");
            String like = "%" + keyword.toLowerCase() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sql.append("ORDER BY t.sort_order DESC, t.published_at DESC, t.updated_at DESC ")
                .append("LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(limit));
        params.add(Integer.valueOf(offset));
        return d1.query(sql.toString(), params).getRows();
    }

    public long templateCount(String status, String visibility, String categoryKey, String keyword) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS total FROM templates t WHERE t.deleted_at IS NULL ");
        if (status != null) { sql.append("AND t.status=? "); params.add(status); }
        if (visibility != null) { sql.append("AND t.visibility=? "); params.add(visibility); }
        if (categoryKey != null) { sql.append("AND t.category_key=? "); params.add(categoryKey); }
        if (keyword != null) {
            sql.append("AND (lower(t.slug) LIKE ? OR lower(t.tags_json) LIKE ? OR EXISTS (")
                    .append("SELECT 1 FROM template_translations x WHERE x.template_id=t.template_id ")
                    .append("AND lower(x.name) LIKE ?)) ");
            String like = "%" + keyword.toLowerCase() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        Map<String, Object> row = d1.query(sql.toString(), params).firstRow();
        Object value = row == null ? null : row.get("total");
        return value instanceof Number ? ((Number) value).longValue()
                : value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    public Map<String, Object> template(String templateId) {
        return d1.query("SELECT template_id, slug, default_locale, category_key, tags_json, status, "
                + "visibility, current_version_id, sort_order, revision, created_at, updated_at, "
                + "published_at FROM templates WHERE template_id=? AND deleted_at IS NULL LIMIT 1",
                templateId).firstRow();
    }

    public List<Map<String, Object>> translations(String templateId) {
        return d1.query("SELECT locale, name, description, seo_title, seo_description "
                + "FROM template_translations WHERE template_id=? ORDER BY locale", templateId).getRows();
    }

    public List<Map<String, Object>> versions(String templateId) {
        return d1.query("SELECT v.version_id, v.version_number, v.status, v.width, v.height, v.fps, v.duration_seconds, "
                + "v.base_duration_seconds, v.cycle_duration_seconds, v.slot_count, v.validation_status, "
                + "v.validation_render_job_id, v.validation_master_sha256, v.draft_snapshot_sha256, "
                + "v.timeline_evidence_sha256, v.native_runtime_version, v.native_runtime_sha256, "
                + "v.renderer_version, v.source_node_id, v.source_local_key, v.source_availability, "
                + "CASE WHEN v.source_availability='available' AND rn.status='online' "
                + "AND rn.last_seen_at>=datetime('now','-90 seconds') "
                + "THEN 'available' ELSE 'unavailable' END AS effective_source_availability, "
                + "v.last_source_verified_at, v.source_provenance_json, v.created_at, v.published_at "
                + "FROM template_versions v LEFT JOIN renderer_nodes rn ON rn.node_id=v.source_node_id "
                + "WHERE v.template_id=? ORDER BY v.version_number DESC", templateId).getRows();
    }

    public Map<String, Object> version(String templateId, String versionId) {
        return d1.query("SELECT v.*,CASE WHEN v.source_availability='available' AND rn.status='online' "
                        + "AND rn.last_seen_at>=datetime('now','-90 seconds') "
                        + "THEN 'available' ELSE 'unavailable' END AS effective_source_availability "
                        + "FROM template_versions v LEFT JOIN renderer_nodes rn ON rn.node_id=v.source_node_id "
                        + "WHERE v.template_id=? AND v.version_id=? LIMIT 1",
                templateId, versionId).firstRow();
    }

    public Map<String, Object> versionByValidationJob(String validationRenderJobId) {
        return d1.query("SELECT version_id, template_id, draft_snapshot_sha256, validation_master_sha256, "
                + "status FROM template_versions WHERE validation_render_job_id=? LIMIT 1",
                validationRenderJobId).firstRow();
    }

    public int nextVersionNumber(String templateId) {
        Map<String, Object> row = d1.query("SELECT COALESCE(MAX(version_number),0)+1 AS next_number "
                + "FROM template_versions WHERE template_id=?", templateId).firstRow();
        Object value = row == null ? null : row.get("next_number");
        return value instanceof Number ? ((Number) value).intValue()
                : value == null ? 1 : Integer.parseInt(String.valueOf(value));
    }

    public List<Map<String, Object>> slots(String versionId) {
        return d1.query("SELECT slot_id, slot_key, slot_type, display_name, timeline_order, aspect_ratio, "
                + "crop_policy, repeat_policy, is_required, material_id, material_group "
                + "FROM template_slots WHERE version_id=? ORDER BY timeline_order, slot_key", versionId).getRows();
    }

    public List<Map<String, Object>> media(String versionId) {
        return d1.query("SELECT media_id, media_role, provider, provider_asset_id, status, source_sha256, "
                + "source_size_bytes, width, height, duration_seconds, provider_details_json, "
                + "error_message, created_at, updated_at, ready_at FROM template_media "
                + "WHERE version_id=? ORDER BY media_role", versionId).getRows();
    }

    public Map<String, Object> mediaByRole(String versionId, String role) {
        return d1.query("SELECT * FROM template_media WHERE version_id=? AND media_role=? LIMIT 1",
                versionId, role).firstRow();
    }

    public Map<String, Object> mediaById(String templateId, String versionId, String mediaId) {
        return d1.query("SELECT * FROM template_media WHERE template_id=? AND version_id=? "
                + "AND media_id=? LIMIT 1", templateId, versionId, mediaId).firstRow();
    }

    public void promote(TemplatePromotionRequest request, String versionId, int versionNumber,
                        String tagsJson, String sourceProvenanceJson, String evidenceJson) {
        List<D1Statement> statements = new ArrayList<D1Statement>();
        statements.add(statement("INSERT INTO renderer_nodes "
                + "(node_id,name,status,runtime_version,runtime_sha256,last_seen_at,created_at,updated_at) "
                + "VALUES (?,?,'online',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                + "ON CONFLICT(node_id) DO UPDATE SET status='online',runtime_version=excluded.runtime_version,"
                + "runtime_sha256=excluded.runtime_sha256,last_seen_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP",
                request.getSourceNodeId(), request.getSourceNodeId(), request.getNativeRuntimeVersion(),
                request.getNativeRuntimeSha256()));
        statements.add(statement("INSERT INTO templates "
                + "(template_id,slug,default_locale,category_key,tags_json,status,visibility,sort_order,revision,created_at,updated_at) "
                + "VALUES (?,?,'zh-CN',?,?,'draft','public',0,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                + "ON CONFLICT(template_id) DO UPDATE SET slug=excluded.slug,category_key=excluded.category_key,"
                + "tags_json=excluded.tags_json,revision=templates.revision+1,updated_at=CURRENT_TIMESTAMP",
                request.getTemplateId(), request.getSlug(), request.getCategoryKey(), tagsJson));
        statements.add(translation(request.getTemplateId(), "zh-CN", request.getNameZh(), request.getDescriptionZh()));
        statements.add(translation(request.getTemplateId(), "en", request.getNameEn(), request.getDescriptionEn()));
        statements.add(statement("INSERT INTO template_versions "
                + "(version_id,template_id,version_number,status,width,height,fps,duration_seconds,"
                + "base_duration_seconds,cycle_duration_seconds,slot_count,validation_status,"
                + "validation_render_job_id,validation_master_sha256,draft_snapshot_sha256,"
                + "timeline_evidence_sha256,native_runtime_version,native_runtime_sha256,renderer_version,"
                + "source_node_id,source_local_key,source_availability,last_source_verified_at,"
                + "source_provenance_json,created_at) VALUES (?,?,?,'validated',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                + "?,'available',CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)",
                versionId, request.getTemplateId(), Integer.valueOf(versionNumber), request.getWidth(),
                request.getHeight(), request.getFps(), request.getDurationSeconds(),
                request.getBaseDurationSeconds(), request.getCycleDurationSeconds(),
                Integer.valueOf(request.getSlots().size()), request.getSemanticIntegrity(),
                request.getValidationRenderJobId(), request.getValidationMasterSha256().toLowerCase(),
                request.getDraftSnapshotSha256().toLowerCase(), request.getTimelineEvidenceSha256().toLowerCase(),
                request.getNativeRuntimeVersion(), request.getNativeRuntimeSha256().toLowerCase(),
                request.getRendererVersion(), request.getSourceNodeId(), request.getSourceLocalKey(),
                sourceProvenanceJson));
        for (TemplatePromotionRequest.Slot slot : request.getSlots()) {
            statements.add(statement("INSERT INTO template_slots "
                    + "(slot_id,version_id,slot_key,slot_type,display_name,timeline_order,aspect_ratio,"
                    + "crop_policy,repeat_policy,is_required,material_id,material_group) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    IdUtils.token("slot"), versionId, slot.getSlotKey(), slot.getSlotType(),
                    slot.getDisplayName(), slot.getTimelineOrder(), slot.getAspectRatio(),
                    slot.getCropPolicy(), slot.getRepeatPolicy(),
                    Integer.valueOf(Boolean.FALSE.equals(slot.getRequired()) ? 0 : 1),
                    slot.getMaterialId(), slot.getMaterialGroup()));
        }
        statements.add(statement("INSERT INTO template_validation_records "
                + "(validation_id,version_id,render_job_id,semantic_integrity,video_encode_count,"
                + "intermediate_video_count,external_resource_read_count,missing_resource_count,"
                + "renderer_version,elapsed_seconds,evidence_json,validated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                IdUtils.token("val"), versionId, request.getValidationRenderJobId(),
                request.getSemanticIntegrity(), request.getVideoEncodeCount(),
                request.getIntermediateVideoCount(), request.getExternalResourceReadCount(),
                request.getMissingResourceCount(), request.getRendererVersion(),
                request.getValidationElapsedSeconds(), evidenceJson));
        d1.batch(statements);
    }

    public void updateMetadata(String templateId, String categoryKey, String tagsJson,
                               String nameZh, String descriptionZh, String nameEn,
                               String descriptionEn, String visibility, int sortOrder) {
        d1.batch(Arrays.asList(
                statement("UPDATE templates SET category_key=?,tags_json=?,visibility=?,sort_order=?,"
                        + "revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE template_id=? AND deleted_at IS NULL",
                        categoryKey, tagsJson, visibility, Integer.valueOf(sortOrder), templateId),
                translation(templateId, "zh-CN", nameZh, descriptionZh),
                translation(templateId, "en", nameEn, descriptionEn)));
    }

    public void upsertMedia(String mediaId, String templateId, String versionId, String role,
                            String provider, String providerAssetId, String status, String sha256,
                            long sizeBytes, Integer width, Integer height, Double duration,
                            String providerDetailsJson) {
        d1.query("INSERT INTO template_media "
                + "(media_id,template_id,version_id,media_role,provider,provider_asset_id,status,"
                + "source_sha256,source_size_bytes,width,height,duration_seconds,provider_details_json,"
                + "created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                + "ON CONFLICT(version_id,media_role) DO UPDATE SET provider=excluded.provider,"
                + "provider_asset_id=excluded.provider_asset_id,status=excluded.status,"
                + "source_sha256=excluded.source_sha256,source_size_bytes=excluded.source_size_bytes,"
                + "width=excluded.width,height=excluded.height,duration_seconds=excluded.duration_seconds,"
                + "provider_details_json=excluded.provider_details_json,error_message=NULL,"
                + "ready_at=NULL,updated_at=CURRENT_TIMESTAMP",
                mediaId, templateId, versionId, role, provider, providerAssetId, status,
                sha256.toLowerCase(), Long.valueOf(sizeBytes), width, height, duration, providerDetailsJson);
    }

    public void markMediaReady(String mediaId, String providerDetailsJson) {
        d1.query("UPDATE template_media SET status='ready',provider_details_json=?,error_message=NULL,"
                + "ready_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE media_id=?",
                providerDetailsJson, mediaId);
    }

    public void publish(String templateId, String versionId) {
        d1.batch(Arrays.asList(
                statement("UPDATE template_versions SET status='published',published_at=CURRENT_TIMESTAMP "
                        + "WHERE template_id=? AND version_id=? AND validation_status='exact' "
                        + "AND source_availability='available'", templateId, versionId),
                statement("UPDATE templates SET status='published',current_version_id=?,revision=revision+1,"
                        + "published_at=COALESCE(published_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP "
                        + "WHERE template_id=? AND deleted_at IS NULL", versionId, templateId)));
    }

    public void setOffline(String templateId) {
        d1.query("UPDATE templates SET status='offline',revision=revision+1,updated_at=CURRENT_TIMESTAMP "
                + "WHERE template_id=? AND deleted_at IS NULL", templateId);
    }

    public Map<String, Object> readiness() {
        return d1.query("SELECT "
                + "(SELECT schema_version FROM music_mv_schema_metadata WHERE schema_key='core') "
                + "AS schema_version, "
                + "(SELECT schema_sha256 FROM music_mv_schema_metadata WHERE schema_key='core') "
                + "AS schema_sha256, "
                + "(SELECT COUNT(*) FROM template_categories WHERE enabled=1) AS category_count, "
                + "(SELECT COUNT(*) FROM templates WHERE deleted_at IS NULL) AS template_count, "
                + "(SELECT COUNT(*) FROM template_versions) AS version_count, "
                + "(SELECT COUNT(*) FROM template_slots) AS slot_count, "
                + "(SELECT COUNT(*) FROM template_media WHERE status='ready') AS ready_media_count").firstRow();
    }

    private D1Statement translation(String templateId, String locale, String name, String description) {
        return statement("INSERT INTO template_translations (template_id,locale,name,description) "
                + "VALUES (?,?,?,?) ON CONFLICT(template_id,locale) DO UPDATE SET name=excluded.name,"
                + "description=excluded.description", templateId, locale, name,
                description == null ? "" : description);
    }

    private D1Statement statement(String sql, Object... params) {
        return new D1Statement(sql, Arrays.asList(params));
    }
}
