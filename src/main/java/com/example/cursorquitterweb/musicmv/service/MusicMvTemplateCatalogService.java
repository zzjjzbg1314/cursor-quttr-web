package com.example.cursorquitterweb.musicmv.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.dto.TemplateMediaUploadSessionRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateMetadataUpdateRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.service.CloudflareTemplateMediaProvider.MediaState;
import com.example.cursorquitterweb.musicmv.service.CloudflareTemplateMediaProvider.UploadSession;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.IdUtils;
import com.example.cursorquitterweb.musicmv.support.RowUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvTemplateCatalogService {
    private static final Set<String> VISIBILITIES;
    static {
        Set<String> values = new HashSet<String>();
        values.add("public"); values.add("private"); values.add("unlisted");
        VISIBILITIES = Collections.unmodifiableSet(values);
    }

    private final MusicMvTemplateCatalogRepository repository;
    private final CloudflareTemplateMediaProvider mediaProvider;
    private final D1DatabaseClient d1;
    private final ObjectMapper objectMapper;

    public MusicMvTemplateCatalogService(MusicMvTemplateCatalogRepository repository,
                                         CloudflareTemplateMediaProvider mediaProvider,
                                         D1DatabaseClient d1,
                                         ObjectMapper objectMapper) {
        this.repository = repository;
        this.mediaProvider = mediaProvider;
        this.d1 = d1;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> categories(String locale) {
        boolean english = normalizeLocale(locale).startsWith("en");
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : repository.categories()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("key", RowUtils.str(row, "category_key"));
            item.put("name", RowUtils.str(row, english ? "name_en" : "name_zh"));
            item.put("nameZh", RowUtils.str(row, "name_zh"));
            item.put("nameEn", RowUtils.str(row, "name_en"));
            item.put("sortOrder", RowUtils.integer(row, "sort_order"));
            items.add(item);
        }
        return singleton("items", items);
    }

    public Map<String, Object> list(String locale, String categoryKey, String keyword,
                                    Integer page, Integer pageSize, boolean admin,
                                    String requestedStatus) {
        String category = blankToNull(categoryKey);
        if (category != null) requireCategory(category);
        String query = blankToNull(keyword);
        int normalizedPage = page == null ? 1 : Math.max(1, page.intValue());
        int normalizedSize = pageSize == null ? 24 : Math.max(1, Math.min(100, pageSize.intValue()));
        String status = admin ? blankToNull(requestedStatus) : "published";
        String visibility = admin ? null : "public";
        long total = repository.templateCount(status, visibility, category, query);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : repository.templates(normalizeLocale(locale), status,
                visibility, category, query, normalizedSize, (normalizedPage - 1) * normalizedSize)) {
            items.add(summary(row));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", items);
        result.put("page", Integer.valueOf(normalizedPage));
        result.put("pageSize", Integer.valueOf(normalizedSize));
        result.put("total", Long.valueOf(total));
        result.put("hasMore", Boolean.valueOf((long) normalizedPage * normalizedSize < total));
        return result;
    }

    public Map<String, Object> detail(String templateId, boolean admin) {
        Map<String, Object> template = requireTemplate(templateId);
        if (!admin && (!"published".equals(RowUtils.str(template, "status"))
                || !"public".equals(RowUtils.str(template, "visibility")))) {
            throw notFound("TEMPLATE_NOT_FOUND", "Template was not found");
        }
        Map<String, Object> result = templateView(template);
        List<Map<String, Object>> translations = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : repository.translations(templateId)) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            copy(item, "locale", row, "locale");
            copy(item, "name", row, "name");
            copy(item, "description", row, "description");
            copy(item, "seoTitle", row, "seo_title");
            copy(item, "seoDescription", row, "seo_description");
            translations.add(item);
        }
        result.put("translations", translations);
        String currentVersionId = RowUtils.str(template, "current_version_id");
        List<Map<String, Object>> versions = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : repository.versions(templateId)) {
            if (!admin && !String.valueOf(currentVersionId).equals(RowUtils.str(row, "version_id"))) continue;
            Map<String, Object> version = versionView(row, admin);
            String versionId = RowUtils.str(row, "version_id");
            version.put("slots", slotViews(repository.slots(versionId)));
            version.put("media", mediaViews(repository.media(versionId)));
            versions.add(version);
        }
        result.put("versions", versions);
        return result;
    }

    public Map<String, Object> promote(TemplatePromotionRequest request) {
        requireExactValidation(request);
        requireCategory(request.getCategoryKey());
        requireUniqueSlots(request.getSlots());
        Map<String, Object> existing = repository.versionByValidationJob(request.getValidationRenderJobId());
        if (existing != null) {
            boolean same = request.getTemplateId().equals(RowUtils.str(existing, "template_id"))
                    && request.getDraftSnapshotSha256().equalsIgnoreCase(
                    RowUtils.str(existing, "draft_snapshot_sha256"))
                    && request.getValidationMasterSha256().equalsIgnoreCase(
                    RowUtils.str(existing, "validation_master_sha256"));
            if (!same) throw conflict("TEMPLATE_PROMOTION_IDEMPOTENCY_CONFLICT",
                    "Validation render job is already bound to different template evidence");
            Map<String, Object> replay = promotionView(request.getTemplateId(),
                    RowUtils.str(existing, "version_id"), RowUtils.str(existing, "status"));
            replay.put("idempotentReplay", Boolean.TRUE);
            return replay;
        }
        String versionId = IdUtils.token("tplver");
        repository.promote(request, versionId, repository.nextVersionNumber(request.getTemplateId()),
                json(request.getTags()), jsonOrEmpty(request.getSourceProvenance()),
                jsonOrEmpty(request.getValidationEvidence()));
        Map<String, Object> result = promotionView(request.getTemplateId(), versionId, "validated");
        result.put("idempotentReplay", Boolean.FALSE);
        return result;
    }

    public Map<String, Object> updateMetadata(String templateId, TemplateMetadataUpdateRequest request) {
        requireTemplate(templateId);
        requireCategory(request.getCategoryKey());
        String visibility = blankToNull(request.getVisibility());
        if (visibility == null || !VISIBILITIES.contains(visibility)) {
            throw badRequest("TEMPLATE_VISIBILITY_INVALID", "Template visibility is invalid");
        }
        repository.updateMetadata(templateId, request.getCategoryKey(), json(request.getTags()),
                request.getNameZh(), request.getDescriptionZh(), request.getNameEn(),
                request.getDescriptionEn(), visibility,
                request.getSortOrder() == null ? 0 : request.getSortOrder().intValue());
        return detail(templateId, true);
    }

    public Map<String, Object> createMediaSession(String templateId, String versionId,
                                                   boolean video,
                                                   TemplateMediaUploadSessionRequest request) {
        requireVersion(templateId, versionId);
        String expectedRole = video ? "full_mv" : "cover";
        if (!expectedRole.equals(request.getRole())) {
            throw badRequest("TEMPLATE_MEDIA_ROLE_INVALID", video
                    ? "Stream upload only accepts role full_mv"
                    : "Images upload only accepts role cover");
        }
        if (video && (request.getDurationSeconds() == null || request.getFilename() == null)) {
            throw badRequest("TEMPLATE_VIDEO_METADATA_REQUIRED",
                    "Full MV upload requires durationSeconds and filename");
        }
        Map<String, Object> existing = repository.mediaByRole(versionId, expectedRole);
        if (existing != null && request.getSourceSha256().equalsIgnoreCase(
                RowUtils.str(existing, "source_sha256")) && "ready".equals(RowUtils.str(existing, "status"))
                && mediaProvider.isReusableReadyAsset(RowUtils.str(existing, "provider"),
                RowUtils.str(existing, "provider_asset_id"))) {
            Map<String, Object> ready = mediaSessionView(RowUtils.str(existing, "media_id"),
                    null, "ready", parseObject(RowUtils.str(existing, "provider_details_json")));
            ready.put("idempotentReplay", Boolean.TRUE);
            return ready;
        }
        String mediaId = existing == null ? IdUtils.token("media") : RowUtils.str(existing, "media_id");
        String providerAssetId = IdUtils.token(video ? "mvstream" : "mvimage");
        UploadSession session = video
                ? mediaProvider.createStreamUpload(providerAssetId, request)
                : mediaProvider.createImageUpload(providerAssetId, request);
        repository.upsertMedia(mediaId, templateId, versionId, expectedRole,
                session.getProvider(), session.getProviderAssetId(), session.getStatus(),
                request.getSourceSha256(), request.getSourceSizeBytes().longValue(),
                request.getWidth(), request.getHeight(), request.getDurationSeconds(),
                json(session.getProviderDetails()));
        Map<String, Object> result = mediaSessionView(mediaId, session.getUploadUrl(),
                session.getStatus(), session.getProviderDetails());
        result.put("idempotentReplay", Boolean.FALSE);
        return result;
    }

    public Map<String, Object> completeMedia(String templateId, String versionId, String mediaId) {
        Map<String, Object> media = repository.mediaById(templateId, versionId, mediaId);
        if (media == null) throw notFound("TEMPLATE_MEDIA_NOT_FOUND", "Template media was not found");
        MediaState state;
        String provider = RowUtils.str(media, "provider");
        if ("cloudflare_images".equals(provider)) {
            state = mediaProvider.imageState(RowUtils.str(media, "provider_asset_id"));
        } else if ("cloudflare_stream".equals(provider)) {
            state = mediaProvider.streamState(RowUtils.str(media, "provider_asset_id"));
        } else {
            throw conflict("TEMPLATE_MEDIA_PROVIDER_UNSUPPORTED", "Template media provider is unsupported");
        }
        if ("ready".equals(state.getStatus())) {
            repository.markMediaReady(mediaId, json(state.getProviderDetails()));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mediaId", mediaId);
        result.put("status", state.getStatus());
        result.put("providerDetails", state.getProviderDetails());
        return result;
    }

    public Map<String, Object> publish(String templateId, String versionId) {
        Map<String, Object> version = requireVersion(templateId, versionId);
        String sourceAvailability = RowUtils.str(version, "effective_source_availability");
        if (sourceAvailability == null) {
            sourceAvailability = RowUtils.str(version, "source_availability");
        }
        if (!"exact".equals(RowUtils.str(version, "validation_status"))
                || !"available".equals(sourceAvailability)) {
            throw conflict("TEMPLATE_VERSION_NOT_PUBLISHABLE",
                    "Template version must be exact and available on a renderer node");
        }
        Map<String, Object> cover = repository.mediaByRole(versionId, "cover");
        Map<String, Object> fullMv = repository.mediaByRole(versionId, "full_mv");
        if (!ready(cover) || !ready(fullMv)) {
            Map<String, Object> details = new LinkedHashMap<String, Object>();
            details.put("coverStatus", cover == null ? "missing" : RowUtils.str(cover, "status"));
            details.put("fullMvStatus", fullMv == null ? "missing" : RowUtils.str(fullMv, "status"));
            throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_MEDIA_NOT_READY",
                    "Cover and full MV must both be ready before publish", true, details);
        }
        repository.publish(templateId, versionId);
        return promotionView(templateId, versionId, "published");
    }

    public Map<String, Object> action(String templateId, String action, String versionId) {
        String normalized = blankToNull(action);
        if ("promote-version".equals(normalized) || "publish".equals(normalized)) {
            if (blankToNull(versionId) == null) {
                throw badRequest("TEMPLATE_VERSION_REQUIRED", "Publishing requires versionId");
            }
            return publish(templateId, versionId);
        }
        if ("unpublish".equals(normalized)) {
            requireTemplate(templateId);
            repository.setOffline(templateId);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("templateId", templateId);
            result.put("status", "offline");
            return result;
        }
        throw badRequest("TEMPLATE_ACTION_UNSUPPORTED", "Template action is unsupported in cloud mode");
    }

    public Map<String, Object> readiness() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("moduleEnabled", Boolean.TRUE);
        result.put("d1Configured", Boolean.valueOf(d1.isConfigured()));
        result.put("imagesConfigured", Boolean.valueOf(mediaProvider.imagesConfigured()));
        result.put("streamConfigured", Boolean.valueOf(mediaProvider.streamConfigured()));
        result.put("imagesDeliveryConfigured", Boolean.valueOf(mediaProvider.imagesDeliveryConfigured()));
        result.put("streamDeliveryConfigured", Boolean.valueOf(mediaProvider.streamDeliveryConfigured()));
        result.put("imagesDeliveryValid", Boolean.valueOf(mediaProvider.imagesDeliveryValid()));
        result.put("streamDeliveryValid", Boolean.valueOf(mediaProvider.streamDeliveryValid()));
        boolean d1Reachable = false;
        boolean schemaReady = false;
        if (d1.isConfigured()) {
            try {
                Map<String, Object> counts = camelReadiness(repository.readiness());
                result.put("catalog", counts);
                Object categories = counts.get("categoryCount");
                long categoryCount = categories instanceof Number
                        ? ((Number) categories).longValue() : Long.parseLong(String.valueOf(categories));
                Object schemaVersionValue = counts.get("schemaVersion");
                long schemaVersion = schemaVersionValue instanceof Number
                        ? ((Number) schemaVersionValue).longValue()
                        : Long.parseLong(String.valueOf(schemaVersionValue));
                String schemaSha256 = String.valueOf(counts.get("schemaSha256"));
                d1Reachable = true;
                schemaReady = categoryCount == 12L
                        && schemaVersion == MusicMvD1SchemaInitializer.SCHEMA_VERSION
                        && schemaSha256.matches("[0-9a-f]{64}");
            } catch (RuntimeException exception) {
                result.put("d1Error", "Independent Music MV D1 is not reachable or schema is incomplete");
            }
        }
        result.put("d1Reachable", Boolean.valueOf(d1Reachable));
        result.put("d1SchemaReady", Boolean.valueOf(schemaReady));
        boolean ready = d1Reachable && schemaReady && mediaProvider.imagesConfigured()
                && mediaProvider.streamConfigured() && mediaProvider.imagesDeliveryValid()
                && mediaProvider.streamDeliveryValid();
        result.put("ready", Boolean.valueOf(ready));
        return result;
    }

    private Map<String, Object> summary(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        copy(result, "templateId", row, "template_id");
        copy(result, "slug", row, "slug");
        copy(result, "name", row, "display_name");
        copy(result, "description", row, "description");
        copy(result, "categoryKey", row, "category_key");
        result.put("tags", parseList(RowUtils.str(row, "tags_json")));
        copy(result, "status", row, "status");
        copy(result, "visibility", row, "visibility");
        copy(result, "currentVersionId", row, "current_version_id");
        copy(result, "versionNumber", row, "version_number");
        copy(result, "width", row, "width");
        copy(result, "height", row, "height");
        copy(result, "fps", row, "fps");
        copy(result, "durationSeconds", row, "duration_seconds");
        copy(result, "cycleDurationSeconds", row, "cycle_duration_seconds");
        copy(result, "slotCount", row, "slot_count");
        result.put("cover", providerMedia(row, "cover"));
        result.put("preview", providerMedia(row, "preview"));
        return result;
    }

    private Map<String, Object> templateView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        copy(result, "templateId", row, "template_id");
        copy(result, "slug", row, "slug");
        copy(result, "defaultLocale", row, "default_locale");
        copy(result, "categoryKey", row, "category_key");
        result.put("tags", parseList(RowUtils.str(row, "tags_json")));
        copy(result, "status", row, "status");
        copy(result, "visibility", row, "visibility");
        copy(result, "currentVersionId", row, "current_version_id");
        copy(result, "sortOrder", row, "sort_order");
        copy(result, "revision", row, "revision");
        copy(result, "createdAt", row, "created_at");
        copy(result, "updatedAt", row, "updated_at");
        copy(result, "publishedAt", row, "published_at");
        return result;
    }

    private Map<String, Object> versionView(Map<String, Object> row, boolean admin) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        String[][] fields = new String[][] {
                {"versionId","version_id"},{"versionNumber","version_number"},{"status","status"},
                {"width","width"},{"height","height"},{"fps","fps"},
                {"durationSeconds","duration_seconds"},{"baseDurationSeconds","base_duration_seconds"},
                {"cycleDurationSeconds","cycle_duration_seconds"},{"slotCount","slot_count"},
                {"validationStatus","validation_status"},
                {"createdAt","created_at"},{"publishedAt","published_at"}
        };
        for (String[] field : fields) copy(result, field[0], row, field[1]);
        Object effectiveAvailability = row.get("effective_source_availability");
        result.put("sourceAvailability", effectiveAvailability == null
                ? row.get("source_availability") : effectiveAvailability);
        if (admin) {
            copy(result, "validationRenderJobId", row, "validation_render_job_id");
            copy(result, "validationMasterSha256", row, "validation_master_sha256");
            copy(result, "draftSnapshotSha256", row, "draft_snapshot_sha256");
            copy(result, "timelineEvidenceSha256", row, "timeline_evidence_sha256");
            copy(result, "nativeRuntimeVersion", row, "native_runtime_version");
            copy(result, "nativeRuntimeSha256", row, "native_runtime_sha256");
            copy(result, "rendererVersion", row, "renderer_version");
            copy(result, "sourceNodeId", row, "source_node_id");
            copy(result, "sourceLocalKey", row, "source_local_key");
            result.put("sourceProvenance", parseObject(RowUtils.str(row, "source_provenance_json")));
        }
        return result;
    }

    private List<Map<String, Object>> slotViews(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            String[][] fields = new String[][] {{"slotId","slot_id"},{"slotKey","slot_key"},
                    {"slotType","slot_type"},{"displayName","display_name"},
                    {"timelineOrder","timeline_order"},{"aspectRatio","aspect_ratio"},
                    {"cropPolicy","crop_policy"},{"repeatPolicy","repeat_policy"},
                    {"materialId","material_id"},{"materialGroup","material_group"}};
            for (String[] field : fields) copy(item, field[0], row, field[1]);
            item.put("required", Boolean.valueOf(RowUtils.bool(row, "is_required")));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> mediaViews(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            String[][] fields = new String[][] {{"mediaId","media_id"},{"role","media_role"},
                    {"provider","provider"},{"providerAssetId","provider_asset_id"},
                    {"status","status"},{"sourceSha256","source_sha256"},
                    {"sourceSizeBytes","source_size_bytes"},{"width","width"},{"height","height"},
                    {"durationSeconds","duration_seconds"},{"errorMessage","error_message"},
                    {"createdAt","created_at"},{"updatedAt","updated_at"},{"readyAt","ready_at"}};
            for (String[] field : fields) copy(item, field[0], row, field[1]);
            item.put("providerDetails", mediaProvider.resolveDeliveryDetails(
                    RowUtils.str(row, "provider"), RowUtils.str(row, "provider_asset_id"),
                    parseObject(RowUtils.str(row, "provider_details_json"))));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> providerMedia(Map<String, Object> row, String prefix) {
        String provider = RowUtils.str(row, prefix + "_provider");
        if (provider == null) return null;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("provider", provider);
        String providerAssetId = RowUtils.str(row, prefix + "_asset_id");
        result.put("providerAssetId", providerAssetId);
        result.put("providerDetails", mediaProvider.resolveDeliveryDetails(
                provider, providerAssetId, parseObject(RowUtils.str(row,
                        prefix + "_provider_details_json"))));
        return result;
    }

    private Map<String, Object> mediaSessionView(String mediaId, String uploadUrl, String status,
                                                  Map<String, Object> providerDetails) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mediaId", mediaId);
        result.put("uploadUrl", uploadUrl);
        result.put("status", status);
        result.put("providerDetails", providerDetails);
        return result;
    }

    private Map<String, Object> promotionView(String templateId, String versionId, String status) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("templateId", templateId);
        result.put("versionId", versionId);
        result.put("status", status);
        return result;
    }

    private Map<String, Object> camelReadiness(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        copy(result, "schemaVersion", row, "schema_version");
        copy(result, "schemaSha256", row, "schema_sha256");
        copy(result, "categoryCount", row, "category_count");
        copy(result, "templateCount", row, "template_count");
        copy(result, "versionCount", row, "version_count");
        copy(result, "slotCount", row, "slot_count");
        copy(result, "readyMediaCount", row, "ready_media_count");
        return result;
    }

    private void requireExactValidation(TemplatePromotionRequest request) {
        boolean exact = "exact".equals(request.getSemanticIntegrity())
                && request.getVideoEncodeCount().intValue() == 1
                && request.getIntermediateVideoCount().intValue() == 0
                && request.getMissingResourceCount().intValue() == 0;
        if (!exact) throw conflict("TEMPLATE_VALIDATION_NOT_EXACT",
                "Only exact, single-encode native validation can be promoted");
    }

    private void requireUniqueSlots(List<TemplatePromotionRequest.Slot> slots) {
        Set<String> keys = new HashSet<String>();
        for (TemplatePromotionRequest.Slot slot : slots) {
            if (!keys.add(slot.getSlotKey())) throw badRequest("TEMPLATE_SLOT_DUPLICATE",
                    "Template promotion contains a duplicate slot key");
            if (!"image".equals(slot.getSlotType()) && !"photo".equals(slot.getSlotType())) {
                throw badRequest("TEMPLATE_SLOT_TYPE_UNSUPPORTED",
                        "Music MV template currently supports image slots only");
            }
        }
    }

    private void requireCategory(String categoryKey) {
        Map<String, Object> category = repository.category(categoryKey);
        if (category == null || !RowUtils.bool(category, "enabled")) {
            throw badRequest("TEMPLATE_CATEGORY_INVALID", "Template category is not enabled");
        }
    }

    private Map<String, Object> requireTemplate(String templateId) {
        Map<String, Object> row = repository.template(templateId);
        if (row == null) throw notFound("TEMPLATE_NOT_FOUND", "Template was not found");
        return row;
    }

    private Map<String, Object> requireVersion(String templateId, String versionId) {
        requireTemplate(templateId);
        Map<String, Object> row = repository.version(templateId, versionId);
        if (row == null) throw notFound("TEMPLATE_VERSION_NOT_FOUND", "Template version was not found");
        return row;
    }

    private boolean ready(Map<String, Object> row) { return row != null && "ready".equals(RowUtils.str(row, "status")); }
    private String normalizeLocale(String value) { return value != null && value.toLowerCase().startsWith("en") ? "en" : "zh-CN"; }
    private String blankToNull(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }

    private List<Object> parseList(String value) {
        if (value == null || value.trim().isEmpty()) return new ArrayList<Object>();
        try { return objectMapper.readValue(value, new TypeReference<List<Object>>() {}); }
        catch (Exception ignored) { return new ArrayList<Object>(); }
    }

    private Map<String, Object> parseObject(String value) {
        if (value == null || value.trim().isEmpty()) return new LinkedHashMap<String, Object>();
        try { return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception ignored) { return new LinkedHashMap<String, Object>(); }
    }

    private String jsonOrEmpty(Object value) { return json(value == null ? new LinkedHashMap<String, Object>() : value); }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Serialize template catalog value failed", exception); }
    }

    private void copy(Map<String, Object> target, String targetKey, Map<String, Object> source, String sourceKey) {
        target.put(targetKey, source.get(sourceKey));
    }

    private Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put(key, value); return result;
    }
    private ApiException badRequest(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
