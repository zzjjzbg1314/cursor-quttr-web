package com.example.cursorquitterweb.musicmv.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.cursorquitterweb.musicmv.dto.TemplateMediaUploadSessionRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateBrowserSceneRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateMetadataUpdateRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateSlotReconcileRequest;
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
    private static final Set<String> FULL_MV_SOURCE_TYPES;
    static {
        Set<String> values = new HashSet<String>();
        values.add("public"); values.add("private"); values.add("unlisted");
        VISIBILITIES = Collections.unmodifiableSet(values);

        Set<String> fullMvSourceTypes = new HashSet<String>();
        fullMvSourceTypes.add("capcut_official_template_preview");
        fullMvSourceTypes.add("validated_ai_music_mv_native_output");
        FULL_MV_SOURCE_TYPES = Collections.unmodifiableSet(fullMvSourceTypes);
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
            Map<String, Object> browserScene = repository.browserScene(versionId);
            if (browserScene != null && (admin || "ready".equals(RowUtils.str(browserScene, "status")))) {
                version.put("browserRender", browserSceneView(browserScene, admin));
            }
            versions.add(version);
        }
        result.put("versions", versions);
        return result;
    }

    public Map<String, Object> promote(TemplatePromotionRequest request) {
        requirePromotionEvidence(request);
        requireCategory(request.getCategoryKey());
        requireUniqueSlots(request.getSlots());
        List<Map<String, Object>> identities = repository.templatesByCapCutTemplateIds(
                Collections.singletonList(request.getCapcutTemplateId()));
        if (identities != null && !identities.isEmpty() && !request.getTemplateId().equals(
                RowUtils.str(identities.get(0), "template_id"))) {
            throw conflict("CAPCUT_TEMPLATE_ALREADY_EXISTS",
                    "This CapCut template is already bound to template "
                            + RowUtils.str(identities.get(0), "template_id"));
        }
        Map<String, Object> existing = repository.versionByValidationJob(request.getValidationRenderJobId());
        if (existing != null) {
            boolean same = request.getTemplateId().equals(RowUtils.str(existing, "template_id"))
                    && request.getDraftSnapshotSha256().equalsIgnoreCase(
                    RowUtils.str(existing, "draft_snapshot_sha256"))
                    && request.getValidationMasterSha256().equalsIgnoreCase(
                    RowUtils.str(existing, "validation_master_sha256"));
            if (!same) throw conflict("TEMPLATE_PROMOTION_IDEMPOTENCY_CONFLICT",
                    "Validation render job is already bound to different template evidence");
            if (!"latest_saved_draft".equals(request.getPromotionMode())) {
                repository.enrichVisualQuality(RowUtils.str(existing, "version_id"),
                        request.getCycleDurationSeconds(), json(promotionProvenance(request)));
            }
            Map<String, Object> replay = promotionView(request.getTemplateId(),
                    RowUtils.str(existing, "version_id"), RowUtils.str(existing, "status"));
            replay.put("idempotentReplay", Boolean.TRUE);
            return replay;
        }
        String versionId = IdUtils.token("tplver");
        repository.promote(request, versionId, repository.nextVersionNumber(request.getTemplateId()),
                json(request.getTags()), json(promotionProvenance(request)),
                jsonOrEmpty(request.getValidationEvidence()));
        Map<String, Object> result = promotionView(request.getTemplateId(), versionId, "validated");
        result.put("idempotentReplay", Boolean.FALSE);
        return result;
    }

    public Map<String, Object> capCutTemplateExistence(List<String> requestedIds) {
        List<String> ids = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (String raw : requestedIds) {
            String value = blankToNull(raw);
            if (value == null || !value.matches("^[0-9]{8,24}$")) {
                throw badRequest("CAPCUT_TEMPLATE_ID_INVALID", "CapCut template ID is invalid");
            }
            if (seen.add(value)) ids.add(value);
        }
        Map<String, Map<String, Object>> existing = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> row : repository.templatesByCapCutTemplateIds(ids)) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            copy(item, "templateId", row, "template_id");
            copy(item, "status", row, "status");
            copy(item, "currentVersionId", row, "current_version_id");
            existing.put(RowUtils.str(row, "capcut_template_id"), item);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("existing", existing);
        return result;
    }

    public Map<String, Object> bindCapCutTemplateIdentity(
            String templateId, String capcutTemplateId
    ) {
        requireTemplate(templateId);
        List<Map<String, Object>> existing = repository.templatesByCapCutTemplateIds(
                Collections.singletonList(capcutTemplateId));
        if (existing != null && !existing.isEmpty()
                && !templateId.equals(RowUtils.str(existing.get(0), "template_id"))) {
            throw conflict("CAPCUT_TEMPLATE_ALREADY_EXISTS",
                    "This CapCut template is already bound to template "
                            + RowUtils.str(existing.get(0), "template_id"));
        }
        repository.bindCapCutTemplateIdentity(templateId, capcutTemplateId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("templateId", templateId);
        result.put("capcutTemplateId", capcutTemplateId);
        result.put("status", "bound");
        return result;
    }

    public Map<String, Object> reconcileSlots(String templateId, String versionId,
                                               TemplateSlotReconcileRequest request) {
        Map<String, Object> version = requireVersion(templateId, versionId);
        String expectedNode = RowUtils.str(version, "source_node_id");
        String expectedLocalKey = RowUtils.str(version, "source_local_key");
        if (!request.getSourceNodeId().equals(expectedNode)
                || !request.getSourceLocalKey().equals(expectedLocalKey)) {
            throw conflict("TEMPLATE_SLOT_SOURCE_MISMATCH",
                    "Slot reconciliation source does not match the immutable template version");
        }
        requireUniqueSlots(request.getSlots());
        repository.replaceSlots(templateId, versionId, request.getSlots());

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("templateId", templateId);
        result.put("versionId", versionId);
        result.put("status", "reconciled");
        result.put("slotCount", Integer.valueOf(request.getSlots().size()));
        return result;
    }

    public Map<String, Object> synchronizeBrowserScene(
            String templateId, String versionId, TemplateBrowserSceneRequest request) {
        requireVersion(templateId, versionId);
        Map<String, Object> scene = request.getScene();
        if (!templateId.equals(String.valueOf(scene.get("templateId")))
                || !versionId.equals(String.valueOf(scene.get("versionId")))) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_ID_MISMATCH",
                    "Browser scene does not match the target template version");
        }
        if (!request.getSchemaVersion().equals(String.valueOf(scene.get("schemaVersion")))) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_SCHEMA_MISMATCH",
                    "Browser scene schema does not match its envelope");
        }
        requireSanitizedBrowserScene(scene);
        String sceneJson = json(scene);
        String actualSha256 = sha256(sceneJson);
        if (!actualSha256.equalsIgnoreCase(request.getManifestSha256())) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_HASH_MISMATCH",
                    "Browser scene SHA-256 does not match its content");
        }
        Map<String, Object> capability = scene.get("capability") instanceof Map
                ? (Map<String, Object>) scene.get("capability")
                : Collections.<String, Object>emptyMap();
        if (!Boolean.TRUE.equals(capability.get("photoReplacementReady"))) {
            throw conflict("TEMPLATE_BROWSER_SCENE_NOT_READY",
                    "Every formal photo slot must be resolved before browser rendering is enabled");
        }
        if (capability.get("photoAnimationContract") != null
                && !Boolean.TRUE.equals(capability.get("photoAnimationReady"))) {
            throw conflict("TEMPLATE_BROWSER_SCENE_ANIMATION_NOT_READY",
                    "Every published photo animation must be executable before browser rendering is enabled");
        }
        repository.upsertBrowserScene(templateId, versionId, request.getSchemaVersion(),
                actualSha256, "ready", sceneJson);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("templateId", templateId);
        result.put("versionId", versionId);
        result.put("status", "ready");
        result.put("schemaVersion", request.getSchemaVersion());
        result.put("manifestSha256", actualSha256);
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
        String expectedRole = request.getRole();
        boolean slotDefault = !video && expectedRole != null
                && expectedRole.startsWith("slot_default:");
        if (slotDefault) {
            String slotKey = expectedRole.substring("slot_default:".length());
            boolean slotExists = false;
            for (Map<String, Object> slot : repository.slots(versionId)) {
                if (slotKey.equals(RowUtils.str(slot, "slot_key"))
                        && ("image".equals(RowUtils.str(slot, "slot_type"))
                        || "photo".equals(RowUtils.str(slot, "slot_type")))) {
                    slotExists = true;
                    break;
                }
            }
            if (!slotExists) {
                throw badRequest("TEMPLATE_MEDIA_SLOT_INVALID",
                        "The template photo role does not match a material slot");
            }
        }
        if (video ? !"full_mv".equals(expectedRole)
                : !("cover".equals(expectedRole) || slotDefault)) {
            throw badRequest("TEMPLATE_MEDIA_ROLE_INVALID", video
                    ? "Stream upload only accepts role full_mv"
                    : "Images upload only accepts cover or a template slot photo");
        }
        if (video && (request.getDurationSeconds() == null || request.getFilename() == null)) {
            throw badRequest("TEMPLATE_VIDEO_METADATA_REQUIRED",
                    "Full MV upload requires durationSeconds and filename");
        }
        Map<String, Object> existing = repository.mediaByRole(versionId, expectedRole);
        boolean forceReplace = Boolean.TRUE.equals(request.getForceReplace());
        if (!forceReplace && existing != null && request.getSourceSha256().equalsIgnoreCase(
                RowUtils.str(existing, "source_sha256")) && "ready".equals(RowUtils.str(existing, "status"))
                && mediaProvider.isReusableReadyAsset(RowUtils.str(existing, "provider"),
                RowUtils.str(existing, "provider_asset_id"))) {
            Map<String, Object> existingDetails = parseObject(
                    RowUtils.str(existing, "provider_details_json"));
            if (video && request.getSourceType() != null) {
                existingDetails.put("sourceType", request.getSourceType());
                existingDetails.put("displayLabel", request.getDisplayLabel());
                existingDetails.put("loopDurationSeconds", request.getLoopDurationSeconds());
                existingDetails.put("visualQuality", request.getVisualQuality());
                repository.markMediaReady(RowUtils.str(existing, "media_id"),
                        json(existingDetails));
            }
            Map<String, Object> ready = mediaSessionView(RowUtils.str(existing, "media_id"),
                    null, "ready", existingDetails);
            ready.put("idempotentReplay", Boolean.TRUE);
            return ready;
        }
        String mediaId = existing == null ? IdUtils.token("media") : RowUtils.str(existing, "media_id");
        String providerAssetId = IdUtils.token(video ? "mvstream" : "mvimage");
        UploadSession session = video
                ? mediaProvider.createStreamUpload(providerAssetId, request)
                : mediaProvider.createImageUpload(providerAssetId, request);
        Map<String, Object> providerDetails = new LinkedHashMap<String, Object>();
        providerDetails.putAll(session.getProviderDetails());
        if (video && request.getSourceType() != null) {
            if (!FULL_MV_SOURCE_TYPES.contains(request.getSourceType())) {
                throw badRequest("TEMPLATE_MEDIA_SOURCE_TYPE_INVALID",
                        "Unsupported full MV source type");
            }
            providerDetails.put("sourceType", request.getSourceType());
            providerDetails.put("displayLabel", request.getDisplayLabel());
            providerDetails.put("officialTemplateId", request.getOfficialTemplateId());
            providerDetails.put("officialPageUrl", request.getOfficialPageUrl());
            providerDetails.put("loopDurationSeconds", request.getLoopDurationSeconds());
            providerDetails.put("visualQuality", request.getVisualQuality());
        }
        repository.upsertMedia(mediaId, templateId, versionId, expectedRole,
                session.getProvider(), session.getProviderAssetId(), session.getStatus(),
                request.getSourceSha256(), request.getSourceSizeBytes().longValue(),
                request.getWidth(), request.getHeight(), request.getDurationSeconds(),
                json(providerDetails));
        Map<String, Object> result = mediaSessionView(mediaId, session.getUploadUrl(),
                session.getStatus(), providerDetails);
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
        Map<String, Object> providerDetails = parseObject(
                RowUtils.str(media, "provider_details_json"));
        providerDetails.putAll(state.getProviderDetails());
        if ("ready".equals(state.getStatus())) {
            repository.markMediaReady(mediaId, json(providerDetails));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mediaId", mediaId);
        result.put("status", state.getStatus());
        result.put("providerDetails", providerDetails);
        return result;
    }

    public Map<String, Object> publish(String templateId, String versionId) {
        Map<String, Object> version = requireVersion(templateId, versionId);
        String sourceAvailability = RowUtils.str(version, "effective_source_availability");
        if (sourceAvailability == null) {
            sourceAvailability = RowUtils.str(version, "source_availability");
        }
        String validationStatus = RowUtils.str(version, "validation_status");
        boolean browserReady = "browser_ready".equals(validationStatus);
        if (!browserReady) {
            requireVisualQuality(parseObject(RowUtils.str(version, "source_provenance_json"))
                            .get("visualQuality"),
                    RowUtils.dbl(version, "base_duration_seconds"),
                    RowUtils.dbl(version, "cycle_duration_seconds"),
                    RowUtils.str(version, "validation_master_sha256"));
        }
        boolean sourceReady = browserReady || "available".equals(sourceAvailability);
        if ((!browserReady && !"exact".equals(validationStatus)) || !sourceReady) {
            throw conflict("TEMPLATE_VERSION_NOT_PUBLISHABLE",
                    "Template version must be browser-ready, or exact and available on a renderer node");
        }
        Map<String, Object> cover = repository.mediaByRole(versionId, "cover");
        Map<String, Object> fullMv = repository.mediaByRole(versionId, "full_mv");
        List<String> missingDefaults = new ArrayList<String>();
        for (Map<String, Object> slot : repository.slots(versionId)) {
            String type = RowUtils.str(slot, "slot_type");
            if (!"image".equals(type) && !"photo".equals(type)) continue;
            String slotKey = RowUtils.str(slot, "slot_key");
            if (!ready(repository.mediaByRole(versionId, "slot_default:" + slotKey))) {
                missingDefaults.add(slotKey);
            }
        }
        Map<String, Object> browserScene = repository.browserScene(versionId);
        boolean browserSceneReady = browserScene != null
                && "ready".equals(RowUtils.str(browserScene, "status"));
        boolean mediaReady = browserReady
                ? browserSceneReady
                : ready(cover) && ready(fullMv) && missingDefaults.isEmpty();
        if (!mediaReady) {
            Map<String, Object> details = new LinkedHashMap<String, Object>();
            details.put("coverStatus", cover == null ? "missing" : RowUtils.str(cover, "status"));
            details.put("fullMvStatus", fullMv == null ? "missing" : RowUtils.str(fullMv, "status"));
            details.put("missingTemplatePhotoSlots", missingDefaults);
            details.put("browserSceneStatus", browserScene == null
                    ? "missing" : RowUtils.str(browserScene, "status"));
            throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_MEDIA_NOT_READY",
                    browserReady
                            ? "Browser scene must be ready before publish"
                            : "Cover, full MV, and every original template photo must be ready before publish",
                    true, details);
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
        if ("delete-template".equals(normalized)) {
            return deleteTemplate(templateId, false);
        }
        if ("force-delete-template".equals(normalized)) {
            return deleteTemplate(templateId, true);
        }
        throw badRequest("TEMPLATE_ACTION_UNSUPPORTED", "Template action is unsupported in cloud mode");
    }

    private Map<String, Object> deleteTemplate(String templateId, boolean force) {
        Map<String, Object> template = repository.template(templateId);
        if (template == null) {
            Map<String, Object> replay = new LinkedHashMap<String, Object>();
            replay.put("templateId", templateId);
            replay.put("deleted", Boolean.TRUE);
            replay.put("forced", Boolean.valueOf(force));
            replay.put("idempotentReplay", Boolean.TRUE);
            return replay;
        }
        if (!force && !"offline".equals(RowUtils.str(template, "status"))) {
            throw conflict("TEMPLATE_DELETE_REQUIRES_OFFLINE",
                    "Template must be offline before permanent deletion");
        }
        long projectReferences = repository.projectReferenceCount(templateId);
        long renderJobReferences = repository.renderJobReferenceCount(templateId);
        if (!force && (projectReferences > 0L || renderJobReferences > 0L)) {
            Map<String, Object> details = new LinkedHashMap<String, Object>();
            details.put("projectReferences", Long.valueOf(projectReferences));
            details.put("renderJobReferences", Long.valueOf(renderJobReferences));
            throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_DELETE_REFERENCED",
                    "Template is still referenced by user projects or render jobs", false, details);
        }

        java.util.List<Map<String, Object>> media = repository.mediaForTemplate(templateId);
        for (Map<String, Object> item : media) {
            mediaProvider.deleteAsset(RowUtils.str(item, "provider"),
                    RowUtils.str(item, "provider_asset_id"));
        }
        if (force) {
            repository.forceDeleteTemplate(templateId);
        } else {
            repository.deleteTemplate(templateId);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("templateId", templateId);
        result.put("deleted", Boolean.TRUE);
        result.put("deletedMediaCount", Integer.valueOf(media.size()));
        result.put("detachedProjectCount", Long.valueOf(force ? projectReferences : 0L));
        result.put("deletedRenderJobCount", Long.valueOf(force ? renderJobReferences : 0L));
        result.put("forced", Boolean.valueOf(force));
        result.put("idempotentReplay", Boolean.FALSE);
        return result;
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
        copy(result, "capcutTemplateId", row, "capcut_template_id");
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
        copy(result, "validationStatus", row, "validation_status");
        copy(result, "rendererVersion", row, "renderer_version");
        result.put("cover", providerMedia(row, "cover"));
        result.put("preview", providerMedia(row, "preview"));
        return result;
    }

    private Map<String, Object> templateView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        copy(result, "templateId", row, "template_id");
        copy(result, "capcutTemplateId", row, "capcut_template_id");
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

    private void requirePromotionEvidence(TemplatePromotionRequest request) {
        if ("latest_saved_draft".equals(request.getPromotionMode())) {
            boolean ready = "browser_ready".equals(request.getSemanticIntegrity())
                    && request.getVideoEncodeCount().intValue() == 0
                    && request.getIntermediateVideoCount().intValue() == 0
                    && request.getMissingResourceCount().intValue() == 0
                    && "browser-canvas-v1".equals(request.getRendererVersion());
            if (!ready) throw conflict("TEMPLATE_BROWSER_DRAFT_NOT_READY",
                    "Latest saved draft promotion requires an immutable browser-ready draft");
            return;
        }
        boolean exact = "exact".equals(request.getSemanticIntegrity())
                && request.getVideoEncodeCount().intValue() == 1
                && request.getIntermediateVideoCount().intValue() == 0
                && request.getMissingResourceCount().intValue() == 0;
        if (!exact) throw conflict("TEMPLATE_VALIDATION_NOT_EXACT",
                "Only exact, single-encode native validation can be promoted");
        requireVisualQuality(request.getVisualQuality(), request.getBaseDurationSeconds(),
                request.getCycleDurationSeconds(), request.getValidationMasterSha256());
    }

    private Map<String, Object> promotionProvenance(TemplatePromotionRequest request) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (request.getSourceProvenance() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> supplied = (Map<String, Object>) request.getSourceProvenance();
            result.putAll(supplied);
        }
        if (!"latest_saved_draft".equals(request.getPromotionMode())) {
            result.put("visualQuality", request.getVisualQuality());
        }
        return result;
    }

    private void requireVisualQuality(Object evidence, Number baseDuration,
                                      Number cycleDuration, String validationSha256) {
        if (!(evidence instanceof Map)) {
            throw conflict("TEMPLATE_VISUAL_QUALITY_REQUIRED",
                    "Template requires black-screen quality evidence before publish");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) evidence;
        String status = String.valueOf(report.get("status"));
        String schema = String.valueOf(report.get("schemaVersion"));
        String sourceSha256 = String.valueOf(report.get("sourceSha256"));
        double declaredBase = numeric(report.get("baseDurationSeconds"));
        double effectiveCycle = numeric(report.get("effectiveCycleDurationSeconds"));
        double expectedBase = baseDuration == null ? 0.0d : baseDuration.doubleValue();
        double expectedCycle = cycleDuration == null ? 0.0d : cycleDuration.doubleValue();
        boolean valid = "template-visual-quality-v1".equals(schema)
                && ("passed".equals(status) || "adjusted".equals(status))
                && validationSha256 != null && validationSha256.equalsIgnoreCase(sourceSha256)
                && Math.abs(declaredBase - expectedBase) <= 0.05d
                && Math.abs(effectiveCycle - expectedCycle) <= 0.05d
                && effectiveCycle > 0.0d && effectiveCycle <= declaredBase + 0.05d;
        if (!valid) throw conflict("TEMPLATE_VISUAL_QUALITY_INVALID",
                "Template black-screen quality evidence does not match the immutable video");
    }

    private double numeric(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (RuntimeException ignored) { return 0.0d; }
    }

    public Map<String, Object> migrateCurrentTemplatesToBrowserRendering() {
        int updated = repository.migrateCurrentTemplatesToBrowserRendering();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("updatedVersionCount", Integer.valueOf(updated));
        result.put("validationStatus", "browser_ready");
        result.put("rendererVersion", "browser-canvas-v1");
        result.put("mediaPreserved", Boolean.TRUE);
        return result;
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

    private Map<String, Object> browserSceneView(Map<String, Object> row, boolean admin) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", row.get("schema_version"));
        result.put("status", row.get("status"));
        result.put("manifestSha256", row.get("manifest_sha256"));
        result.put("scene", parseObject(RowUtils.str(row, "scene_json")));
        if (admin) {
            result.put("createdAt", row.get("created_at"));
            result.put("updatedAt", row.get("updated_at"));
        }
        return result;
    }

    private void requireSanitizedBrowserScene(Object value) {
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase();
                if (key.equals("path") || key.endsWith("path") || key.contains("localkey")
                        || key.contains("sourcedraft") || key.equals("materials")) {
                    throw badRequest("TEMPLATE_BROWSER_SCENE_PRIVATE_DATA",
                            "Browser scene contains private source information");
                }
                requireSanitizedBrowserScene(entry.getValue());
            }
        } else if (value instanceof List) {
            for (Object item : (List<?>) value) requireSanitizedBrowserScene(item);
        } else if (value instanceof String) {
            String text = ((String) value).toLowerCase();
            if (text.startsWith("file:") || text.contains("/users/")
                    || text.contains("\\users\\") || text.contains("templateDraft".toLowerCase())) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_PRIVATE_DATA",
                        "Browser scene contains a local filesystem reference");
            }
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

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
