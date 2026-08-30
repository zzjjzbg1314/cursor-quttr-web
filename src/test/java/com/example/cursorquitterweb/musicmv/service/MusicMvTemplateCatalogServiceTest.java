package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateMediaUploadSessionRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateBrowserSceneRequest;
import com.example.cursorquitterweb.musicmv.dto.TemplateSlotReconcileRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository.TemplateDetailRows;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class MusicMvTemplateCatalogServiceTest {
    private MusicMvTemplateCatalogRepository repository;
    private CloudflareTemplateMediaProvider mediaProvider;
    private MusicMvTemplateCatalogService service;

    @BeforeEach
    void setUp() {
        repository = mock(MusicMvTemplateCatalogRepository.class);
        mediaProvider = mock(CloudflareTemplateMediaProvider.class);
        service = new MusicMvTemplateCatalogService(repository,
                mediaProvider, mock(D1DatabaseClient.class),
                new ObjectMapper());
        when(repository.versionByValidationJob(anyString())).thenReturn(null);
        Map<String, Object> category = new LinkedHashMap<String, Object>();
        category.put("enabled", Integer.valueOf(1));
        category.put("level", Integer.valueOf(2));
        category.put("is_selectable", Integer.valueOf(1));
        for (String key : Arrays.asList("birthday", "wedding", "anniversary", "graduation",
                "holidays-parties", "family", "baby-kids", "couples", "friendship",
                "daily-life", "travel", "school-life", "growing-up", "recap",
                "hobbies-interests", "motivation", "healing", "love-thanks",
                "farewell-breakup", "memorial")) {
            when(repository.category(key)).thenReturn(category);
        }
    }

    @Test
    void classifiesMothersDayFamilyTemplateIntoFamilyHolidayAndThanks() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("family");
        request.setSourceCategory("Family");
        request.setSourceTitle("Happy Mother's Day");
        request.setSourceHashtags(Arrays.asList("mothersday", "bestmom"));
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("family");
        assertEquals(new HashSet<String>(Arrays.asList(
                "family", "holidays-parties", "love-thanks")), keys);
    }

    @Test
    void classifiesBabyMilestoneFamilyTemplateIntoBabyGrowthAndRecap() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("family");
        request.setSourceCategory("Family");
        request.setSourceTitle("Baby first year photo dump");
        request.setSourceHashtags(Arrays.asList("baby", "firstyear", "photodump"));
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("family");
        assertTrue(keys.containsAll(Arrays.asList(
                "family", "baby-kids", "growing-up", "recap")));
        assertEquals(Integer.valueOf(4), Integer.valueOf(keys.size()));
    }

    @Test
    void usesOriginalCapCutClassificationAsPrimaryAndBatchCategoryOnlyAsFallback() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("birthday");
        request.setSourceTitle("Family Moments");
        request.setSourceCategory("Family");
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("family");
        assertEquals(Collections.singleton("family"), keys);
    }

    @Test
    void keepsManuallyLockedPrimaryEvenWhenCapCutMetadataMatchesAnotherCategory() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("birthday");
        request.setSourceTitle("Family Moments");
        request.setSourceCategory("Family");
        request.setClassificationLocked(Boolean.TRUE);
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("birthday");
        assertEquals(Collections.singleton("birthday"), keys);
    }

    @Test
    void doesNotTreatMomentsAsTheShortFamilyKeywordMom() {
        TemplatePromotionRequest request = validPromotion();
        request.setCategoryKey("recap");
        request.setSourceTitle("Summer moments");
        request.setSourceHashtags(Collections.singletonList("moments"));
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(1));

        service.promote(request);

        Set<String> keys = capturedCategoryKeys("recap");
        assertFalse(keys.contains("family"));
    }

    @Test
    void promotesExactNativeEvidenceIntoNewImmutableVersion() {
        when(repository.versionByValidationJob("native_1")).thenReturn(null);
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(3));
        Map<String, Object> result = service.promote(validPromotion());

        assertEquals("tpl_1", result.get("templateId"));
        assertEquals("validated", result.get("status"));
        assertFalse((Boolean) result.get("idempotentReplay"));
        verify(repository).promote(any(TemplatePromotionRequest.class), anyString(),
                anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsDifferentTemplateForExistingCapCutIdentity() {
        Map<String, Object> existing = new LinkedHashMap<String, Object>();
        existing.put("template_id", "tpl_existing");
        existing.put("capcut_template_id", "7362454015088561426");
        when(repository.templatesByCapCutTemplateIds(
                Collections.singletonList("7362454015088561426")))
                .thenReturn(Collections.singletonList(existing));

        ApiException error = assertThrows(ApiException.class,
                () -> service.promote(validPromotion()));

        assertEquals("CAPCUT_TEMPLATE_ALREADY_EXISTS", error.getCode());
        verify(repository, never()).promote(any(), anyString(), anyInt(), anyString(),
                anyString(), anyString());
    }

    @Test
    void enrichesAnIdempotentNativeVersionWithDerivedQualityEvidence() {
        TemplatePromotionRequest request = validPromotion();
        Map<String, Object> existing = row("version_id", "tplver_existing");
        existing.put("template_id", "tpl_1");
        existing.put("draft_snapshot_sha256", hash('b'));
        existing.put("validation_master_sha256", hash('a'));
        existing.put("status", "published");
        when(repository.versionByValidationJob("native_1")).thenReturn(existing);

        Map<String, Object> result = service.promote(request);

        assertEquals(Boolean.TRUE, result.get("idempotentReplay"));
        verify(repository).enrichVisualQuality(eq("tplver_existing"), eq(Double.valueOf(13)),
                anyString());
    }

    @Test
    void rejectsValidationThatUsedMoreThanOneVideoEncode() {
        TemplatePromotionRequest request = validPromotion();
        request.setVideoEncodeCount(Integer.valueOf(2));

        ApiException error = assertThrows(ApiException.class, () -> service.promote(request));
        assertEquals("TEMPLATE_VALIDATION_NOT_EXACT", error.getCode());
        verify(repository, never()).promote(any(), anyString(), anyInt(), anyString(),
                anyString(), anyString());
    }

    @Test
    void rejectsNativePromotionWithoutBoundVisualQualityEvidence() {
        TemplatePromotionRequest request = validPromotion();
        request.setVisualQuality(null);

        ApiException error = assertThrows(ApiException.class, () -> service.promote(request));

        assertEquals("TEMPLATE_VISUAL_QUALITY_REQUIRED", error.getCode());
        verify(repository, never()).promote(any(), anyString(), anyInt(), anyString(),
                anyString(), anyString());
    }

    @Test
    void promotesLatestSavedDraftWithoutNativeValidationRender() {
        TemplatePromotionRequest request = validPromotion();
        request.setPromotionMode("latest_saved_draft");
        request.setValidationRenderJobId("draft_1");
        request.setSemanticIntegrity("browser_ready");
        request.setVideoEncodeCount(Integer.valueOf(0));
        request.setRendererVersion("browser-canvas-v1");
        when(repository.versionByValidationJob("draft_1")).thenReturn(null);
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(4));

        Map<String, Object> result = service.promote(request);

        assertEquals("validated", result.get("status"));
        verify(repository).promote(eq(request), anyString(), eq(Integer.valueOf(4)),
                anyString(), anyString(), anyString());
    }

    @Test
    void cachesPublicTemplateDetailAfterSingleBatchLoad() {
        Map<String, Object> template = row("template_id", "tpl_1");
        template.put("status", "published");
        template.put("visibility", "public");
        template.put("current_version_id", "tplver_1");
        template.put("revision", Integer.valueOf(3));
        Map<String, Object> version = row("version_id", "tplver_1");
        TemplateDetailRows rows = new TemplateDetailRows(template,
                Collections.<Map<String, Object>>emptyList(), null,
                Collections.<Map<String, Object>>emptyList(),
                Collections.singletonList(version),
                Collections.<Map<String, Object>>emptyList(),
                Collections.<Map<String, Object>>emptyList(),
                Collections.<Map<String, Object>>emptyList());
        when(repository.templateDetail("tpl_1")).thenReturn(rows);

        Map<String, Object> first = service.detail("tpl_1", false);
        Map<String, Object> second = service.detail("tpl_1", false);

        assertEquals("tplver_1", first.get("currentVersionId"));
        assertTrue(first == second);
        verify(repository).templateDetail("tpl_1");
    }

    @Test
    void publishesBrowserReadyVersionWithSceneOnly() {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        Map<String, Object> version = row("validation_status", "browser_ready");
        version.put("source_availability", "unavailable");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        when(repository.browserScene("tplver_1")).thenReturn(row("status", "ready"));

        Map<String, Object> result = service.publish("tpl_1", "tplver_1");

        assertEquals("published", result.get("status"));
        verify(repository).publish("tpl_1", "tplver_1");
    }

    @Test
    void rejectsVersionTwoBrowserSceneWhenPhotoAnimationIsNotExecutable() throws Exception {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("photoReplacementReady", Boolean.TRUE);
        capability.put("photoAnimationReady", Boolean.FALSE);
        capability.put("photoAnimationContract", "timeline_keyframes_v1");
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("schemaVersion", "browser-template-scene-v2");
        scene.put("templateId", "tpl_1");
        scene.put("versionId", "tplver_1");
        scene.put("capability", capability);
        TemplateBrowserSceneRequest request = new TemplateBrowserSceneRequest();
        request.setSchemaVersion("browser-template-scene-v2");
        request.setScene(scene);
        request.setManifestSha256(sha256(new ObjectMapper().writeValueAsString(scene)));

        ApiException error = assertThrows(ApiException.class,
                () -> service.synchronizeBrowserScene("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_BROWSER_SCENE_ANIMATION_NOT_READY", error.getCode());
        verify(repository, never()).upsertBrowserScene(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void migratesCurrentVersionsInPlaceAndReportsMediaPreserved() {
        when(repository.migrateCurrentTemplatesToBrowserRendering()).thenReturn(3);

        Map<String, Object> result = service.migrateCurrentTemplatesToBrowserRendering();

        assertEquals(Integer.valueOf(3), result.get("updatedVersionCount"));
        assertEquals("browser_ready", result.get("validationStatus"));
        assertEquals("browser-canvas-v1", result.get("rendererVersion"));
        assertEquals(Boolean.TRUE, result.get("mediaPreserved"));
    }

    @Test
    void refusesPublishUntilBothImagesAndStreamMediaAreReady() {
        Map<String, Object> template = row("template_id", "tpl_1");
        Map<String, Object> version = row("validation_status", "exact");
        version.put("source_availability", "available");
        version.put("base_duration_seconds", Double.valueOf(13));
        version.put("cycle_duration_seconds", Double.valueOf(13));
        version.put("validation_master_sha256", hash('a'));
        version.put("source_provenance_json", qualityProvenanceJson(13.0d, 13.0d));
        when(repository.template("tpl_1")).thenReturn(template);
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        when(repository.mediaByRole("tplver_1", "cover")).thenReturn(row("status", "ready"));
        when(repository.mediaByRole("tplver_1", "full_mv")).thenReturn(row("status", "processing"));

        ApiException error = assertThrows(ApiException.class,
                () -> service.publish("tpl_1", "tplver_1"));
        assertEquals("TEMPLATE_MEDIA_NOT_READY", error.getCode());
        verify(repository, never()).publish(anyString(), anyString());
    }

    @Test
    void replacesReadyDatabaseRecordWhenCloudflareAssetWasDeleted() {
        Map<String, Object> version = row("version_id", "tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        Map<String, Object> existing = row("media_id", "media_1");
        existing.put("source_sha256", hash('a'));
        existing.put("status", "ready");
        existing.put("provider", "cloudflare_images");
        existing.put("provider_asset_id", "deleted-image");
        existing.put("provider_details_json", "{\"ready\":true}");
        when(repository.mediaByRole("tplver_1", "cover")).thenReturn(existing);
        when(mediaProvider.isReusableReadyAsset("cloudflare_images", "deleted-image"))
                .thenReturn(false);
        when(mediaProvider.createImageUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_images", "replacement-image", "https://upload.example",
                        "awaiting_upload", new LinkedHashMap<String, Object>()));

        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("cover");
        request.setSourceSha256(hash('a'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", false, request);

        assertEquals("media_1", result.get("mediaId"));
        assertEquals("awaiting_upload", result.get("status"));
        assertFalse((Boolean) result.get("idempotentReplay"));
        verify(repository).upsertMedia(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(Long.class),
                any(), any(), any(), anyString());
    }

    @Test
    void forceReplaceCreatesFreshAssetEvenWhenExistingAssetIsReusable() {
        Map<String, Object> version = row("version_id", "tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        Map<String, Object> existing = row("media_id", "media_1");
        existing.put("source_sha256", hash('a'));
        existing.put("status", "ready");
        existing.put("provider", "cloudflare_stream");
        existing.put("provider_asset_id", "old-stream");
        when(repository.mediaByRole("tplver_1", "full_mv")).thenReturn(existing);
        when(mediaProvider.isReusableReadyAsset("cloudflare_stream", "old-stream"))
                .thenReturn(true);
        when(mediaProvider.createStreamUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_stream", "new-stream", "https://upload.example",
                        "awaiting_upload", new LinkedHashMap<String, Object>()));

        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("full_mv");
        request.setSourceSha256(hash('a'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));
        request.setDurationSeconds(Double.valueOf(180));
        request.setFilename("showcase.mp4");
        request.setForceReplace(Boolean.TRUE);

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", true, request);

        assertEquals("media_1", result.get("mediaId"));
        assertEquals("awaiting_upload", result.get("status"));
        assertFalse((Boolean) result.get("idempotentReplay"));
        verify(mediaProvider).createStreamUpload(anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsCapCutOfficialPreviewProvenanceOnFullMv() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        when(repository.mediaByRole("tplver_1", "full_mv")).thenReturn(null);
        when(mediaProvider.createStreamUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_stream", "preview-stream", "https://upload.example",
                        "awaiting_upload", row("playbackUrl", "https://stream.example/manifest/video.m3u8")));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("full_mv");
        request.setSourceSha256(hash('c'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setDurationSeconds(Double.valueOf(26.633));
        request.setFilename("official-preview.mp4");
        request.setSourceType("capcut_official_template_preview");
        request.setDisplayLabel("CapCut 原始模板预览");
        request.setOfficialTemplateId("7583099812292218119");
        request.setOfficialPageUrl("https://www.capcut.com/template-detail/7583099812292218119");

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", true, request);
        Map<String, Object> details = (Map<String, Object>) result.get("providerDetails");

        assertEquals("capcut_official_template_preview", details.get("sourceType"));
        assertEquals("CapCut 原始模板预览", details.get("displayLabel"));
        assertEquals("7583099812292218119", details.get("officialTemplateId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsValidatedAiMusicMvNativeOutputAsFullMv() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        when(repository.mediaByRole("tplver_1", "full_mv")).thenReturn(null);
        when(mediaProvider.createStreamUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_stream", "validated-stream", "https://upload.example",
                        "awaiting_upload", row("playbackUrl", "https://stream.example/manifest/video.m3u8")));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("full_mv");
        request.setSourceSha256(hash('d'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setDurationSeconds(Double.valueOf(180));
        request.setFilename("validated-native-output.mp4");
        request.setSourceType("validated_ai_music_mv_native_output");
        request.setDisplayLabel("已验收原生 MV");

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", true, request);
        Map<String, Object> details = (Map<String, Object>) result.get("providerDetails");

        assertEquals("validated_ai_music_mv_native_output", details.get("sourceType"));
        assertEquals("已验收原生 MV", details.get("displayLabel"));
    }

    @Test
    void createsCloudflareImageForADeclaredTemplatePhotoSlot() {
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(row("version_id", "tplver_1"));
        Map<String, Object> slot = row("slot_key", "photo_01");
        slot.put("slot_type", "image");
        when(repository.slots("tplver_1")).thenReturn(Collections.singletonList(slot));
        when(repository.mediaByRole("tplver_1", "slot_default:photo_01")).thenReturn(null);
        when(mediaProvider.createImageUpload(anyString(), any()))
                .thenReturn(new CloudflareTemplateMediaProvider.UploadSession(
                        "cloudflare_images", "template-photo", "https://upload.example",
                        "awaiting_upload", new LinkedHashMap<String, Object>()));
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("slot_default:photo_01");
        request.setSourceSha256(hash('b'));
        request.setSourceSizeBytes(Long.valueOf(100));
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));

        Map<String, Object> result = service.createMediaSession(
                "tpl_1", "tplver_1", false, request);

        assertEquals("awaiting_upload", result.get("status"));
        verify(repository).upsertMedia(anyString(), eq("tpl_1"), eq("tplver_1"),
                eq("slot_default:photo_01"), anyString(), anyString(), anyString(),
                anyString(), any(Long.class), any(), any(), any(), anyString());
    }

    @Test
    void reconcilesDerivedSlotsForTheSameImmutableSource() {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        Map<String, Object> version = row("source_node_id", "mac-1");
        version.put("source_local_key", "templates/tpl_1/tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);

        TemplateSlotReconcileRequest request = reconcileRequest();
        Map<String, Object> result = service.reconcileSlots("tpl_1", "tplver_1", request);

        assertEquals("reconciled", result.get("status"));
        assertEquals(Integer.valueOf(1), result.get("slotCount"));
        verify(repository).replaceSlots("tpl_1", "tplver_1", request.getSlots());
    }

    @Test
    void rejectsSlotReconciliationFromAnotherSourceSnapshot() {
        when(repository.template("tpl_1")).thenReturn(row("template_id", "tpl_1"));
        Map<String, Object> version = row("source_node_id", "mac-1");
        version.put("source_local_key", "templates/tpl_1/tplver_1");
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);

        TemplateSlotReconcileRequest request = reconcileRequest();
        request.setSourceLocalKey("templates/tpl_1/other");
        ApiException error = assertThrows(ApiException.class,
                () -> service.reconcileSlots("tpl_1", "tplver_1", request));

        assertEquals("TEMPLATE_SLOT_SOURCE_MISMATCH", error.getCode());
        verify(repository, never()).replaceSlots(anyString(), anyString(), any());
    }

    @Test
    void permanentlyDeletesOfflineTemplateMediaAndCatalogGraph() {
        Map<String, Object> template = row("status", "offline");
        when(repository.template("tpl_1")).thenReturn(template);
        when(repository.projectReferenceCount("tpl_1")).thenReturn(Long.valueOf(0L));
        when(repository.renderJobReferenceCount("tpl_1")).thenReturn(Long.valueOf(0L));
        Map<String, Object> cover = row("provider", "cloudflare_images");
        cover.put("provider_asset_id", "image-1");
        Map<String, Object> fullMv = row("provider", "cloudflare_stream");
        fullMv.put("provider_asset_id", "stream-1");
        when(repository.mediaForTemplate("tpl_1"))
                .thenReturn(java.util.Arrays.asList(cover, fullMv));

        Map<String, Object> result = service.action("tpl_1", "delete-template", null);

        assertEquals(Boolean.TRUE, result.get("deleted"));
        assertEquals(Integer.valueOf(2), result.get("deletedMediaCount"));
        verify(mediaProvider).deleteAsset("cloudflare_images", "image-1");
        verify(mediaProvider).deleteAsset("cloudflare_stream", "stream-1");
        verify(repository).deleteTemplate("tpl_1");
    }

    @Test
    void refusesPermanentDeletionUntilOfflineAndUnreferenced() {
        when(repository.template("tpl_1")).thenReturn(row("status", "published"));
        ApiException published = assertThrows(ApiException.class,
                () -> service.action("tpl_1", "delete-template", null));
        assertEquals("TEMPLATE_DELETE_REQUIRES_OFFLINE", published.getCode());

        when(repository.template("tpl_1")).thenReturn(row("status", "offline"));
        when(repository.projectReferenceCount("tpl_1")).thenReturn(Long.valueOf(1L));
        ApiException referenced = assertThrows(ApiException.class,
                () -> service.action("tpl_1", "delete-template", null));
        assertEquals("TEMPLATE_DELETE_REFERENCED", referenced.getCode());
        verify(mediaProvider, never()).deleteAsset(anyString(), anyString());
        verify(repository, never()).deleteTemplate(anyString());
    }

    @Test
    void forceDeletionSkipsStatusAndReferenceGuards() {
        when(repository.template("tpl_1")).thenReturn(row("status", "published"));
        when(repository.projectReferenceCount("tpl_1")).thenReturn(Long.valueOf(3L));
        when(repository.renderJobReferenceCount("tpl_1")).thenReturn(Long.valueOf(2L));
        Map<String, Object> preview = row("provider", "cloudflare_stream");
        preview.put("provider_asset_id", "stream-1");
        when(repository.mediaForTemplate("tpl_1"))
                .thenReturn(java.util.Collections.singletonList(preview));

        Map<String, Object> result = service.action("tpl_1", "force-delete-template", null);

        assertEquals(Boolean.TRUE, result.get("deleted"));
        assertEquals(Boolean.TRUE, result.get("forced"));
        assertEquals(Long.valueOf(3L), result.get("detachedProjectCount"));
        assertEquals(Long.valueOf(2L), result.get("deletedRenderJobCount"));
        verify(mediaProvider).deleteAsset("cloudflare_stream", "stream-1");
        verify(repository).forceDeleteTemplate("tpl_1");
        verify(repository, never()).deleteTemplate("tpl_1");
    }

    private TemplateSlotReconcileRequest reconcileRequest() {
        TemplateSlotReconcileRequest request = new TemplateSlotReconcileRequest();
        request.setSourceNodeId("mac-1");
        request.setSourceLocalKey("templates/tpl_1/tplver_1");
        TemplatePromotionRequest.Slot slot = new TemplatePromotionRequest.Slot();
        slot.setSlotKey("photo_1");
        slot.setSlotType("image");
        slot.setDisplayName("Photo 1");
        slot.setTimelineOrder(Integer.valueOf(0));
        slot.setCropPolicy("fill");
        slot.setRepeatPolicy("cycle");
        request.getSlots().add(slot);
        return request;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Set<String> capturedCategoryKeys(String primary) {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceTemplateCategories(eq("tpl_1"), eq(primary), captor.capture());
        Set<String> result = new HashSet<String>();
        for (Object raw : captor.getValue()) {
            result.add(String.valueOf(((Map<String, Object>) raw).get("categoryKey")));
        }
        return result;
    }

    private TemplatePromotionRequest validPromotion() {
        TemplatePromotionRequest request = new TemplatePromotionRequest();
        request.setTemplateId("tpl_1");
        request.setCapcutTemplateId("7362454015088561426");
        request.setSlug("birthday-one");
        request.setCategoryKey("birthday");
        request.setNameZh("生日");
        request.setNameEn("Birthday");
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));
        request.setFps(Double.valueOf(30));
        request.setDurationSeconds(Double.valueOf(180));
        request.setBaseDurationSeconds(Double.valueOf(13));
        request.setCycleDurationSeconds(Double.valueOf(13));
        request.setValidationRenderJobId("native_1");
        request.setValidationMasterSha256(hash('a'));
        request.setDraftSnapshotSha256(hash('b'));
        request.setTimelineEvidenceSha256(hash('c'));
        request.setNativeRuntimeVersion("9.2.0");
        request.setNativeRuntimeSha256(hash('d'));
        request.setRendererVersion("renderer-1");
        request.setSourceNodeId("mac-1");
        request.setSourceLocalKey("templates/tpl_1/tplver_1");
        request.setSemanticIntegrity("exact");
        request.setVideoEncodeCount(Integer.valueOf(1));
        request.setIntermediateVideoCount(Integer.valueOf(0));
        request.setExternalResourceReadCount(Integer.valueOf(0));
        request.setMissingResourceCount(Integer.valueOf(0));
        request.setValidationElapsedSeconds(Double.valueOf(12));
        request.setVisualQuality(visualQuality(13.0d, 13.0d));
        TemplatePromotionRequest.Slot slot = new TemplatePromotionRequest.Slot();
        slot.setSlotKey("photo_1");
        slot.setSlotType("image");
        slot.setDisplayName("照片 1");
        slot.setTimelineOrder(Integer.valueOf(0));
        slot.setCropPolicy("fill");
        slot.setRepeatPolicy("cycle");
        request.getSlots().add(slot);
        return request;
    }

    private Map<String, Object> visualQuality(double base, double cycle) {
        Map<String, Object> quality = new LinkedHashMap<String, Object>();
        quality.put("schemaVersion", "template-visual-quality-v1");
        quality.put("status", cycle < base ? "adjusted" : "passed");
        quality.put("sourceSha256", hash('a'));
        quality.put("baseDurationSeconds", Double.valueOf(base));
        quality.put("effectiveCycleDurationSeconds", Double.valueOf(cycle));
        return quality;
    }

    private String qualityProvenanceJson(double base, double cycle) {
        try {
            Map<String, Object> provenance = new LinkedHashMap<String, Object>();
            provenance.put("visualQuality", visualQuality(base, cycle));
            return new ObjectMapper().writeValueAsString(provenance);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Map<String, Object> row(String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<String, Object>(); row.put(key, value); return row;
    }

    private String hash(char value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 64; i++) result.append(value);
        return result.toString();
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
}
