package com.example.cursorquitterweb.musicmv.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
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
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository.TemplateDetailRows;
import com.example.cursorquitterweb.musicmv.service.CloudflareTemplateMediaProvider.MediaState;
import com.example.cursorquitterweb.musicmv.service.CloudflareTemplateMediaProvider.UploadSession;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.IdUtils;
import com.example.cursorquitterweb.musicmv.support.RowUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

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
    private final Cache<String, Map<String, Object>> publicDetailCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

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
        List<Map<String, Object>> flatItems = new ArrayList<Map<String, Object>>();
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> row : repository.categories()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("key", RowUtils.str(row, "category_key"));
            item.put("parentKey", RowUtils.str(row, "parent_key"));
            item.put("level", RowUtils.integer(row, "level"));
            item.put("slugPath", RowUtils.str(row, "slug_path"));
            item.put("selectable", Boolean.valueOf(RowUtils.bool(row, "is_selectable")));
            item.put("name", RowUtils.str(row, english ? "name_en" : "name_zh"));
            item.put("nameZh", RowUtils.str(row, "name_zh"));
            item.put("nameEn", RowUtils.str(row, "name_en"));
            item.put("sortOrder", RowUtils.integer(row, "sort_order"));
            item.put("children", new ArrayList<Map<String, Object>>());
            flatItems.add(item);
            byKey.put(String.valueOf(item.get("key")), item);
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : flatItems) {
            String parentKey = item.get("parentKey") == null ? null : String.valueOf(item.get("parentKey"));
            Map<String, Object> parent = parentKey == null ? null : byKey.get(parentKey);
            if (parent == null) items.add(item);
            else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", items);
        result.put("flatItems", flatItems);
        result.put("filters", technicalFilters());
        return result;
    }

    public Map<String, Object> collections(String locale, String parentCategoryKey) {
        String parent = blankToNull(parentCategoryKey);
        if (parent != null) requireCategory(parent);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : repository.collections(parent)) {
            items.add(collectionView(row, locale));
        }
        return singleton("items", items);
    }

    public Map<String, Object> collection(String slug, String locale) {
        Map<String, Object> row = repository.collectionBySlug(slug);
        if (row == null) throw notFound("TEMPLATE_COLLECTION_NOT_FOUND", "Template collection was not found");
        Map<String, Object> result = collectionView(row, locale);
        List<Map<String, Object>> related = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : repository.relatedCollections(RowUtils.str(row, "collection_key"))) {
            related.add(collectionView(item, locale));
        }
        result.put("related", related);
        return result;
    }

    public Map<String, Object> list(String locale, String categoryKey, String collectionSlug,
                                    String keyword, Integer minSlots, Integer maxSlots,
                                    Double minDuration, Double maxDuration, String aspectRatio,
                                    Integer page, Integer pageSize, boolean admin,
                                    String requestedStatus) {
        String category = blankToNull(categoryKey);
        if (category != null) requireCategory(category);
        String collectionKey = null;
        String collection = blankToNull(collectionSlug);
        if (collection != null) {
            Map<String, Object> found = repository.collectionBySlug(collection);
            if (found == null) throw notFound("TEMPLATE_COLLECTION_NOT_FOUND", "Template collection was not found");
            collectionKey = RowUtils.str(found, "collection_key");
        }
        String query = blankToNull(keyword);
        String ratio = normalizeAspectRatio(aspectRatio);
        int normalizedPage = page == null ? 1 : Math.max(1, page.intValue());
        int normalizedSize = pageSize == null ? 24 : Math.max(1, Math.min(100, pageSize.intValue()));
        String status = admin ? blankToNull(requestedStatus) : "published";
        String visibility = admin ? null : "public";
        long total = repository.templateCount(status, visibility, category, collectionKey, query,
                minSlots, maxSlots, minDuration, maxDuration, ratio);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : repository.templates(normalizeLocale(locale), status,
                visibility, category, collectionKey, query, minSlots, maxSlots,
                minDuration, maxDuration, ratio, normalizedSize,
                (normalizedPage - 1) * normalizedSize)) {
            items.add(summary(row));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", items);
        result.put("page", Integer.valueOf(normalizedPage));
        result.put("pageSize", Integer.valueOf(normalizedSize));
        result.put("total", Long.valueOf(total));
        result.put("hasMore", Boolean.valueOf((long) normalizedPage * normalizedSize < total));
        result.put("filters", technicalFilters());
        return result;
    }

    public Map<String, Object> similar(String templateId, String locale, Integer limit) {
        Map<String, Object> template = requireTemplate(templateId);
        int size = limit == null ? 6 : Math.max(1, Math.min(24, limit.intValue()));
        List<Map<String, Object>> candidates = repository.templates(normalizeLocale(locale),
                "published", "public", RowUtils.str(template, "category_key"), null, null,
                null, null, null, null, null, size + 1, 0);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : candidates) {
            if (!templateId.equals(RowUtils.str(row, "template_id"))) items.add(summary(row));
            if (items.size() >= size) break;
        }
        return singleton("items", items);
    }

    public Map<String, Object> detail(String templateId, boolean admin) {
        if (admin) return loadDetail(templateId, true);
        return publicDetailCache.get(templateId, key -> loadDetail(key, false));
    }

    private Map<String, Object> loadDetail(String templateId, boolean admin) {
        TemplateDetailRows rows = repository.templateDetail(templateId);
        Map<String, Object> template = rows.getTemplate();
        if (template == null) throw notFound("TEMPLATE_NOT_FOUND", "Template was not found");
        if (!admin && (!"published".equals(RowUtils.str(template, "status"))
                || !"public".equals(RowUtils.str(template, "visibility")))) {
            throw notFound("TEMPLATE_NOT_FOUND", "Template was not found");
        }
        Map<String, Object> result = templateView(template, admin,
                rows.getCategories(), rows.getSourceMetadata());
        List<Map<String, Object>> translations = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows.getTranslations()) {
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
        Map<String, List<Map<String, Object>>> slotsByVersion = groupByVersion(rows.getSlots());
        Map<String, List<Map<String, Object>>> mediaByVersion = groupByVersion(rows.getMedia());
        Map<String, Map<String, Object>> scenesByVersion = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> scene : rows.getBrowserScenes()) {
            scenesByVersion.put(RowUtils.str(scene, "version_id"), scene);
        }
        List<Map<String, Object>> versions = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows.getVersions()) {
            if (!admin && !String.valueOf(currentVersionId).equals(RowUtils.str(row, "version_id"))) continue;
            Map<String, Object> version = versionView(row, admin);
            String versionId = RowUtils.str(row, "version_id");
            version.put("slots", slotViews(orEmpty(slotsByVersion.get(versionId))));
            version.put("media", mediaViews(orEmpty(mediaByVersion.get(versionId))));
            Map<String, Object> browserScene = scenesByVersion.get(versionId);
            if (browserScene != null && (admin || "ready".equals(RowUtils.str(browserScene, "status")))) {
                version.put("browserRender", browserSceneView(browserScene, admin));
            }
            versions.add(version);
        }
        result.put("versions", versions);
        return result;
    }

    private Map<String, List<Map<String, Object>>> groupByVersion(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> grouped =
                new LinkedHashMap<String, List<Map<String, Object>>>();
        for (Map<String, Object> row : rows) {
            String versionId = RowUtils.str(row, "version_id");
            List<Map<String, Object>> values = grouped.get(versionId);
            if (values == null) {
                values = new ArrayList<Map<String, Object>>();
                grouped.put(versionId, values);
            }
            values.add(row);
        }
        return grouped;
    }

    private List<Map<String, Object>> orEmpty(List<Map<String, Object>> rows) {
        return rows == null ? Collections.<Map<String, Object>>emptyList() : rows;
    }

    private void invalidateDetail(String templateId) {
        if (templateId != null) publicDetailCache.invalidate(templateId);
    }

    public Map<String, Object> promote(TemplatePromotionRequest request) {
        requirePromotionEvidence(request);
        requireLeafCategory(request.getCategoryKey());
        List<String> sourceHashtags = rawHashtags(request.getSourceHashtags());
        List<Map<String, Object>> categoryAssignments = categoryAssignments(
                request.getCategoryKey(), request.getCategoryKeys(), request.getSourceTitle(),
                request.getSourceDescription(), request.getSourceCategory(),
                request.getSourceSearchKeyword(), sourceHashtags,
                Boolean.TRUE.equals(request.getClassificationLocked()));
        String primaryCategory = resolvedPrimaryCategory(request.getCategoryKey(),
                categoryAssignments, Boolean.TRUE.equals(request.getClassificationLocked()));
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
            invalidateDetail(request.getTemplateId());
            Map<String, Object> replay = promotionView(request.getTemplateId(),
                    RowUtils.str(existing, "version_id"), RowUtils.str(existing, "status"));
            replay.put("idempotentReplay", Boolean.TRUE);
            return replay;
        }
        String versionId = IdUtils.token("tplver");
        repository.promote(request, versionId, repository.nextVersionNumber(request.getTemplateId()),
                json(sourceHashtags), json(promotionProvenance(request)),
                jsonOrEmpty(request.getValidationEvidence()));
        repository.upsertTemplateSourceMetadata(request.getTemplateId(), safe(request.getSourceTitle()),
                safe(request.getSourceDescription()), safe(request.getSourceCategory()),
                safe(request.getSourceSearchKeyword()), json(sourceHashtags), safe(request.getSourceUrl()),
                Boolean.TRUE.equals(request.getClassificationLocked()));
        repository.replaceTemplateCategories(request.getTemplateId(), primaryCategory,
                categoryAssignments);
        invalidateDetail(request.getTemplateId());
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
        invalidateDetail(templateId);
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
        invalidateDetail(templateId);

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
        if ("browser-template-scene-v4".equals(request.getSchemaVersion())) {
            requireValidBrowserSceneGraph(scene);
            requireValidBrowserExecutionCapabilities(capability);
        }
        repository.upsertBrowserScene(templateId, versionId, request.getSchemaVersion(),
                actualSha256, "ready", sceneJson);
        invalidateDetail(templateId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("templateId", templateId);
        result.put("versionId", versionId);
        result.put("status", "ready");
        result.put("schemaVersion", request.getSchemaVersion());
        result.put("manifestSha256", actualSha256);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void requireValidBrowserSceneGraph(Map<String, Object> scene) {
        Object rawLayers = scene.get("layers");
        if (!(rawLayers instanceof List) || ((List<?>) rawLayers).isEmpty()) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_GRAPH_REQUIRED",
                    "Browser scene v4 requires a non-empty layer graph");
        }
        Set<String> slotKeys = new HashSet<String>();
        Object rawSlots = scene.get("slots");
        if (rawSlots instanceof List) {
            for (Object rawSlot : (List<?>) rawSlots) {
                if (!(rawSlot instanceof Map)) continue;
                Object slotKey = ((Map<?, ?>) rawSlot).get("slotKey");
                if (slotKey != null) slotKeys.add(String.valueOf(slotKey));
            }
        }
        Map<String, String> resourceKinds = new LinkedHashMap<String, String>();
        Object rawResources = scene.get("resources");
        if (rawResources instanceof List) {
            for (Object rawResource : (List<?>) rawResources) {
                if (!(rawResource instanceof Map)) continue;
                Map<?, ?> resource = (Map<?, ?>) rawResource;
                String resourceKey = blankToNull(resource.get("resourceKey") == null
                        ? null : String.valueOf(resource.get("resourceKey")));
                String kind = blankToNull(resource.get("kind") == null
                        ? null : String.valueOf(resource.get("kind")));
                if (resourceKey != null && kind != null) resourceKinds.put(resourceKey, kind);
            }
        }
        Set<String> layerIds = new HashSet<String>();
        for (Object rawLayer : (List<?>) rawLayers) {
            if (!(rawLayer instanceof Map)) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_LAYER_INVALID",
                        "Every browser scene layer must be an object");
            }
            Map<String, Object> layer = (Map<String, Object>) rawLayer;
            String layerId = blankToNull(layer.get("layerId") == null
                    ? null : String.valueOf(layer.get("layerId")));
            String type = blankToNull(layer.get("type") == null
                    ? null : String.valueOf(layer.get("type")));
            if (layerId == null || type == null || !layerIds.add(layerId)) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_LAYER_INVALID",
                        "Browser scene layer IDs and types must be present and unique");
            }
            if ("photo".equals(type)) {
                String slotKey = blankToNull(layer.get("slotKey") == null
                        ? null : String.valueOf(layer.get("slotKey")));
                if (slotKey == null || !slotKeys.contains(slotKey)) {
                    throw badRequest("TEMPLATE_BROWSER_SCENE_SLOT_REFERENCE_INVALID",
                            "Photo layers must reference a declared template slot");
                }
            } else if ("static_image".equals(type) || "sticker".equals(type)
                    || "video".equals(type)) {
                String resourceKey = blankToNull(layer.get("resourceKey") == null
                        ? null : String.valueOf(layer.get("resourceKey")));
                String requiredKind = "video".equals(type) ? "video" : "image";
                if (resourceKey == null || !requiredKind.equals(resourceKinds.get(resourceKey))) {
                    throw badRequest("TEMPLATE_BROWSER_SCENE_RESOURCE_REFERENCE_INVALID",
                            "Image, sticker, and video layers must reference a matching resource");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void requireValidBrowserExecutionCapabilities(Map<String, Object> capability) {
        Object rawCapabilities = capability.get("executionCapabilities");
        if (!(rawCapabilities instanceof List) || ((List<?>) rawCapabilities).isEmpty()) {
            throw badRequest("TEMPLATE_BROWSER_EXECUTION_CAPABILITIES_REQUIRED",
                    "Browser scene v4 requires an execution capability list");
        }
        Set<String> allowedFidelity = new HashSet<String>(java.util.Arrays.asList(
                "exact", "semantic_approximation", "unsupported", "not_present"));
        Set<String> features = new HashSet<String>();
        Set<String> derivedBlocking = new HashSet<String>();
        for (Object raw : (List<?>) rawCapabilities) {
            if (!(raw instanceof Map)) {
                throw badRequest("TEMPLATE_BROWSER_EXECUTION_CAPABILITY_INVALID",
                        "Every browser execution capability must be an object");
            }
            Map<String, Object> item = (Map<String, Object>) raw;
            String feature = blankToNull(item.get("feature") == null
                    ? null : String.valueOf(item.get("feature")));
            String fidelity = blankToNull(item.get("fidelity") == null
                    ? null : String.valueOf(item.get("fidelity")));
            long declared = item.get("declaredCount") instanceof Number
                    ? ((Number) item.get("declaredCount")).longValue() : -1L;
            long executable = item.get("executableCount") instanceof Number
                    ? ((Number) item.get("executableCount")).longValue() : -1L;
            boolean expectedBlock = "unsupported".equals(fidelity) && declared > 0L;
            if (feature == null || !features.add(feature) || !allowedFidelity.contains(fidelity)
                    || declared < 0L || executable < 0L || executable > declared
                    || !Boolean.valueOf(expectedBlock).equals(item.get("blocksExport"))) {
                throw badRequest("TEMPLATE_BROWSER_EXECUTION_CAPABILITY_INVALID",
                        "Browser execution capabilities contain inconsistent counts or fidelity");
            }
            if (expectedBlock) derivedBlocking.add(feature);
        }
        Set<String> declaredBlocking = new HashSet<String>();
        Object rawBlocking = capability.get("blockingFeatures");
        if (rawBlocking instanceof List) {
            for (Object item : (List<?>) rawBlocking) {
                if (item != null) declaredBlocking.add(String.valueOf(item));
            }
        }
        boolean expectedReady = derivedBlocking.isEmpty()
                && Boolean.TRUE.equals(capability.get("browserLayerCompositionReady"));
        if (!derivedBlocking.equals(declaredBlocking)
                || !Boolean.valueOf(expectedReady).equals(capability.get("browserExportReady"))) {
            throw badRequest("TEMPLATE_BROWSER_EXECUTION_STATUS_INVALID",
                    "Browser export readiness does not match its declared blocking features");
        }
    }

    public Map<String, Object> updateMetadata(String templateId, TemplateMetadataUpdateRequest request) {
        requireTemplate(templateId);
        requireLeafCategory(request.getCategoryKey());
        Map<String, Object> source = repository.templateSourceMetadata(templateId);
        List<String> sourceHashtags = parseStringList(source == null ? null
                : RowUtils.str(source, "source_hashtags_json"));
        List<Map<String, Object>> categoryAssignments = categoryAssignments(
                request.getCategoryKey(), request.getCategoryKeys(),
                source == null ? "" : RowUtils.str(source, "source_title"),
                source == null ? "" : RowUtils.str(source, "source_description"),
                source == null ? "" : RowUtils.str(source, "source_category"),
                source == null ? "" : RowUtils.str(source, "source_search_keyword"),
                sourceHashtags, Boolean.TRUE.equals(request.getClassificationLocked()));
        String primaryCategory = resolvedPrimaryCategory(request.getCategoryKey(),
                categoryAssignments, Boolean.TRUE.equals(request.getClassificationLocked()));
        String visibility = blankToNull(request.getVisibility());
        if (visibility == null || !VISIBILITIES.contains(visibility)) {
            throw badRequest("TEMPLATE_VISIBILITY_INVALID", "Template visibility is invalid");
        }
        repository.updateMetadata(templateId, primaryCategory, json(sourceHashtags),
                request.getNameZh(), request.getDescriptionZh(), request.getNameEn(),
                request.getDescriptionEn(), visibility,
                request.getSortOrder() == null ? 0 : request.getSortOrder().intValue());
        repository.replaceTemplateCategories(templateId, primaryCategory, categoryAssignments);
        if (source != null) {
            repository.upsertTemplateSourceMetadata(templateId,
                    safe(RowUtils.str(source, "source_title")),
                    safe(RowUtils.str(source, "source_description")),
                    safe(RowUtils.str(source, "source_category")),
                    safe(RowUtils.str(source, "source_search_keyword")), json(sourceHashtags),
                    safe(RowUtils.str(source, "source_url")),
                    Boolean.TRUE.equals(request.getClassificationLocked()));
        }
        invalidateDetail(templateId);
        return detail(templateId, true);
    }

    public Map<String, Object> createMediaSession(String templateId, String versionId,
                                                   boolean video,
                                                   TemplateMediaUploadSessionRequest request) {
        requireVersion(templateId, versionId);
        String expectedRole = request.getRole();
        boolean slotDefault = !video && expectedRole != null
                && expectedRole.startsWith("slot_default:");
        boolean browserResource = expectedRole != null
                && expectedRole.startsWith("browser_resource:");
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
        if (browserResource && !isDeclaredBrowserResource(
                versionId, expectedRole, video ? "video" : null)) {
            throw badRequest("TEMPLATE_MEDIA_BROWSER_RESOURCE_INVALID",
                    "The browser resource role is not declared by the template scene");
        }
        if (video ? !("full_mv".equals(expectedRole) || browserResource)
                : !("cover".equals(expectedRole) || slotDefault || browserResource)) {
            throw badRequest("TEMPLATE_MEDIA_ROLE_INVALID", video
                    ? "Stream upload only accepts a full MV or declared browser video resource"
                    : "Images upload only accepts cover, a template slot photo, or a declared browser resource");
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
                invalidateDetail(templateId);
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
        invalidateDetail(templateId);
        Map<String, Object> result = mediaSessionView(mediaId, session.getUploadUrl(),
                session.getStatus(), providerDetails);
        result.put("idempotentReplay", Boolean.FALSE);
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean isDeclaredBrowserResource(
            String versionId, String expectedRole, String requiredKind) {
        Map<String, Object> row = repository.browserScene(versionId);
        if (row == null || !"ready".equals(RowUtils.str(row, "status"))) return false;
        Object rawResources = parseObject(RowUtils.str(row, "scene_json")).get("resources");
        if (!(rawResources instanceof List)) return false;
        for (Object rawResource : (List<?>) rawResources) {
            if (!(rawResource instanceof Map)) continue;
            Map<String, Object> resource = (Map<String, Object>) rawResource;
            String resourceKey = blankToNull(resource.get("resourceKey") == null
                    ? null : String.valueOf(resource.get("resourceKey")));
            String role = blankToNull(resource.get("role") == null
                    ? null : String.valueOf(resource.get("role")));
            if (expectedRole.equals(role)
                    && expectedRole.equals("browser_resource:" + resourceKey)
                    && (requiredKind == null
                            ? !"video".equals(String.valueOf(resource.get("kind")))
                            : requiredKind.equals(String.valueOf(resource.get("kind"))))) {
                return true;
            }
        }
        return false;
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
            invalidateDetail(templateId);
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
        invalidateDetail(templateId);
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
            invalidateDetail(templateId);
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
        invalidateDetail(templateId);

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
                schemaReady = categoryCount == MusicMvD1SchemaInitializer.ENABLED_CATEGORY_COUNT
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
        List<String> categoryKeys = parseStringList(RowUtils.str(row, "category_keys_json"));
        if (categoryKeys.isEmpty() && RowUtils.str(row, "category_key") != null) {
            categoryKeys.add(RowUtils.str(row, "category_key"));
        }
        result.put("categoryKeys", categoryKeys);
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

    private Map<String, Object> templateView(Map<String, Object> row, boolean admin) {
        String templateId = RowUtils.str(row, "template_id");
        return templateView(row, admin, categoryViews(templateId),
                admin ? repository.templateSourceMetadata(templateId) : null);
    }

    private Map<String, Object> templateView(Map<String, Object> row, boolean admin,
                                             List<Map<String, Object>> categoryRows,
                                             Map<String, Object> sourceMetadata) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        copy(result, "templateId", row, "template_id");
        copy(result, "capcutTemplateId", row, "capcut_template_id");
        copy(result, "slug", row, "slug");
        copy(result, "defaultLocale", row, "default_locale");
        copy(result, "categoryKey", row, "category_key");
        List<Map<String, Object>> categories = categoryViews(categoryRows);
        result.put("categoryKeys", categoryKeys(categories));
        result.put("categoryAssignments", categories);
        if (admin) result.put("sourceMetadata", sourceMetadataView(sourceMetadata));
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
        publicDetailCache.invalidateAll();
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

    private Map<String, Object> technicalFilters() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> aspectRatios = new ArrayList<Map<String, Object>>();
        aspectRatios.add(filterOption("9:16", "Portrait 9:16", "竖屏 9:16"));
        aspectRatios.add(filterOption("1:1", "Square 1:1", "方形 1:1"));
        aspectRatios.add(filterOption("16:9", "Landscape 16:9", "横屏 16:9"));
        result.put("aspectRatios", aspectRatios);
        result.put("slotCount", filterRange(0, 100));
        result.put("durationSeconds", filterRange(0, 900));
        return result;
    }

    private Map<String, Object> filterOption(String value, String nameEn, String nameZh) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("value", value); result.put("nameEn", nameEn); result.put("nameZh", nameZh);
        return result;
    }

    private Map<String, Object> filterRange(int min, int max) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("min", Integer.valueOf(min)); result.put("max", Integer.valueOf(max));
        return result;
    }

    private String normalizeAspectRatio(String value) {
        String ratio = blankToNull(value);
        if (ratio == null) return null;
        if (!"9:16".equals(ratio) && !"1:1".equals(ratio) && !"16:9".equals(ratio)) {
            throw badRequest("TEMPLATE_ASPECT_RATIO_INVALID", "Template aspect ratio is invalid");
        }
        return ratio;
    }

    private List<String> rawHashtags(List<String> values) {
        LinkedHashSet<String> unique = new LinkedHashSet<String>();
        if (values != null) {
            for (String raw : values) {
                String value = blankToNull(raw);
                if (value != null && value.length() <= 120) unique.add(value);
            }
        }
        if (unique.size() > 80) {
            throw badRequest("TEMPLATE_SOURCE_HASHTAGS_TOO_MANY",
                    "CapCut source hashtags exceed the supported evidence limit");
        }
        return new ArrayList<String>(unique);
    }

    private List<Map<String, Object>> categoryAssignments(
            String primary, List<String> selected, String title, String description,
            String sourceCategory, String searchKeyword, List<String> hashtags,
            boolean locked) {
        Map<String, Map<String, Object>> automatic = locked
                ? Collections.<String, Map<String, Object>>emptyMap()
                : classifySource(title, description, sourceCategory, searchKeyword, hashtags);
        boolean hasAutomaticPrimary = !automatic.isEmpty();
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        if (!hasAutomaticPrimary) keys.add(primary);
        keys.addAll(automatic.keySet());
        if (selected != null) {
            for (String raw : selected) {
                String key = blankToNull(raw);
                if (key != null && (locked || !hasAutomaticPrimary || !key.equals(primary))) {
                    keys.add(key);
                }
            }
        }
        for (String key : keys) requireLeafCategory(key);
        if (keys.size() > 8) {
            throw badRequest("TEMPLATE_CATEGORIES_TOO_MANY",
                    "A template can appear in at most 8 public categories");
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (String key : keys) {
            Map<String, Object> item = automatic.get(key);
            if (item == null) {
                item = new LinkedHashMap<String, Object>();
                item.put("categoryKey", key);
                item.put("source", "manual");
                item.put("confidence", Double.valueOf(1.0));
                List<Map<String, Object>> evidence = new ArrayList<Map<String, Object>>();
                Map<String, Object> fact = new LinkedHashMap<String, Object>();
                fact.put("field", key.equals(primary) ? "primaryCategory" : "selectedCategory");
                fact.put("value", key);
                evidence.add(fact);
                item.put("evidenceJson", json(evidence));
            }
            result.add(item);
        }
        return result;
    }

    private String resolvedPrimaryCategory(
            String fallback,
            List<Map<String, Object>> assignments,
            boolean locked
    ) {
        if (locked) return fallback;
        String primary = null;
        double confidence = -1.0d;
        for (Map<String, Object> assignment : assignments) {
            if (!"automatic".equals(String.valueOf(assignment.get("source")))) continue;
            Object rawConfidence = assignment.get("confidence");
            double candidateConfidence = rawConfidence instanceof Number
                    ? ((Number) rawConfidence).doubleValue() : 0.0d;
            if (primary == null || candidateConfidence > confidence) {
                primary = String.valueOf(assignment.get("categoryKey"));
                confidence = candidateConfidence;
            }
        }
        return primary == null ? fallback : primary;
    }

    private Map<String, Map<String, Object>> classifySource(
            String title, String description, String sourceCategory,
            String searchKeyword, List<String> hashtags) {
        String hashtagText = hashtags == null ? "" : joinLower(hashtags);
        String[][] rules = new String[][] {
                {"birthday", "birthday", "birth day", "bday", "happybirthday",
                        "cumpleaños", "cumpleanos", "生日"},
                {"wedding", "wedding", "bride", "groom", "婚礼", "结婚"},
                {"anniversary", "anniversary", "纪念日", "周年"},
                {"graduation", "graduation", "graduate", "毕业"},
                {"holidays-parties", "christmas", "holiday", "party", "festival",
                        "mother's day", "mothers day", "mothersday", "father's day",
                        "fathers day", "fathersday", "thanksgiving", "母亲节", "父亲节",
                        "节日", "派对"},
                {"family", "family", "familia", "família", "mom", "mommy", "mama",
                        "mum", "mother", "dad", "daddy", "papa", "father", "parent",
                        "grandparent", "grandma", "grandpa", "daughter", "brother", "sister",
                        "son", "sons", "sibling", "cousin", "aunt", "uncle", "niece",
                        "nephew", "bestmom", "bestdad", "momlife", "dadlife", "家庭",
                        "家人", "亲情", "妈妈", "母亲",
                        "爸爸", "父亲", "爷爷", "奶奶", "外公", "外婆", "女儿",
                        "儿子", "兄弟", "姐妹"},
                {"baby-kids", "baby", "babies", "kid", "kids", "child", "children",
                        "newborn", "toddler", "infant", "baby girl", "baby boy", "宝宝",
                        "婴儿", "孩子", "儿童", "幼儿"},
                {"couples", "couple", "relationship", "boyfriend", "girlfriend", "情侣", "恋爱"},
                {"friendship", "friendship", "friends", "bestfriend", "友情", "朋友", "闺蜜"},
                {"daily-life", "daily", "vlog", "dayinmylife", "day in my life",
                        "family time", "familytime", "home life", "weekend", "日常", "生活"},
                {"travel", "travel", "trip", "vacation", "family vacation", "roadtrip",
                        "road trip", "journey", "旅行", "旅游", "度假"},
                {"school-life", "school", "campus", "classmate", "校园", "同学"},
                {"growing-up", "growth", "growing", "growing up", "growup", "milestone",
                        "first year", "firstyear", "1st year", "month old", "monthold",
                        "months old", "monthsold", "childhood", "first steps", "first smile",
                        "baby growth", "babygrowth", "mêsversário",
                        "mesversario", "成长", "月龄", "满月", "百天", "周岁"},
                {"recap", "recap", "review", "year in review", "memory", "memories",
                        "family memories", "photo dump", "photodump", "album", "montage",
                        "回顾", "总结", "回忆", "相册"},
                {"hobbies-interests", "sports", "gaming", "anime", "hobby", "运动", "游戏", "兴趣"},
                {"motivation", "motivation", "inspiration", "励志", "鼓励"},
                {"healing", "healing", "comfort", "疗愈", "治愈"},
                {"love-thanks", "grateful", "gratitude", "thank", "appreciate", "love",
                        "love you", "best mom", "bestmom", "best mum", "best dad", "bestdad",
                        "ilovemom", "ilovedad", "mother's day",
                        "mothers day", "mothersday", "father's day", "fathers day", "fathersday",
                        "感谢", "感恩", "爱", "最好的妈妈", "最好的爸爸"},
                {"farewell-breakup", "farewell", "goodbye", "breakup", "告别", "分手"},
                {"memorial", "memorial", "remembering", "remembrance", "deceased",
                        "in memory of", "in heaven", "heavenly", "rest in peace", "passed away",
                        "缅怀", "追思", "怀念", "已故", "逝世"}
        };
        Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
        for (String[] rule : rules) {
            String key = rule[0];
            String[] terms = new String[rule.length - 1];
            System.arraycopy(rule, 1, terms, 0, terms.length);
            List<Map<String, Object>> evidence = new ArrayList<Map<String, Object>>();
            double confidence = 0.0;
            confidence = Math.max(confidence, evidence(evidence, "sourceCategory", sourceCategory, 1.0, terms));
            confidence = Math.max(confidence, evidence(evidence, "sourceHashtags", hashtagText, 0.9, terms));
            confidence = Math.max(confidence, evidence(evidence, "sourceTitle", title, 0.8, terms));
            confidence = Math.max(confidence, evidence(evidence, "sourceDescription", description, 0.65, terms));
            confidence = Math.max(confidence, evidence(evidence, "sourceSearchKeyword", searchKeyword, 0.55, terms));
            if (confidence >= 0.75) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("categoryKey", key);
                item.put("source", "automatic");
                item.put("confidence", Double.valueOf(confidence));
                item.put("evidenceJson", json(evidence));
                result.put(key, item);
            }
        }
        return result;
    }

    private double evidence(List<Map<String, Object>> facts, String field, String value,
                            double weight, String[] terms) {
        String text = safe(value).toLowerCase(java.util.Locale.ROOT);
        if (text.isEmpty()) return 0.0;
        for (String term : terms) {
            if (containsClassificationTerm(text, term)) {
                Map<String, Object> fact = new LinkedHashMap<String, Object>();
                fact.put("field", field);
                fact.put("matched", term);
                fact.put("weight", Double.valueOf(weight));
                facts.add(fact);
                return weight;
            }
        }
        return 0.0;
    }

    /** Avoids short-token collisions such as "mom" inside "moments". */
    private boolean containsClassificationTerm(String text, String rawTerm) {
        String term = safe(rawTerm).toLowerCase(java.util.Locale.ROOT);
        if (term.isEmpty()) return false;
        if (!term.matches("^[a-z0-9]{1,3}$")) return text.contains(term);
        int offset = 0;
        while (offset <= text.length() - term.length()) {
            int index = text.indexOf(term, offset);
            if (index < 0) return false;
            int end = index + term.length();
            boolean leftBoundary = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
            boolean rightBoundary = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (leftBoundary && rightBoundary) return true;
            offset = index + 1;
        }
        return false;
    }

    private String joinLower(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) result.append(' ').append(safe(value));
        return result.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private List<Map<String, Object>> categoryViews(String templateId) {
        return categoryViews(repository.templateCategories(templateId));
    }

    private List<Map<String, Object>> categoryViews(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            copy(item, "categoryKey", row, "category_key");
            item.put("primary", Boolean.valueOf(RowUtils.bool(row, "is_primary")));
            copy(item, "source", row, "source");
            copy(item, "confidence", row, "confidence");
            item.put("evidence", parseList(RowUtils.str(row, "evidence_json")));
            copy(item, "nameZh", row, "name_zh");
            copy(item, "nameEn", row, "name_en");
            result.add(item);
        }
        return result;
    }

    private List<String> categoryKeys(String templateId) {
        return categoryKeys(categoryViews(templateId));
    }

    private List<String> categoryKeys(List<Map<String, Object>> categories) {
        List<String> result = new ArrayList<String>();
        for (Map<String, Object> category : categories) {
            result.add(String.valueOf(category.get("categoryKey")));
        }
        return result;
    }

    private Map<String, Object> sourceMetadataView(String templateId) {
        return sourceMetadataView(repository.templateSourceMetadata(templateId));
    }

    private Map<String, Object> sourceMetadataView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (row == null) return result;
        copy(result, "sourceTitle", row, "source_title");
        copy(result, "sourceDescription", row, "source_description");
        copy(result, "sourceCategory", row, "source_category");
        copy(result, "sourceSearchKeyword", row, "source_search_keyword");
        result.put("sourceHashtags", parseList(RowUtils.str(row, "source_hashtags_json")));
        copy(result, "sourceUrl", row, "source_url");
        copy(result, "classifierVersion", row, "classifier_version");
        result.put("classificationLocked", Boolean.valueOf(RowUtils.bool(row, "classification_locked")));
        return result;
    }

    private List<String> parseStringList(String value) {
        List<String> result = new ArrayList<String>();
        for (Object item : parseList(value)) {
            String text = blankToNull(String.valueOf(item));
            if (text != null) result.add(text);
        }
        return result;
    }

    private String safe(String value) { return value == null ? "" : value; }

    private List<String> normalizedKeywords(List<String> preferred, List<String> legacy) {
        List<String> source = preferred != null && !preferred.isEmpty() ? preferred : legacy;
        LinkedHashSet<String> unique = new LinkedHashSet<String>();
        if (source != null) {
            for (String raw : source) {
                String value = blankToNull(raw);
                if (value == null) continue;
                if (value.length() > 40) {
                    throw badRequest("TEMPLATE_KEYWORD_TOO_LONG", "A template keyword is too long");
                }
                unique.add(value);
            }
        }
        if (unique.size() > 30) {
            throw badRequest("TEMPLATE_KEYWORDS_TOO_MANY", "A template can have at most 30 keywords");
        }
        return new ArrayList<String>(unique);
    }

    private List<String> requireCollectionKeys(List<String> values) {
        LinkedHashSet<String> unique = new LinkedHashSet<String>();
        if (values != null) {
            for (String raw : values) {
                String value = blankToNull(raw);
                if (value == null || !unique.add(value)) continue;
                Map<String, Object> collection = repository.collection(value);
                if (collection == null || !RowUtils.bool(collection, "enabled")) {
                    throw badRequest("TEMPLATE_COLLECTION_INVALID", "Template collection is not enabled");
                }
            }
        }
        if (unique.size() > 12) {
            throw badRequest("TEMPLATE_COLLECTIONS_TOO_MANY", "A template can appear in at most 12 collections");
        }
        return new ArrayList<String>(unique);
    }

    private List<String> collectionKeys(String templateId) {
        List<String> result = new ArrayList<String>();
        for (Map<String, Object> row : repository.templateCollections(templateId)) {
            result.add(RowUtils.str(row, "collection_key"));
        }
        return result;
    }

    private List<Map<String, Object>> collectionViews(List<Map<String, Object>> rows, String locale) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) result.add(collectionView(row, locale));
        return result;
    }

    private Map<String, Object> collectionView(Map<String, Object> row, String locale) {
        boolean english = normalizeLocale(locale).startsWith("en");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        copy(result, "key", row, "collection_key");
        copy(result, "slug", row, "slug");
        copy(result, "parentCategoryKey", row, "parent_category_key");
        copy(result, "keyword", row, "keyword");
        result.put("name", RowUtils.str(row, english ? "name_en" : "name_zh"));
        copy(result, "nameZh", row, "name_zh");
        copy(result, "nameEn", row, "name_en");
        result.put("description", RowUtils.str(row,
                english ? "description_en" : "description_zh"));
        copy(result, "descriptionZh", row, "description_zh");
        copy(result, "descriptionEn", row, "description_en");
        copy(result, "seoTitle", row, "seo_title");
        copy(result, "seoDescription", row, "seo_description");
        copy(result, "sortOrder", row, "sort_order");
        return result;
    }

    private void requireCategory(String categoryKey) {
        Map<String, Object> category = repository.category(categoryKey);
        if (category == null || !RowUtils.bool(category, "enabled")) {
            throw badRequest("TEMPLATE_CATEGORY_INVALID", "Template category is not enabled");
        }
    }

    private void requireLeafCategory(String categoryKey) {
        requireCategory(categoryKey);
        Map<String, Object> category = repository.category(categoryKey);
        if (!RowUtils.bool(category, "is_selectable")) {
            throw badRequest("TEMPLATE_CATEGORY_NOT_SELECTABLE",
                    "A template must belong to one selectable leaf category");
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
                if ("inlinedata".equals(key)) {
                    if (entry.getValue() != null
                            && !String.valueOf(entry.getValue()).trim().isEmpty()) {
                        requireSafeInlineBrowserResource(entry.getValue());
                    }
                    continue;
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

    private void requireSafeInlineBrowserResource(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        int comma = text.indexOf(',');
        String header = comma < 0 ? "" : text.substring(0, comma).toLowerCase();
        if (!(header.equals("data:font/ttf;base64")
                || header.equals("data:font/otf;base64")
                || header.equals("data:font/woff;base64")
                || header.equals("data:font/woff2;base64"))
                || text.length() > 8 * 1024 * 1024) {
            throw badRequest("TEMPLATE_BROWSER_RESOURCE_INLINE_INVALID",
                    "Inline browser resources only accept bounded font data");
        }
        try {
            Base64.getDecoder().decode(text.substring(comma + 1));
        } catch (IllegalArgumentException exception) {
            throw badRequest("TEMPLATE_BROWSER_RESOURCE_INLINE_INVALID",
                    "Inline browser resource is not valid base64 data");
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
