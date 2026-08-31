package com.example.cursorquitterweb.musicmv.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest;
import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1QueryResult;
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
        return d1.query("SELECT category_key,parent_key,level,slug_path,is_selectable,"
                + "name_zh,name_en,sort_order "
                + "FROM template_categories WHERE enabled=1 ORDER BY sort_order, category_key").getRows();
    }

    public Map<String, Object> category(String categoryKey) {
        return d1.query("SELECT category_key,parent_key,level,slug_path,is_selectable,enabled "
                + "FROM template_categories WHERE category_key=? LIMIT 1", categoryKey).firstRow();
    }

    public List<Map<String, Object>> collections(String parentCategoryKey) {
        if (parentCategoryKey == null) {
            return d1.query("SELECT * FROM template_collections WHERE enabled=1 "
                    + "ORDER BY sort_order,collection_key").getRows();
        }
        return d1.query("SELECT * FROM template_collections WHERE enabled=1 "
                        + "AND parent_category_key=? ORDER BY sort_order,collection_key",
                parentCategoryKey).getRows();
    }

    public Map<String, Object> collectionBySlug(String slug) {
        return d1.query("SELECT * FROM template_collections WHERE enabled=1 AND slug=? LIMIT 1",
                slug).firstRow();
    }

    public Map<String, Object> collection(String collectionKey) {
        return d1.query("SELECT collection_key,slug,parent_category_key,enabled "
                        + "FROM template_collections WHERE collection_key=? LIMIT 1",
                collectionKey).firstRow();
    }

    public List<Map<String, Object>> relatedCollections(String collectionKey) {
        return d1.query("SELECT c.* FROM template_collection_relations r "
                        + "JOIN template_collections c ON c.collection_key=r.related_collection_key "
                        + "WHERE r.collection_key=? AND c.enabled=1 "
                        + "ORDER BY r.sort_order,c.sort_order,c.collection_key",
                collectionKey).getRows();
    }

    public List<Map<String, Object>> templateCollections(String templateId) {
        return d1.query("SELECT c.collection_key,c.slug,c.parent_category_key,c.keyword,"
                        + "c.name_zh,c.name_en,c.description_zh,c.description_en,c.seo_title,"
                        + "c.seo_description,c.sort_order FROM template_collection_items i "
                        + "JOIN template_collections c ON c.collection_key=i.collection_key "
                        + "WHERE i.template_id=? AND c.enabled=1 "
                        + "ORDER BY i.sort_order DESC,c.sort_order,c.collection_key",
                templateId).getRows();
    }

    public List<Map<String, Object>> templateCategories(String templateId) {
        return d1.query("SELECT i.category_key,i.is_primary,i.source,i.confidence,i.evidence_json,"
                        + "c.name_zh,c.name_en FROM template_category_items i "
                        + "JOIN template_categories c ON c.category_key=i.category_key "
                        + "WHERE i.template_id=? AND c.enabled=1 "
                        + "ORDER BY i.is_primary DESC,i.confidence DESC,c.sort_order,c.category_key",
                templateId).getRows();
    }

    public Map<String, Object> templateSourceMetadata(String templateId) {
        return d1.query("SELECT * FROM template_source_metadata WHERE template_id=? LIMIT 1",
                templateId).firstRow();
    }

    public List<Map<String, Object>> templates(String locale, String status, String visibility,
                                                String categoryKey, String collectionKey,
                                                String keyword, Integer minSlots, Integer maxSlots,
                                                Double minDuration, Double maxDuration,
                                                String aspectRatio,
                                                int limit, int offset) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT t.template_id, t.capcut_template_id, t.slug, t.category_key, t.tags_json, t.status, ")
                .append("t.visibility, t.current_version_id, t.sort_order, t.revision, ")
                .append("t.created_at, t.updated_at, t.published_at, ")
                .append("COALESCE((SELECT json_group_array(category_key) FROM (")
                .append("SELECT i.category_key FROM template_category_items i ")
                .append("JOIN template_categories c ON c.category_key=i.category_key ")
                .append("WHERE i.template_id=t.template_id AND c.enabled=1 ")
                .append("ORDER BY i.is_primary DESC,i.confidence DESC,c.sort_order,c.category_key)), '[]') ")
                .append("AS category_keys_json, ")
                .append("COALESCE(req.name, en.name, zh.name, t.slug) AS display_name, ")
                .append("COALESCE(req.description, en.description, zh.description, '') AS description, ")
                .append("v.version_number, v.width, v.height, v.fps, v.duration_seconds, ")
                .append("v.cycle_duration_seconds, v.slot_count, v.validation_status, v.renderer_version, ")
                .append("v.source_availability AS source_availability, ")
                .append("cover.provider AS cover_provider, cover.provider_asset_id AS cover_asset_id, ")
                .append("cover.provider_details_json AS cover_provider_details_json, ")
                .append("preview.provider AS preview_provider, preview.provider_asset_id AS preview_asset_id, ")
                .append("preview.provider_details_json AS preview_provider_details_json ")
                .append("FROM templates t ")
                .append("LEFT JOIN template_translations req ON req.template_id=t.template_id AND req.locale=? ")
                .append("LEFT JOIN template_translations en ON en.template_id=t.template_id AND en.locale='en' ")
                .append("LEFT JOIN template_translations zh ON zh.template_id=t.template_id AND zh.locale='zh-CN' ")
                .append("LEFT JOIN template_versions v ON v.version_id=t.current_version_id ")
                .append("LEFT JOIN template_media cover ON cover.version_id=t.current_version_id ")
                .append("AND cover.media_role='cover' AND cover.status='ready' ")
                .append("LEFT JOIN template_media preview ON preview.version_id=t.current_version_id ")
                // A forced resync changes the full-MV row to processing before the
                // replacement becomes playable. Cloudflare already provides a stable
                // thumbnail at that point, so keep exposing it as the catalog cover.
                // Playback readiness is still enforced by the media status elsewhere.
                .append("AND preview.media_role='full_mv' ")
                .append("WHERE t.deleted_at IS NULL ");
        params.add(locale);
        if (status != null) { sql.append("AND t.status=? "); params.add(status); }
        if (visibility != null) { sql.append("AND t.visibility=? "); params.add(visibility); }
        if (categoryKey != null) {
            sql.append("AND (EXISTS (SELECT 1 FROM template_category_items ti "
                    + "WHERE ti.template_id=t.template_id AND ti.category_key=?) OR EXISTS ("
                    + "SELECT 1 FROM template_category_items ti JOIN template_categories tc "
                    + "ON tc.category_key=ti.category_key WHERE ti.template_id=t.template_id "
                    + "AND tc.parent_key=?)) ");
            params.add(categoryKey); params.add(categoryKey);
        }
        if (collectionKey != null) {
            sql.append("AND EXISTS (SELECT 1 FROM template_collection_items ci "
                    + "WHERE ci.template_id=t.template_id AND ci.collection_key=?) ");
            params.add(collectionKey);
        }
        if (keyword != null) {
            sql.append("AND (lower(t.slug) LIKE ? OR lower(COALESCE(req.name,en.name,zh.name,'')) LIKE ? ")
                    .append("OR lower(COALESCE(req.description,en.description,zh.description,'')) LIKE ? ")
                    .append("OR EXISTS (SELECT 1 FROM template_source_metadata sm WHERE sm.template_id=t.template_id ")
                    .append("AND (lower(sm.source_title) LIKE ? OR lower(sm.source_description) LIKE ? ")
                    .append("OR lower(sm.source_category) LIKE ? OR lower(sm.source_search_keyword) LIKE ? ")
                    .append("OR lower(sm.source_hashtags_json) LIKE ?))) ");
            String like = "%" + keyword.toLowerCase() + "%";
            params.add(like); params.add(like); params.add(like); params.add(like);
            params.add(like); params.add(like); params.add(like); params.add(like);
        }
        appendTechnicalFilters(sql, params, minSlots, maxSlots, minDuration, maxDuration, aspectRatio);
        sql.append("ORDER BY t.sort_order DESC, t.published_at DESC, t.updated_at DESC ")
                .append("LIMIT ? OFFSET ?");
        params.add(Integer.valueOf(limit));
        params.add(Integer.valueOf(offset));
        return d1.query(sql.toString(), params).getRows();
    }

    public long templateCount(String status, String visibility, String categoryKey,
                              String collectionKey, String keyword, Integer minSlots,
                              Integer maxSlots, Double minDuration, Double maxDuration,
                              String aspectRatio) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS total FROM templates t "
                + "LEFT JOIN template_versions v ON v.version_id=t.current_version_id "
                + "WHERE t.deleted_at IS NULL ");
        if (status != null) { sql.append("AND t.status=? "); params.add(status); }
        if (visibility != null) { sql.append("AND t.visibility=? "); params.add(visibility); }
        if (categoryKey != null) {
            sql.append("AND (EXISTS (SELECT 1 FROM template_category_items ti "
                    + "WHERE ti.template_id=t.template_id AND ti.category_key=?) OR EXISTS ("
                    + "SELECT 1 FROM template_category_items ti JOIN template_categories tc "
                    + "ON tc.category_key=ti.category_key WHERE ti.template_id=t.template_id "
                    + "AND tc.parent_key=?)) ");
            params.add(categoryKey); params.add(categoryKey);
        }
        if (collectionKey != null) {
            sql.append("AND EXISTS (SELECT 1 FROM template_collection_items ci "
                    + "WHERE ci.template_id=t.template_id AND ci.collection_key=?) ");
            params.add(collectionKey);
        }
        if (keyword != null) {
            sql.append("AND (lower(t.slug) LIKE ? OR EXISTS (")
                    .append("SELECT 1 FROM template_translations x WHERE x.template_id=t.template_id ")
                    .append("AND lower(x.name) LIKE ?) OR EXISTS (SELECT 1 FROM template_source_metadata sm ")
                    .append("WHERE sm.template_id=t.template_id AND (lower(sm.source_title) LIKE ? ")
                    .append("OR lower(sm.source_description) LIKE ? OR lower(sm.source_category) LIKE ? ")
                    .append("OR lower(sm.source_search_keyword) LIKE ? OR lower(sm.source_hashtags_json) LIKE ?))) ");
            String like = "%" + keyword.toLowerCase() + "%";
            params.add(like); params.add(like); params.add(like); params.add(like);
            params.add(like); params.add(like); params.add(like);
        }
        appendTechnicalFilters(sql, params, minSlots, maxSlots, minDuration, maxDuration, aspectRatio);
        Map<String, Object> row = d1.query(sql.toString(), params).firstRow();
        Object value = row == null ? null : row.get("total");
        return value instanceof Number ? ((Number) value).longValue()
                : value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private void appendTechnicalFilters(StringBuilder sql, List<Object> params,
                                        Integer minSlots, Integer maxSlots,
                                        Double minDuration, Double maxDuration,
                                        String aspectRatio) {
        if (minSlots != null) { sql.append("AND v.slot_count>=? "); params.add(minSlots); }
        if (maxSlots != null) { sql.append("AND v.slot_count<=? "); params.add(maxSlots); }
        if (minDuration != null) { sql.append("AND v.duration_seconds>=? "); params.add(minDuration); }
        if (maxDuration != null) { sql.append("AND v.duration_seconds<=? "); params.add(maxDuration); }
        if (aspectRatio != null) {
            String[] parts = aspectRatio.split(":", -1);
            if (parts.length == 2) {
                try {
                    int width = Integer.parseInt(parts[0]);
                    int height = Integer.parseInt(parts[1]);
                    sql.append("AND v.width*?=v.height*? ");
                    params.add(Integer.valueOf(height));
                    params.add(Integer.valueOf(width));
                } catch (NumberFormatException ignored) {
                    // The service rejects malformed ratios before the repository is called.
                }
            }
        }
    }

    public Map<String, Object> template(String templateId) {
        return d1.query("SELECT template_id, capcut_template_id, slug, default_locale, category_key, tags_json, status, "
                + "visibility, current_version_id, sort_order, revision, created_at, updated_at, "
                + "published_at FROM templates WHERE template_id=? AND deleted_at IS NULL LIMIT 1",
                templateId).firstRow();
    }

    /**
     * Loads the complete template graph in one Cloudflare D1 batch request.
     *
     * The statements remain independent reads, but D1 executes them through a
     * single HTTP round trip. This avoids the former 5 + (3 * versionCount)
     * sequence of remote requests on the template detail path.
     */
    public TemplateDetailRows templateDetail(String templateId) {
        List<D1QueryResult> results = d1.batch(Arrays.asList(
                D1Statement.of("SELECT template_id, capcut_template_id, slug, default_locale, "
                                + "category_key, tags_json, status, visibility, current_version_id, "
                                + "sort_order, revision, created_at, updated_at, published_at "
                                + "FROM templates WHERE template_id=? AND deleted_at IS NULL LIMIT 1",
                        templateId),
                D1Statement.of("SELECT i.category_key,i.is_primary,i.source,i.confidence,i.evidence_json,"
                                + "c.name_zh,c.name_en FROM template_category_items i "
                                + "JOIN template_categories c ON c.category_key=i.category_key "
                                + "WHERE i.template_id=? AND c.enabled=1 "
                                + "ORDER BY i.is_primary DESC,i.confidence DESC,c.sort_order,c.category_key",
                        templateId),
                D1Statement.of("SELECT * FROM template_source_metadata WHERE template_id=? LIMIT 1",
                        templateId),
                D1Statement.of("SELECT locale, name, description, seo_title, seo_description "
                                + "FROM template_translations WHERE template_id=? ORDER BY locale",
                        templateId),
                D1Statement.of("SELECT v.version_id, v.version_number, v.status, v.width, v.height, v.fps, "
                                + "v.duration_seconds, v.base_duration_seconds, v.cycle_duration_seconds, "
                                + "v.slot_count, v.validation_status, v.validation_render_job_id, "
                                + "v.validation_master_sha256, v.draft_snapshot_sha256, "
                                + "v.timeline_evidence_sha256, v.native_runtime_version, "
                                + "v.native_runtime_sha256, v.renderer_version, v.source_node_id, "
                                + "v.source_local_key, v.source_availability, "
                                + "v.source_availability AS effective_source_availability, "
                                + "v.last_source_verified_at, v.source_provenance_json, "
                                + "v.created_at, v.published_at FROM template_versions v "
                                + "WHERE v.template_id=? ORDER BY v.version_number DESC",
                        templateId),
                D1Statement.of("SELECT s.version_id,s.slot_id,s.slot_key,s.slot_type,s.display_name,"
                                + "s.timeline_order,s.aspect_ratio,s.crop_policy,s.repeat_policy,"
                                + "s.is_required,s.material_id,s.material_group FROM template_slots s "
                                + "JOIN template_versions v ON v.version_id=s.version_id "
                                + "WHERE v.template_id=? ORDER BY s.version_id,s.timeline_order,s.slot_key",
                        templateId),
                D1Statement.of("SELECT m.version_id,m.media_id,m.media_role,m.provider,"
                                + "m.provider_asset_id,m.status,m.source_sha256,m.source_size_bytes,"
                                + "m.width,m.height,m.duration_seconds,m.provider_details_json,"
                                + "m.error_message,m.created_at,m.updated_at,m.ready_at "
                                + "FROM template_media m JOIN template_versions v ON v.version_id=m.version_id "
                                + "WHERE v.template_id=? ORDER BY m.version_id,m.media_role",
                        templateId),
                D1Statement.of("SELECT s.version_id,s.template_id,s.schema_version,s.manifest_sha256,"
                                + "s.status,s.scene_json,s.created_at,s.updated_at "
                                + "FROM template_browser_scenes s JOIN template_versions v "
                                + "ON v.version_id=s.version_id WHERE v.template_id=? "
                                + "ORDER BY s.version_id",
                        templateId)
        ));
        if (results.size() != 8) {
            throw new IllegalStateException("Template detail D1 batch returned " + results.size()
                    + " result sets; expected 8");
        }
        return new TemplateDetailRows(
                results.get(0).firstRow(), results.get(1).getRows(), results.get(2).firstRow(),
                results.get(3).getRows(), results.get(4).getRows(), results.get(5).getRows(),
                results.get(6).getRows(), results.get(7).getRows());
    }

    public static final class TemplateDetailRows {
        private final Map<String, Object> template;
        private final List<Map<String, Object>> categories;
        private final Map<String, Object> sourceMetadata;
        private final List<Map<String, Object>> translations;
        private final List<Map<String, Object>> versions;
        private final List<Map<String, Object>> slots;
        private final List<Map<String, Object>> media;
        private final List<Map<String, Object>> browserScenes;

        public TemplateDetailRows(Map<String, Object> template,
                                  List<Map<String, Object>> categories,
                                  Map<String, Object> sourceMetadata,
                                  List<Map<String, Object>> translations,
                                  List<Map<String, Object>> versions,
                                  List<Map<String, Object>> slots,
                                  List<Map<String, Object>> media,
                                  List<Map<String, Object>> browserScenes) {
            this.template = template;
            this.categories = categories;
            this.sourceMetadata = sourceMetadata;
            this.translations = translations;
            this.versions = versions;
            this.slots = slots;
            this.media = media;
            this.browserScenes = browserScenes;
        }

        public Map<String, Object> getTemplate() { return template; }
        public List<Map<String, Object>> getCategories() { return categories; }
        public Map<String, Object> getSourceMetadata() { return sourceMetadata; }
        public List<Map<String, Object>> getTranslations() { return translations; }
        public List<Map<String, Object>> getVersions() { return versions; }
        public List<Map<String, Object>> getSlots() { return slots; }
        public List<Map<String, Object>> getMedia() { return media; }
        public List<Map<String, Object>> getBrowserScenes() { return browserScenes; }
    }

    public List<Map<String, Object>> templatesByCapCutTemplateIds(List<String> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) return new ArrayList<Map<String, Object>>();
        StringBuilder placeholders = new StringBuilder();
        List<Object> params = new ArrayList<Object>();
        for (String templateId : templateIds) {
            if (placeholders.length() > 0) placeholders.append(',');
            placeholders.append('?');
            params.add(templateId);
        }
        return d1.query("SELECT template_id,capcut_template_id,status,current_version_id "
                + "FROM templates WHERE deleted_at IS NULL AND capcut_template_id IN ("
                + placeholders + ")", params).getRows();
    }

    public void bindCapCutTemplateIdentity(String templateId, String capcutTemplateId) {
        d1.query("UPDATE templates SET capcut_template_id=?,revision=revision+1,"
                        + "updated_at=CURRENT_TIMESTAMP WHERE template_id=? AND deleted_at IS NULL",
                capcutTemplateId, templateId);
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
                + "v.source_availability AS effective_source_availability, "
                + "v.last_source_verified_at, v.source_provenance_json, v.created_at, v.published_at "
                + "FROM template_versions v "
                + "WHERE v.template_id=? ORDER BY v.version_number DESC", templateId).getRows();
    }

    public Map<String, Object> version(String templateId, String versionId) {
        return d1.query("SELECT v.*,v.source_availability AS effective_source_availability "
                        + "FROM template_versions v "
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

    public Map<String, Object> browserScene(String versionId) {
        return d1.query("SELECT version_id,template_id,schema_version,manifest_sha256,status,"
                        + "scene_json,created_at,updated_at FROM template_browser_scenes "
                        + "WHERE version_id=? LIMIT 1", versionId).firstRow();
    }

    public void upsertBrowserScene(String templateId, String versionId, String schemaVersion,
                                   String manifestSha256, String status, String sceneJson) {
        d1.query("INSERT INTO template_browser_scenes "
                        + "(version_id,template_id,schema_version,manifest_sha256,status,scene_json,"
                        + "created_at,updated_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT(version_id) DO UPDATE SET schema_version=excluded.schema_version,"
                        + "manifest_sha256=excluded.manifest_sha256,status=excluded.status,"
                        + "scene_json=excluded.scene_json,updated_at=CURRENT_TIMESTAMP",
                versionId, templateId, schemaVersion, manifestSha256, status, sceneJson);
    }

    public Map<String, Object> browserParity(String versionId) {
        return d1.query("SELECT * FROM template_browser_parity_validations "
                        + "WHERE version_id=? ORDER BY updated_at DESC LIMIT 1", versionId)
                .firstRow();
    }

    public Map<String, Object> matchingBrowserParity(String versionId, String sceneManifest,
                                                     String referenceSha256,
                                                     String rendererVersion) {
        return d1.query("SELECT * FROM template_browser_parity_validations WHERE version_id=? "
                        + "AND scene_manifest_sha256=? AND reference_sha256=? "
                        + "AND renderer_version=? LIMIT 1",
                versionId, sceneManifest, referenceSha256, rendererVersion).firstRow();
    }

    public void upsertBrowserParity(String validationId, String templateId, String versionId,
                                    String sceneManifest, String referenceSha256,
                                    String rendererVersion, String status, Integer sampleCount,
                                    Double ssimThreshold, Double maeThreshold,
                                    Double averageSsim, Double minSsim, Double averageMae,
                                    Double maxMae, Double referenceDuration,
                                    Double outputDuration, String outputSha256,
                                    String reportJson) {
        d1.query("INSERT INTO template_browser_parity_validations "
                        + "(validation_id,template_id,version_id,scene_manifest_sha256,reference_sha256,"
                        + "renderer_version,status,sample_count,ssim_threshold,mae_threshold,average_ssim,"
                        + "min_ssim,average_mae,max_mae,reference_duration_seconds,output_duration_seconds,"
                        + "output_sha256,report_json,created_at,updated_at,completed_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,"
                        + "CASE WHEN ? IN ('passed','failed') THEN CURRENT_TIMESTAMP ELSE NULL END) "
                        + "ON CONFLICT(version_id,scene_manifest_sha256,reference_sha256,renderer_version) "
                        + "DO UPDATE SET status=excluded.status,sample_count=excluded.sample_count,"
                        + "ssim_threshold=excluded.ssim_threshold,mae_threshold=excluded.mae_threshold,"
                        + "average_ssim=excluded.average_ssim,min_ssim=excluded.min_ssim,"
                        + "average_mae=excluded.average_mae,max_mae=excluded.max_mae,"
                        + "reference_duration_seconds=excluded.reference_duration_seconds,"
                        + "output_duration_seconds=excluded.output_duration_seconds,"
                        + "output_sha256=excluded.output_sha256,report_json=excluded.report_json,"
                        + "updated_at=CURRENT_TIMESTAMP,completed_at=excluded.completed_at",
                validationId, templateId, versionId, sceneManifest, referenceSha256,
                rendererVersion, status, sampleCount, ssimThreshold, maeThreshold, averageSsim,
                minSsim, averageMae, maxMae, referenceDuration, outputDuration, outputSha256,
                reportJson, status);
    }

    public List<Map<String, Object>> media(String versionId) {
        return d1.query("SELECT media_id, media_role, provider, provider_asset_id, status, source_sha256, "
                + "source_size_bytes, width, height, duration_seconds, provider_details_json, "
                + "error_message, created_at, updated_at, ready_at FROM template_media "
                + "WHERE version_id=? ORDER BY media_role", versionId).getRows();
    }

    public List<Map<String, Object>> mediaForTemplate(String templateId) {
        return d1.query("SELECT media_id, media_role, provider, provider_asset_id, status "
                + "FROM template_media WHERE template_id=? ORDER BY version_id, media_role",
                templateId).getRows();
    }

    public long projectReferenceCount(String templateId) {
        return count("SELECT COUNT(*) AS total FROM music_mv_projects "
                + "WHERE template_id=? AND deleted_at IS NULL", templateId);
    }

    public long renderJobReferenceCount(String templateId) {
        return count("SELECT COUNT(*) AS total FROM music_mv_render_jobs WHERE template_id=?",
                templateId);
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
                + "(template_id,capcut_template_id,slug,default_locale,category_key,tags_json,status,visibility,sort_order,revision,created_at,updated_at) "
                + "VALUES (?,?,?,'zh-CN',?,?,'draft','public',0,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                + "ON CONFLICT(template_id) DO UPDATE SET slug=excluded.slug,category_key=excluded.category_key,"
                + "capcut_template_id=excluded.capcut_template_id,tags_json=excluded.tags_json,"
                + "revision=templates.revision+1,updated_at=CURRENT_TIMESTAMP",
                request.getTemplateId(), request.getCapcutTemplateId(), request.getSlug(),
                request.getCategoryKey(), tagsJson));
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

    public void replaceTemplateCollections(String templateId, List<String> collectionKeys,
                                           String source) {
        List<D1Statement> statements = new ArrayList<D1Statement>();
        statements.add(statement("DELETE FROM template_collection_items WHERE template_id=?",
                templateId));
        int order = collectionKeys.size();
        for (String collectionKey : collectionKeys) {
            statements.add(statement("INSERT INTO template_collection_items "
                            + "(collection_key,template_id,sort_order,source,created_at,updated_at) "
                            + "VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    collectionKey, templateId, Integer.valueOf(order--), source));
        }
        d1.batch(statements);
    }

    public void replaceTemplateCategories(String templateId, String primaryCategoryKey,
                                           List<Map<String, Object>> categories) {
        List<D1Statement> statements = new ArrayList<D1Statement>();
        statements.add(statement("DELETE FROM template_category_items WHERE template_id=?", templateId));
        for (Map<String, Object> category : categories) {
            String key = String.valueOf(category.get("categoryKey"));
            boolean primary = primaryCategoryKey.equals(key);
            statements.add(statement("INSERT INTO template_category_items "
                            + "(template_id,category_key,is_primary,source,confidence,evidence_json,created_at,updated_at) "
                            + "VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    templateId, key, Integer.valueOf(primary ? 1 : 0), category.get("source"),
                    category.get("confidence"), category.get("evidenceJson")));
        }
        statements.add(statement("UPDATE templates SET category_key=?,revision=revision+1,"
                        + "updated_at=CURRENT_TIMESTAMP WHERE template_id=? AND deleted_at IS NULL",
                primaryCategoryKey, templateId));
        d1.batch(statements);
    }

    public void upsertTemplateSourceMetadata(String templateId, String sourceTitle,
                                              String sourceDescription, String sourceCategory,
                                              String sourceSearchKeyword, String sourceHashtagsJson,
                                              String sourceUrl, boolean classificationLocked) {
        d1.query("INSERT INTO template_source_metadata "
                        + "(template_id,source_title,source_description,source_category,source_search_keyword,"
                        + "source_hashtags_json,source_url,classifier_version,classification_locked,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,'source-rules-v2',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT(template_id) DO UPDATE SET source_title=excluded.source_title,"
                        + "source_description=excluded.source_description,source_category=excluded.source_category,"
                        + "source_search_keyword=excluded.source_search_keyword,"
                        + "source_hashtags_json=excluded.source_hashtags_json,source_url=excluded.source_url,"
                        + "classification_locked=excluded.classification_locked,updated_at=CURRENT_TIMESTAMP",
                templateId, sourceTitle, sourceDescription, sourceCategory, sourceSearchKeyword,
                sourceHashtagsJson, sourceUrl, Integer.valueOf(classificationLocked ? 1 : 0));
    }

    /** Adds derived content-quality evidence without changing immutable source media hashes. */
    public void enrichVisualQuality(String versionId, Double cycleDurationSeconds,
                                    String sourceProvenanceJson) {
        d1.query("UPDATE template_versions SET cycle_duration_seconds=?,source_provenance_json=? "
                        + "WHERE version_id=? AND validation_status='exact'",
                cycleDurationSeconds, sourceProvenanceJson, versionId);
    }

    public void replaceSlots(String templateId, String versionId,
                             List<TemplatePromotionRequest.Slot> slots) {
        List<D1Statement> statements = new ArrayList<D1Statement>();
        statements.add(statement("DELETE FROM template_slots WHERE version_id=? AND EXISTS ("
                + "SELECT 1 FROM template_versions WHERE version_id=? AND template_id=?)",
                versionId, versionId, templateId));
        for (TemplatePromotionRequest.Slot slot : slots) {
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
        statements.add(statement("UPDATE template_versions SET slot_count=? "
                + "WHERE version_id=? AND template_id=?",
                Integer.valueOf(slots.size()), versionId, templateId));
        d1.batch(statements);
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
                        + "WHERE template_id=? AND version_id=? "
                        + "AND (validation_status='browser_ready' OR "
                        + "(validation_status='exact' AND source_availability='available'))",
                        templateId, versionId),
                statement("UPDATE templates SET status='published',current_version_id=?,revision=revision+1,"
                        + "published_at=COALESCE(published_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP "
                        + "WHERE template_id=? AND deleted_at IS NULL", versionId, templateId)));
    }

    public int migrateCurrentTemplatesToBrowserRendering() {
        return d1.query("UPDATE template_versions SET validation_status='browser_ready',"
                + "renderer_version='browser-canvas-v1' WHERE version_id IN ("
                + "SELECT current_version_id FROM templates WHERE deleted_at IS NULL "
                + "AND current_version_id IS NOT NULL) AND validation_status<>'browser_ready' "
                + "RETURNING version_id").getRows().size();
    }

    public void setOffline(String templateId) {
        d1.query("UPDATE templates SET status='offline',revision=revision+1,updated_at=CURRENT_TIMESTAMP "
                + "WHERE template_id=? AND deleted_at IS NULL", templateId);
    }

    /** Deletes the complete catalog graph after the service has removed provider assets. */
    public void deleteTemplate(String templateId) {
        d1.batch(Arrays.asList(
                statement("DELETE FROM template_media WHERE template_id=? AND EXISTS ("
                        + "SELECT 1 FROM templates WHERE template_id=? AND status='offline')",
                        templateId, templateId),
                statement("DELETE FROM template_slots WHERE version_id IN ("
                        + "SELECT version_id FROM template_versions WHERE template_id=?) AND EXISTS ("
                        + "SELECT 1 FROM templates WHERE template_id=? AND status='offline')",
                        templateId, templateId),
                statement("DELETE FROM template_validation_records WHERE version_id IN ("
                        + "SELECT version_id FROM template_versions WHERE template_id=?) AND EXISTS ("
                        + "SELECT 1 FROM templates WHERE template_id=? AND status='offline')",
                        templateId, templateId),
                statement("DELETE FROM template_browser_parity_validations WHERE template_id=? "
                        + "AND EXISTS (SELECT 1 FROM templates WHERE template_id=? AND status='offline')",
                        templateId, templateId),
                statement("DELETE FROM template_browser_scenes WHERE template_id=? AND EXISTS ("
                        + "SELECT 1 FROM templates WHERE template_id=? AND status='offline')",
                        templateId, templateId),
                statement("DELETE FROM template_collection_items WHERE template_id=? AND EXISTS ("
                        + "SELECT 1 FROM templates WHERE template_id=? AND status='offline')",
                        templateId, templateId),
                statement("DELETE FROM template_translations WHERE template_id=? AND EXISTS ("
                        + "SELECT 1 FROM templates WHERE template_id=? AND status='offline')",
                        templateId, templateId),
                statement("DELETE FROM template_versions WHERE template_id=? AND EXISTS ("
                        + "SELECT 1 FROM templates WHERE template_id=? AND status='offline')",
                        templateId, templateId),
                statement("DELETE FROM templates WHERE template_id=? AND status='offline'",
                        templateId)));
    }

    /**
     * Deletes a template regardless of publication state or active references.
     * User projects are detached so their own uploads and finished outputs stay intact;
     * render jobs must be removed because they have database foreign keys to the template.
     */
    public void forceDeleteTemplate(String templateId) {
        d1.batch(Arrays.asList(
                statement("DELETE FROM music_mv_render_job_events WHERE job_id IN ("
                        + "SELECT job_id FROM music_mv_render_jobs WHERE template_id=?)", templateId),
                statement("DELETE FROM music_mv_render_jobs WHERE template_id=?", templateId),
                statement("UPDATE music_mv_projects SET template_id=NULL,template_version_id=NULL,"
                        + "current_step=CASE WHEN status='draft' THEN 'template' ELSE current_step END,"
                        + "revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE template_id=?", templateId),
                statement("DELETE FROM template_media WHERE template_id=?", templateId),
                statement("DELETE FROM template_slots WHERE version_id IN ("
                        + "SELECT version_id FROM template_versions WHERE template_id=?)", templateId),
                statement("DELETE FROM template_validation_records WHERE version_id IN ("
                        + "SELECT version_id FROM template_versions WHERE template_id=?)", templateId),
                statement("DELETE FROM template_browser_parity_validations WHERE template_id=?", templateId),
                statement("DELETE FROM template_browser_scenes WHERE template_id=?", templateId),
                statement("DELETE FROM template_collection_items WHERE template_id=?", templateId),
                statement("DELETE FROM template_category_items WHERE template_id=?", templateId),
                statement("DELETE FROM template_source_metadata WHERE template_id=?", templateId),
                statement("DELETE FROM template_translations WHERE template_id=?", templateId),
                statement("DELETE FROM template_versions WHERE template_id=?", templateId),
                statement("DELETE FROM templates WHERE template_id=?", templateId)));
    }

    private long count(String sql, String templateId) {
        Map<String, Object> row = d1.query(sql, templateId).firstRow();
        Object value = row == null ? null : row.get("total");
        return value instanceof Number ? ((Number) value).longValue()
                : value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    public Map<String, Object> readiness() {
        return d1.query("SELECT "
                + "(SELECT schema_version FROM music_mv_schema_metadata WHERE schema_key='core') "
                + "AS schema_version, "
                + "(SELECT schema_sha256 FROM music_mv_schema_metadata WHERE schema_key='core') "
                + "AS schema_sha256, "
                + "(SELECT COUNT(*) FROM template_categories WHERE enabled=1) AS category_count, "
                + "(SELECT COUNT(*) FROM template_collections WHERE enabled=1) AS collection_count, "
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
