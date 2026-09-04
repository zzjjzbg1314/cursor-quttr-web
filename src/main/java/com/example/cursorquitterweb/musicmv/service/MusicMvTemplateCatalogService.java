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
import com.example.cursorquitterweb.musicmv.dto.TemplateBrowserParityRequest;
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
    private static final double BROWSER_SSIM_MINIMUM = 0.900d;
    private static final double BROWSER_MAX_MAE = 25.0d;
    private static final double BROWSER_MAX_DURATION_DRIFT_SECONDS = 0.12d;
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
        if ("browser-template-scene-v4".equals(request.getSchemaVersion())
                || "browser-template-scene-v5".equals(request.getSchemaVersion())
                || "browser-template-scene-v6".equals(request.getSchemaVersion())) {
            requireValidBrowserSceneGraph(scene);
            requireValidBrowserExecutionCapabilities(capability);
        }
        if ("browser-template-scene-v5".equals(request.getSchemaVersion())
                || "browser-template-scene-v6".equals(request.getSchemaVersion())) {
            requireValidBrowserCapabilityReport(scene, capability);
        }
        if ("browser-template-scene-v6".equals(request.getSchemaVersion())) {
            requireValidBrowserRenderIr(scene);
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
    private void requireValidBrowserRenderIr(Map<String, Object> scene) {
        Object rawIr = scene.get("renderIr");
        if (!(rawIr instanceof Map)) {
            throw badRequest("TEMPLATE_BROWSER_RENDER_IR_REQUIRED",
                    "Browser scene v6 requires a compiled Render IR");
        }
        Map<String, Object> ir = (Map<String, Object>) rawIr;
        if (!"browser-render-ir-v2".equals(RowUtils.str(ir, "version"))) {
            throw badRequest("TEMPLATE_BROWSER_RENDER_IR_VERSION_UNSUPPORTED",
                    "Compiled Render IR version is unsupported");
        }
        Map<String, Object> policy = ir.get("policy") instanceof Map
                ? (Map<String, Object>) ir.get("policy")
                : Collections.<String, Object>emptyMap();
        if (!"canvas_center_normalized_y_up".equals(RowUtils.str(policy, "coordinateSpace"))
                || !"seconds".equals(RowUtils.str(policy, "timeUnit"))
                || !"dual_input_ab_v1".equals(RowUtils.str(policy, "transitionClock"))
                || !"block_export".equals(RowUtils.str(policy, "unsupported"))) {
            throw badRequest("TEMPLATE_BROWSER_RENDER_IR_POLICY_INVALID",
                    "Compiled Render IR uses an unsupported coordinate, clock, or fallback policy");
        }
        if (!(ir.get("canvas") instanceof Map)
                || !(ir.get("layers") instanceof List)
                || !(ir.get("postEffects") instanceof List)
                || !(ir.get("resources") instanceof List)) {
            throw badRequest("TEMPLATE_BROWSER_RENDER_IR_INVALID",
                    "Compiled Render IR canvas, layers, effects, and resources are required");
        }
        List<?> sceneLayers = scene.get("layers") instanceof List
                ? (List<?>) scene.get("layers") : Collections.emptyList();
        List<?> irLayers = (List<?>) ir.get("layers");
        if (sceneLayers.size() != irLayers.size()) {
            throw badRequest("TEMPLATE_BROWSER_RENDER_IR_LAYER_MISMATCH",
                    "Compiled Render IR must contain every public scene layer exactly once");
        }
        Map<String, String> expected = new LinkedHashMap<String, String>();
        for (Object raw : sceneLayers) {
            Map<?, ?> layer = (Map<?, ?>) raw;
            expected.put(String.valueOf(layer.get("layerId")), String.valueOf(layer.get("type")));
        }
        Set<String> actual = new HashSet<String>();
        for (Object raw : irLayers) {
            if (!(raw instanceof Map)) {
                throw badRequest("TEMPLATE_BROWSER_RENDER_IR_LAYER_INVALID",
                        "Compiled Render IR layers must be objects");
            }
            Map<String, Object> layer = (Map<String, Object>) raw;
            String id = RowUtils.str(layer, "id");
            String kind = RowUtils.str(layer, "kind");
            if (id.isEmpty() || !kind.equals(expected.get(id)) || !actual.add(id)
                    || !finiteNonNegative(layer.get("startSeconds"), false)
                    || !finiteNonNegative(layer.get("durationSeconds"), true)
                    || !(layer.get("source") instanceof Map)
                    || !(layer.get("keyframes") instanceof List)
                    || !(layer.get("animations") instanceof List)
                    || !(layer.get("effects") instanceof List)) {
                throw badRequest("TEMPLATE_BROWSER_RENDER_IR_LAYER_INVALID",
                        "Compiled Render IR layer identity, timing, or executable state is invalid");
            }
        }
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
                if (resourceKey != null && kind != null) {
                    resourceKinds.put(resourceKey, kind);
                    if ("font".equals(kind)) requireValidBrowserFontResource(resource);
                }
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
            } else if ("text".equals(type)) {
                requireValidBrowserTextFonts(layer, resourceKinds);
                requireValidBrowserTextLayerFidelity(layer);
            }
            requireValidBrowserLayerAnimations(layer);
            requireValidBrowserLayerTransition(layer);
            requireValidBrowserLayerEffects(layer);
            requireValidBrowserLayerMask(layer);
        }
        requireValidBrowserPostEffects(scene, resourceKinds);
    }

    private void requireValidBrowserPostEffects(
            Map<String, Object> scene, Map<String, String> resourceKinds) {
        Object rawEffects = scene.get("postEffects");
        if (rawEffects == null) return;
        if (!(rawEffects instanceof List)) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_POST_EFFECT_INVALID",
                    "Browser scene post effects must be a list");
        }
        Set<String> allowedPresets = new HashSet<String>(java.util.Arrays.asList(
                "fade_to_black", "dual_lut_skin_mask", "dual_lut_filter_approximation",
                "orange_green_filter_approximation", "unsupported"));
        Set<String> allowedFidelity = new HashSet<String>(java.util.Arrays.asList(
                "exact", "semantic_approximation", "unsupported"));
        for (Object raw : (List<?>) rawEffects) {
            if (!(raw instanceof Map)) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_POST_EFFECT_INVALID",
                        "Browser scene post effects must be objects");
            }
            Map<?, ?> effect = (Map<?, ?>) raw;
            String preset = blankToNull(effect.get("preset") == null
                    ? null : String.valueOf(effect.get("preset")));
            String fidelity = blankToNull(effect.get("fidelity") == null
                    ? null : String.valueOf(effect.get("fidelity")));
            if (!allowedPresets.contains(preset) || !allowedFidelity.contains(fidelity)
                    || !finiteNonNegative(effect.get("targetStartSeconds"), false)
                    || !finiteNonNegative(effect.get("targetDurationSeconds"), true)
                    || !finiteNonNegative(effect.get("intensity"), false)) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_POST_EFFECT_INVALID",
                        "Browser scene post effect timing, intensity, preset, or fidelity is invalid");
            }
            if ("dual_lut_skin_mask".equals(preset)
                    && !validBrowserPostEffectContract(effect)) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_POST_EFFECT_CONTRACT_INVALID",
                        "Verified browser LUT effects require their complete semantic contract");
            }
            if (!"dual_lut_skin_mask".equals(preset)
                    && !"dual_lut_filter_approximation".equals(preset)
                    && !"orange_green_filter_approximation".equals(preset)) continue;
            Object rawKeys = effect.get("resourceKeys");
            if (!(rawKeys instanceof Map)) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_POST_EFFECT_RESOURCE_INVALID",
                        "Browser LUT effects must reference their published resources");
            }
            Map<?, ?> keys = (Map<?, ?>) rawKeys;
            String background = blankToNull(keys.get("background") == null
                    ? null : String.valueOf(keys.get("background")));
            String skin = blankToNull(keys.get("skin") == null
                    ? null : String.valueOf(keys.get("skin")));
            if (background == null || !"lut_2d_png".equals(resourceKinds.get(background))
                    || (skin != null && !"lut_2d_png".equals(resourceKinds.get(skin)))) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_POST_EFFECT_RESOURCE_INVALID",
                        "Browser LUT effects must reference LUT resources from this scene");
            }
        }
    }

    private boolean validBrowserPostEffectContract(Map<?, ?> effect) {
        return "browser-post-effect-semantic-v2".equals(effect.get("contractVersion"))
                && "dual_lut_skin_mask".equals(effect.get("semanticFamily"))
                && "64_cube_8x8_floor_blue_linear_rg".equals(effect.get("lutSampling"))
                && "skin_seg_alpha_y_flipped".equals(effect.get("maskSource"))
                && "mediapipe_selfie_multiclass_256".equals(effect.get("maskProvider"))
                && "max_body_skin_face_skin_confidence".equals(
                        effect.get("maskClassComposition"))
                && "ten_hz_reuse_with_seek_refresh".equals(
                        effect.get("maskRefreshContract"))
                && "source_to_mask_selected_lut".equals(effect.get("intensityMix"))
                && "preserve_source_alpha".equals(effect.get("alphaContract"))
                && "browser_mediapipe_model_not_capcut_skin_seg".equals(
                        effect.get("approximationBoundary"))
                && "package_skinseg_shader_algorithm_and_dual_lut_media".equals(
                        effect.get("evidence"));
    }

    private boolean finiteNonNegative(Object value, boolean positive) {
        if (!(value instanceof Number)) return false;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && (positive ? number > 0.0d : number >= 0.0d);
    }

    private void requireValidBrowserLayerAnimations(Map<String, Object> layer) {
        Object rawAnimations = layer.get("animations");
        if (rawAnimations == null) return;
        if (!(rawAnimations instanceof List)) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_ANIMATION_INVALID",
                    "Browser scene animations must be a list");
        }
        Set<String> allowed = new HashSet<String>(java.util.Arrays.asList(
                "noop", "fade_in", "fade_out", "text_reveal", "lumi_video_animation",
                "keyframe_transform",
                "jitter_approximation", "scale_down_approximation",
                "scale_up_approximation", "blur_in_approximation",
                "fade_approximation", "translate_approximation",
                "generic_transform_approximation", "unsupported"));
        for (Object raw : (List<?>) rawAnimations) {
            requireValidBrowserTimedPreset(raw, allowed,
                    "TEMPLATE_BROWSER_SCENE_ANIMATION_INVALID", true);
            Map<?, ?> animation = (Map<?, ?>) raw;
            if ("lumi_video_animation".equals(String.valueOf(animation.get("preset")))
                    && !("browser-animation-semantic-v1".equals(animation.get("contractVersion"))
                            && "lumi_video_animation".equals(animation.get("semanticFamily"))
                            && "stretch_composition_to_declared_duration".equals(
                                    animation.get("packageClock"))
                            && "left_red_alpha_right_rgb_half_frame".equals(
                                    animation.get("alphaPacking"))
                            && "stretch_output_space".equals(animation.get("cropMode"))
                            && "source_in_mask_then_frame_over".equals(
                                    animation.get("matteComposition"))
                            && "ae_camera_inverse_ray_projective_quad".equals(
                                    animation.get("motionProjection"))
                            && "per_component_cubic_bezier".equals(
                                    animation.get("curveInterpolation"))
                            && "package_duration_table_node_order".equals(
                                    animation.get("compositionOrder"))
                            && "package_lumi_export_lua_motion_shader_and_media".equals(
                                    animation.get("evidence")))) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_ANIMATION_CONTRACT_INVALID",
                        "Exact Lumi video animations require the verified semantic contract");
            }
        }
    }

    private void requireValidBrowserTextLayerFidelity(Map<String, Object> layer) {
        String fidelity = blankToNull(layer.get("fidelity") == null
                ? null : String.valueOf(layer.get("fidelity")));
        String templateResourceId = blankToNull(layer.get("templateResourceId") == null
                ? null : String.valueOf(layer.get("templateResourceId")));
        if (fidelity != null && !"exact".equals(fidelity)
                && !"semantic_approximation".equals(fidelity)) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_TEXT_FIDELITY_INVALID",
                    "Browser text fidelity must be exact or semantic approximation");
        }
        if (templateResourceId != null && !"semantic_approximation".equals(fidelity)) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_TEXT_FIDELITY_INVALID",
                    "Expanded CapCut text templates must declare semantic approximation");
        }
    }

    private void requireValidBrowserLayerTransition(Map<String, Object> layer) {
        Object rawTransition = layer.get("transitionIn");
        if (rawTransition == null) return;
        Set<String> allowed = new HashSet<String>(java.util.Arrays.asList(
                "ab_progress_mix", "soft_fade", "white_flash_approximation", "wipe_approximation",
                "blur_crossfade_approximation", "push_slide_approximation",
                "scale_zoom_approximation", "unsupported"));
        requireValidBrowserTimedPreset(rawTransition, allowed,
                "TEMPLATE_BROWSER_SCENE_TRANSITION_INVALID", true);
        Map<?, ?> transition = (Map<?, ?>) rawTransition;
        if ("ab_progress_mix".equals(String.valueOf(transition.get("preset")))
                && !("browser-transition-semantic-v1".equals(transition.get("contractVersion"))
                        && "ab_progress_mix".equals(transition.get("semanticFamily"))
                        && "normalized_transition_progress".equals(
                                transition.get("progressMapping"))
                        && "one_minus_progress".equals(transition.get("inputAWeight"))
                        && "progress".equals(transition.get("inputBWeight"))
                        && "opaque".equals(transition.get("alphaOutputContract"))
                        && "timeline_ab_after_source_graph".equals(
                                transition.get("applicationStage"))
                        && "package_shader_two_input_progress_mix".equals(
                                transition.get("evidence")))) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_TRANSITION_CONTRACT_INVALID",
                    "Exact A/B progress mix transitions require the verified semantic contract");
        }
    }

    private void requireValidBrowserLayerEffects(Map<String, Object> layer) {
        Object rawEffects = layer.get("effects");
        if (rawEffects == null) return;
        if (!(rawEffects instanceof List)) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_LAYER_EFFECT_INVALID",
                    "Browser scene layer effects must be a list");
        }
        Set<String> allowed = new HashSet<String>(java.util.Arrays.asList(
                "turbulence_bounce_shake", "texture_sequence_screen_multiply",
                "shake_approximation", "noise_approximation", "unsupported"));
        for (Object raw : (List<?>) rawEffects) {
            requireValidBrowserTimedPreset(raw, allowed,
                    "TEMPLATE_BROWSER_SCENE_LAYER_EFFECT_INVALID", false);
            Map<?, ?> effect = (Map<?, ?>) raw;
            String preset = String.valueOf(effect.get("preset"));
            if (("turbulence_bounce_shake".equals(preset)
                    || "texture_sequence_screen_multiply".equals(preset))
                    && !validBrowserLayerEffectContract(effect, preset)) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_LAYER_EFFECT_CONTRACT_INVALID",
                        "Exact packaged layer effects require the verified semantic contract");
            }
            for (String field : java.util.Arrays.asList(
                    "intensity", "speed", "distortion", "sharpen")) {
                Object value = effect.get(field);
                if (value != null && (!(value instanceof Number)
                        || !Double.isFinite(((Number) value).doubleValue()))) {
                    throw badRequest("TEMPLATE_BROWSER_SCENE_LAYER_EFFECT_INVALID",
                            "Browser scene layer effect parameters must be finite numbers");
                }
            }
        }
    }

    private boolean validBrowserLayerEffectContract(Map<?, ?> effect, String preset) {
        if (!preset.equals(effect.get("semanticFamily"))) return false;
        if ("turbulence_bounce_shake".equals(preset)) {
            return "browser-layer-effect-semantic-v4".equals(effect.get("contractVersion"))
                    && "local_seconds_times_half_plus_speed_times_one_point_five".equals(
                            effect.get("packageClock"))
                    && "downsample_passthrough_then_difference_sharpen_then_turbulence_then_shake_then_bounce".equals(
                            effect.get("passOrder"))
                    && "source_graph_before_video_animation".equals(
                            effect.get("applicationStage"))
                    && "preserve_source_alpha".equals(effect.get("alphaContract"))
                    && "person_matting".equals(effect.get("maskProvider"))
                    && "share_bgmask_red_y_flipped".equals(effect.get("maskSource"))
                    && "effect_pass_input_rgba".equals(effect.get("maskInput"))
                    && "max_original_3x3_radius_10_texels_and_displaced_then_restore_uv".equals(
                            effect.get("maskProtection"))
                    && "browser_person_matting_model".equals(
                            effect.get("approximationBoundary"))
                    && "package_lua_and_five_fragment_passes".equals(effect.get("evidence"));
        }
        return "browser-layer-effect-semantic-v4".equals(effect.get("contractVersion"))
                && "effect_local_seconds_clamped_to_declared_range_then_times_half_plus_speed_times_one_point_five"
                        .equals(effect.get("packageClock"))
                && "screen_sequence_then_multiply_texture".equals(effect.get("passOrder"))
                && "source_graph_before_video_animation".equals(
                        effect.get("applicationStage"))
                && "serialized_sequence_order_floor_clock".equals(
                        effect.get("sequenceSampling"))
                && effect.get("sequenceDurationSeconds") instanceof Number
                && ((Number) effect.get("sequenceDurationSeconds")).doubleValue() > 0.0d
                && "clamp".equals(effect.get("sequencePlaybackMode"))
                && "hold_last_frame".equals(effect.get("sequenceEndBehavior"))
                && "straight_alpha_screen".equals(effect.get("screenBlend"))
                && "inverse_premultiplied_straight_alpha_multiply".equals(
                        effect.get("multiplyBlend"))
                && "opaque_result".equals(effect.get("alphaContract"))
                && "package_lua_sequence_and_blend_shaders".equals(effect.get("evidence"));
    }

    private void requireValidBrowserTimedPreset(
            Object raw,
            Set<String> allowedPresets,
            String errorCode,
            boolean requireDuration) {
        if (!(raw instanceof Map)) {
            throw badRequest(errorCode, "Browser scene presets must be objects");
        }
        Map<?, ?> value = (Map<?, ?>) raw;
        String preset = blankToNull(value.get("preset") == null
                ? null : String.valueOf(value.get("preset")));
        Object duration = value.get("durationSeconds");
        String fidelity = blankToNull(value.get("fidelity") == null
                ? null : String.valueOf(value.get("fidelity")));
        Set<String> allowedFidelity = new HashSet<String>(java.util.Arrays.asList(
                "exact", "semantic_approximation", "unsupported"));
        if (preset == null || !allowedPresets.contains(preset)
                || (requireDuration && (!(duration instanceof Number)
                        || !Double.isFinite(((Number) duration).doubleValue())
                        || ((Number) duration).doubleValue() < 0.0d))
                || (fidelity != null && !allowedFidelity.contains(fidelity))) {
            throw badRequest(errorCode,
                    "Browser scene preset, duration, or fidelity is invalid");
        }
    }

    private void requireValidBrowserFontResource(Map<?, ?> resource) {
        String family = blankToNull(resource.get("fontFamily") == null
                ? null : String.valueOf(resource.get("fontFamily")));
        String inlineData = blankToNull(resource.get("inlineData") == null
                ? null : String.valueOf(resource.get("inlineData")));
        if (family == null || inlineData == null || !inlineData.startsWith("data:font/")) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_FONT_RESOURCE_INVALID",
                    "Inline browser fonts require a family and a font data URL");
        }
    }

    @SuppressWarnings("unchecked")
    private void requireValidBrowserTextFonts(
            Map<String, Object> layer,
            Map<String, String> resourceKinds) {
        Object rawStyle = layer.get("style");
        if (rawStyle instanceof Map) {
            requireBrowserFontReference((Map<String, Object>) rawStyle, resourceKinds);
        }
        Object rawRuns = layer.get("runs");
        if (!(rawRuns instanceof List)) return;
        for (Object rawRun : (List<?>) rawRuns) {
            if (!(rawRun instanceof Map)) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_TEXT_RUN_INVALID",
                        "Browser text runs must be objects");
            }
            Map<String, Object> run = (Map<String, Object>) rawRun;
            requireBrowserFontReference(run, resourceKinds);
            Object start = run.get("start");
            Object end = run.get("end");
            if (!(start instanceof Number) || !(end instanceof Number)
                    || ((Number) start).longValue() < 0L
                    || ((Number) end).longValue() < ((Number) start).longValue()) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_TEXT_RUN_INVALID",
                        "Browser text runs require ordered UTF-16 ranges");
            }
        }
    }

    private void requireBrowserFontReference(
            Map<String, Object> source,
            Map<String, String> resourceKinds) {
        String resourceKey = blankToNull(source.get("fontResourceKey") == null
                ? null : String.valueOf(source.get("fontResourceKey")));
        if (resourceKey != null && !"font".equals(resourceKinds.get(resourceKey))) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_FONT_REFERENCE_INVALID",
                    "Browser text fonts must reference declared font resources");
        }
    }

    private void requireValidBrowserLayerMask(Map<String, Object> layer) {
        Object rawMask = layer.get("mask");
        if (rawMask == null) return;
        if (!(rawMask instanceof Map)) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_MASK_INVALID",
                    "Browser scene masks must be objects");
        }
        Map<?, ?> mask = (Map<?, ?>) rawMask;
        String type = blankToNull(mask.get("type") == null
                ? null : String.valueOf(mask.get("type")));
        Set<String> supportedTypes = new HashSet<String>(java.util.Arrays.asList(
                "ellipse", "rectangle", "heart", "linear", "mirror", "unsupported"));
        if (type == null || !supportedTypes.contains(type)) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_MASK_INVALID",
                    "Browser scene masks must declare a recognized shape");
        }
        for (String field : java.util.Arrays.asList(
                "centerX", "centerY", "width", "height", "rotation", "feather",
                "expansion", "roundCorner")) {
            Object value = mask.get(field);
            if (value != null && (!(value instanceof Number)
                    || !Double.isFinite(((Number) value).doubleValue()))) {
                throw badRequest("TEMPLATE_BROWSER_SCENE_MASK_INVALID",
                        "Browser scene mask geometry must contain finite numbers");
            }
        }
        if (mask.get("invert") != null && !(mask.get("invert") instanceof Boolean)) {
            throw badRequest("TEMPLATE_BROWSER_SCENE_MASK_INVALID",
                    "Browser scene mask inversion must be boolean");
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

    @SuppressWarnings("unchecked")
    private void requireValidBrowserCapabilityReport(
            Map<String, Object> scene, Map<String, Object> capability) {
        Object rawReport = scene.get("capabilityReport");
        if (!(rawReport instanceof Map)) {
            throw badRequest("TEMPLATE_BROWSER_CAPABILITY_REPORT_REQUIRED",
                    "Browser scene v5 requires a capability report");
        }
        Map<String, Object> report = (Map<String, Object>) rawReport;
        if (!"browser-scene-capability-report-v1".equals(report.get("schemaVersion"))
                || !"browser_effect_registry_v1".equals(report.get("effectRegistryContract"))
                || !java.util.Objects.equals(
                        capability.get("executionCapabilities"), report.get("features"))) {
            throw badRequest("TEMPLATE_BROWSER_CAPABILITY_REPORT_INVALID",
                    "Browser capability report does not match its scene contract");
        }
        Object rawImplementations = report.get("effectImplementations");
        if (!(rawImplementations instanceof List)) {
            throw badRequest("TEMPLATE_BROWSER_EFFECT_INVENTORY_REQUIRED",
                    "Browser capability report requires an effect implementation inventory");
        }
        requireValidBrowserCapabilitySummary(
                report.get("summary"), report.get("features"),
                ((List<?>) rawImplementations).size());
        Map<String, Integer> actual = browserSceneEffectInventory(scene);
        Map<String, String> renderers = new LinkedHashMap<String, String>();
        renderers.put("animation", "timeline_animation_v1");
        renderers.put("transition", "timeline_transition_v1");
        renderers.put("layer_effect", "canvas_layer_effect_v1");
        renderers.put("post_effect", "canvas_post_effect_v1");
        renderers.put("mask", "canvas_mask_v1");
        renderers.put("blend_mode", "canvas_composite_v1");
        renderers.put("text_template", "text_template_expansion_v1");
        Set<String> declaredKeys = new HashSet<String>();
        for (Object raw : (List<?>) rawImplementations) {
            if (!(raw instanceof Map)) {
                throw badRequest("TEMPLATE_BROWSER_EFFECT_INVENTORY_INVALID",
                        "Every browser effect implementation must be an object");
            }
            Map<String, Object> item = (Map<String, Object>) raw;
            String kind = blankToNull(item.get("kind") == null
                    ? null : String.valueOf(item.get("kind")));
            String implementationKey = blankToNull(item.get("implementationKey") == null
                    ? null : String.valueOf(item.get("implementationKey")));
            String fidelity = blankToNull(item.get("fidelity") == null
                    ? null : String.valueOf(item.get("fidelity")));
            String renderer = blankToNull(item.get("renderer") == null
                    ? null : String.valueOf(item.get("renderer")));
            int usageCount = item.get("usageCount") instanceof Number
                    ? ((Number) item.get("usageCount")).intValue() : -1;
            String key = effectInventoryKey(kind, implementationKey, fidelity);
            boolean expectedBlock = "unsupported".equals(fidelity);
            if (kind == null || implementationKey == null
                    || !("exact".equals(fidelity)
                            || "semantic_approximation".equals(fidelity)
                            || "unsupported".equals(fidelity))
                    || !renderers.containsKey(kind) || !renderers.get(kind).equals(renderer)
                    || usageCount <= 0 || !declaredKeys.add(key)
                    || !Boolean.valueOf(expectedBlock).equals(item.get("blocksExport"))
                    || !Integer.valueOf(usageCount).equals(actual.remove(key))) {
                throw badRequest("TEMPLATE_BROWSER_EFFECT_INVENTORY_INVALID",
                        "Browser effect inventory does not match the published scene graph");
            }
        }
        if (!actual.isEmpty()) {
            throw badRequest("TEMPLATE_BROWSER_EFFECT_INVENTORY_INCOMPLETE",
                    "Browser effect inventory omits implementations used by the scene graph");
        }
    }

    private void requireValidBrowserCapabilitySummary(
            Object rawSummary, Object rawFeatures, int implementationCount) {
        if (!(rawSummary instanceof Map) || !(rawFeatures instanceof List)) {
            throw badRequest("TEMPLATE_BROWSER_CAPABILITY_SUMMARY_INVALID",
                    "Browser capability report requires a derived summary");
        }
        int declaredItems = 0;
        int executableItems = 0;
        int exactFeatures = 0;
        int approximateFeatures = 0;
        int unsupportedFeatures = 0;
        for (Object raw : (List<?>) rawFeatures) {
            if (!(raw instanceof Map)) continue;
            Map<?, ?> feature = (Map<?, ?>) raw;
            declaredItems += feature.get("declaredCount") instanceof Number
                    ? ((Number) feature.get("declaredCount")).intValue() : 0;
            executableItems += feature.get("executableCount") instanceof Number
                    ? ((Number) feature.get("executableCount")).intValue() : 0;
            String fidelity = String.valueOf(feature.get("fidelity"));
            if ("exact".equals(fidelity)) exactFeatures++;
            else if ("semantic_approximation".equals(fidelity)) approximateFeatures++;
            else if ("unsupported".equals(fidelity)) unsupportedFeatures++;
        }
        Map<?, ?> summary = (Map<?, ?>) rawSummary;
        if (!Integer.valueOf(declaredItems).equals(summary.get("declaredItemCount"))
                || !Integer.valueOf(executableItems).equals(summary.get("executableItemCount"))
                || !Integer.valueOf(exactFeatures).equals(summary.get("exactFeatureCount"))
                || !Integer.valueOf(approximateFeatures).equals(
                        summary.get("approximateFeatureCount"))
                || !Integer.valueOf(unsupportedFeatures).equals(
                        summary.get("unsupportedFeatureCount"))
                || !Integer.valueOf(implementationCount).equals(
                        summary.get("effectImplementationCount"))) {
            throw badRequest("TEMPLATE_BROWSER_CAPABILITY_SUMMARY_INVALID",
                    "Browser capability summary does not match its feature inventory");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> browserSceneEffectInventory(Map<String, Object> scene) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        Object rawLayers = scene.get("layers");
        if (rawLayers instanceof List) {
            for (Object rawLayer : (List<?>) rawLayers) {
                if (!(rawLayer instanceof Map)) continue;
                Map<String, Object> layer = (Map<String, Object>) rawLayer;
                addTimedEffects(result, "animation", layer.get("animations"));
                Object rawTransition = layer.get("transitionIn");
                if (rawTransition instanceof Map) {
                    addEffectInventoryItem(result, "transition", (Map<?, ?>) rawTransition);
                }
                addTimedEffects(result, "layer_effect", layer.get("effects"));
                Object rawMask = layer.get("mask");
                if (rawMask instanceof Map) {
                    Map<?, ?> mask = (Map<?, ?>) rawMask;
                    String type = String.valueOf(mask.get("type"));
                    double feather = mask.get("feather") instanceof Number
                            ? ((Number) mask.get("feather")).doubleValue() : 0.0d;
                    String fidelity = "unsupported".equals(type) ? "unsupported"
                            : ("heart".equals(type) || "linear".equals(type)
                                    || "mirror".equals(type) || Math.abs(feather) > 0.000001d)
                                    ? "semantic_approximation" : "exact";
                    incrementEffectInventory(result, "mask", type, fidelity);
                }
                if (layer.get("blendMode") != null) {
                    incrementEffectInventory(result, "blend_mode",
                            String.valueOf(layer.get("blendMode")), "exact");
                }
                if (layer.get("templateResourceId") != null) {
                    incrementEffectInventory(result, "text_template", "expanded_text_template",
                            layer.get("fidelity") == null ? "semantic_approximation"
                                    : String.valueOf(layer.get("fidelity")));
                }
            }
        }
        addTimedEffects(result, "post_effect", scene.get("postEffects"));
        return result;
    }

    private void addTimedEffects(
            Map<String, Integer> result, String kind, Object rawEffects) {
        if (!(rawEffects instanceof List)) return;
        for (Object raw : (List<?>) rawEffects) {
            if (raw instanceof Map) addEffectInventoryItem(result, kind, (Map<?, ?>) raw);
        }
    }

    private void addEffectInventoryItem(
            Map<String, Integer> result, String kind, Map<?, ?> item) {
        String implementationKey = item.get("preset") == null
                ? "unsupported" : String.valueOf(item.get("preset"));
        String fidelity = item.get("fidelity") == null
                ? ("unsupported".equals(implementationKey) ? "unsupported" : "exact")
                : String.valueOf(item.get("fidelity"));
        incrementEffectInventory(result, kind, implementationKey, fidelity);
    }

    private void incrementEffectInventory(
            Map<String, Integer> result, String kind, String implementationKey, String fidelity) {
        String key = effectInventoryKey(kind, implementationKey, fidelity);
        result.put(key, Integer.valueOf(result.containsKey(key) ? result.get(key) + 1 : 1));
    }

    private String effectInventoryKey(String kind, String implementationKey, String fidelity) {
        return String.valueOf(kind) + "\u0000" + String.valueOf(implementationKey)
                + "\u0000" + String.valueOf(fidelity);
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
        boolean browserParityReference = video
                && "browser_parity_reference".equals(expectedRole);
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
        if (video ? !("full_mv".equals(expectedRole) || browserResource
                || browserParityReference)
                : !("cover".equals(expectedRole) || slotDefault || browserResource)) {
            throw badRequest("TEMPLATE_MEDIA_ROLE_INVALID", video
                    ? "Stream upload only accepts a full MV, official parity reference, or declared browser video resource"
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
        Map<String, Object> runtimePackage = repository.runtimePackage(versionId);
        if (runtimePackage != null && !runtimePackage.isEmpty()
                && !"ready".equals(RowUtils.str(runtimePackage, "status"))) {
            throw conflict("TEMPLATE_RUNTIME_PACKAGE_NOT_READY",
                    "Template runtime package must pass integrity verification before publish");
        }
        String validationStatus = RowUtils.str(version, "validation_status");
        boolean browserReady = "browser_ready".equals(validationStatus);
        if (!browserReady) {
            throw conflict("TEMPLATE_VERSION_NOT_PUBLISHABLE",
                    "Template version must be browser-ready; native renderer validation cannot publish a product template");
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
        boolean mediaReady = browserSceneReady;
        if (!mediaReady) {
            Map<String, Object> details = new LinkedHashMap<String, Object>();
            details.put("coverStatus", cover == null ? "missing" : RowUtils.str(cover, "status"));
            details.put("fullMvStatus", fullMv == null ? "missing" : RowUtils.str(fullMv, "status"));
            details.put("missingTemplatePhotoSlots", missingDefaults);
            details.put("browserSceneStatus", browserScene == null
                    ? "missing" : RowUtils.str(browserScene, "status"));
            throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_MEDIA_NOT_READY",
                    "Browser scene must be ready before publish",
                    true, details);
        }
        if (browserSceneReady) {
            Map<String, Object> reference = repository.mediaByRole(
                    versionId, "browser_parity_reference");
            Map<String, Object> parity = repository.browserParity(versionId);
            String sceneManifest = RowUtils.str(browserScene, "manifest_sha256");
            String referenceSha256 = reference == null ? null
                    : RowUtils.str(reference, "source_sha256");
            boolean parityPassed = ready(reference) && parity != null
                    && "passed".equals(RowUtils.str(parity, "status"))
                    && sceneManifest != null
                    && sceneManifest.equals(RowUtils.str(parity, "scene_manifest_sha256"))
                    && referenceSha256 != null
                    && referenceSha256.equals(RowUtils.str(parity, "reference_sha256"));
            if (!parityPassed) {
                Map<String, Object> details = new LinkedHashMap<String, Object>();
                details.put("sceneManifestSha256", RowUtils.str(
                        browserScene, "manifest_sha256"));
                details.put("paritySceneManifestSha256", parity == null ? null
                        : RowUtils.str(parity, "scene_manifest_sha256"));
                details.put("referenceSha256", referenceSha256);
                details.put("parityReferenceSha256", parity == null ? null
                        : RowUtils.str(parity, "reference_sha256"));
                details.put("referenceStatus", reference == null ? "missing"
                        : RowUtils.str(reference, "status"));
                details.put("parityStatus", parity == null ? "missing"
                        : RowUtils.str(parity, "status"));
                throw new ApiException(HttpStatus.CONFLICT,
                        "TEMPLATE_BROWSER_PARITY_REQUIRED",
                        "Browser scene must pass the official-preview visual parity gate before publish",
                        true, details);
            }
        }
        repository.publish(templateId, versionId);
        invalidateDetail(templateId);
        return promotionView(templateId, versionId, "published");
    }

    public Map<String, Object> browserParity(String templateId, String versionId) {
        requireVersion(templateId, versionId);
        Map<String, Object> parity = repository.browserParity(versionId);
        return parity == null ? Collections.<String, Object>emptyMap()
                : browserParityView(parity);
    }

    public Map<String, Object> synchronizeBrowserParity(
            String templateId, String versionId, TemplateBrowserParityRequest request) {
        requireVersion(templateId, versionId);
        String sceneHash = normalizedSha256(request.getSceneManifestSha256(),
                "TEMPLATE_BROWSER_PARITY_SCENE_HASH_INVALID");
        String referenceHash = normalizedSha256(request.getReferenceSha256(),
                "TEMPLATE_BROWSER_PARITY_REFERENCE_HASH_INVALID");
        Map<String, Object> browserScene = repository.browserScene(versionId);
        Map<String, Object> reference = repository.mediaByRole(
                versionId, "browser_parity_reference");
        if (browserScene == null || !sceneHash.equals(
                RowUtils.str(browserScene, "manifest_sha256"))
                || !ready(reference) || !referenceHash.equals(
                RowUtils.str(reference, "source_sha256"))) {
            throw conflict("TEMPLATE_BROWSER_PARITY_INPUT_MISMATCH",
                    "Browser parity evidence must match the immutable scene and reference video");
        }
        String status = blankToNull(request.getStatus());
        if (!("pending".equals(status) || "passed".equals(status)
                || "failed".equals(status))) {
            throw badRequest("TEMPLATE_BROWSER_PARITY_STATUS_INVALID",
                    "Browser parity status must be pending, passed, or failed");
        }
        if (!"pending".equals(status)) requireBrowserParityMetrics(request, status);
        Map<String, Object> existing = repository.matchingBrowserParity(
                versionId, sceneHash, referenceHash);
        String validationId = existing == null
                ? IdUtils.token("bpar") : RowUtils.str(existing, "validation_id");
        repository.upsertBrowserParity(validationId, templateId, versionId,
                sceneHash, referenceHash, status,
                request.getSampleCount(), request.getSsimThreshold(), request.getMaeThreshold(),
                request.getAverageSsim(), request.getMinSsim(), request.getAverageMae(),
                request.getMaxMae(), request.getReferenceDurationSeconds(),
                request.getOutputDurationSeconds(), request.getOutputSha256() == null ? null
                        : normalizedSha256(request.getOutputSha256(),
                        "TEMPLATE_BROWSER_PARITY_OUTPUT_HASH_INVALID"),
                json(request.getReport()));
        invalidateDetail(templateId);
        return browserParityView(repository.matchingBrowserParity(
                versionId, sceneHash, referenceHash));
    }

    private void requireBrowserParityMetrics(TemplateBrowserParityRequest request,
                                             String status) {
        if (request.getSampleCount() == null || request.getSampleCount().intValue() < 3
                || request.getSsimThreshold() == null || request.getMaeThreshold() == null
                || request.getAverageSsim() == null || request.getMinSsim() == null
                || request.getMaxMae() == null
                || request.getReferenceDurationSeconds() == null
                || request.getOutputDurationSeconds() == null
                || request.getOutputSha256() == null) {
            throw badRequest("TEMPLATE_BROWSER_PARITY_METRICS_REQUIRED",
                    "Completed browser parity requires sampled metrics and output evidence");
        }
        if (request.getSsimThreshold().doubleValue() < BROWSER_SSIM_MINIMUM) {
            throw badRequest("TEMPLATE_BROWSER_PARITY_THRESHOLD_INVALID",
                    "Browser parity must require average SSIM >= 0.900");
        }
        boolean metricsPassed = request.getAverageSsim().doubleValue()
                >= request.getSsimThreshold().doubleValue();
        if ("passed".equals(status) != metricsPassed) {
            throw badRequest("TEMPLATE_BROWSER_PARITY_RESULT_INVALID",
                    "Browser parity status does not match its metrics");
        }
    }

    private String normalizedSha256(String value, String code) {
        String normalized = blankToNull(value);
        if (normalized == null || !normalized.matches("(?i)[a-f0-9]{64}")) {
            throw badRequest(code, "SHA-256 evidence is invalid");
        }
        return normalized.toLowerCase();
    }

    private Map<String, Object> browserParityView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (row == null) return result;
        String[][] fields = new String[][] {
                {"validationId","validation_id"},{"templateId","template_id"},
                {"versionId","version_id"},{"sceneManifestSha256","scene_manifest_sha256"},
                {"referenceSha256","reference_sha256"},
                {"status","status"},{"sampleCount","sample_count"},
                {"ssimThreshold","ssim_threshold"},{"maeThreshold","mae_threshold"},
                {"averageSsim","average_ssim"},{"minSsim","min_ssim"},
                {"averageMae","average_mae"},{"maxMae","max_mae"},
                {"referenceDurationSeconds","reference_duration_seconds"},
                {"outputDurationSeconds","output_duration_seconds"},
                {"outputSha256","output_sha256"},{"createdAt","created_at"},
                {"updatedAt","updated_at"},{"completedAt","completed_at"}
        };
        for (String[] field : fields) result.put(field[0], row.get(field[1]));
        result.put("report", parseObject(RowUtils.str(row, "report_json")));
        return result;
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
                {"fathers-day", "father's day", "fathers day", "fathersday", "父亲节"},
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
