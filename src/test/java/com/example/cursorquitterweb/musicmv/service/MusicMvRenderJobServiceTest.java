package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.dto.BrowserRenderOutputRequest;
import com.example.cursorquitterweb.musicmv.dto.BrowserRenderAttemptStartRequest;
import com.example.cursorquitterweb.musicmv.dto.BrowserRenderFailureRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository.RenderContract;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicMvRenderJobServiceTest {
    @Test
    void emptyBindingsRequireAuthoritativePublishedTextOnlyScene() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (String variant : Arrays.asList("valid", "missing", "photo", "unowned", "missingSource", "extraBinding", "notReady")) {
            MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
            AiMusicJobRepository music = mock(AiMusicJobRepository.class);
            MusicMvRenderJobService service = new MusicMvRenderJobService(repository, music,
                    mock(MusicMvRenderArtifactStorageService.class), inputAssets(), mapper, true, 2);
            MusicMvRenderJobCreateRequest request = request();
            if (!"extraBinding".equals(variant)) request.setSlotBindings(Collections.emptyList());
            when(music.ownedCandidate("owner", "song_1")).thenReturn(candidate());
            when(repository.claimBrowserPreparation("text-job")).thenReturn(preparingRow("text-job", request));
            when(repository.updateBrowserPreparation("text-job", "preparing_template", 0.55d))
                    .thenReturn(preparingRow("text-job", request));
            when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(new RenderContract(version(), Collections.emptyList()));
            Map<String,Object> scene=mapper.readValue("{\"schemaVersion\":\"browser-template-scene-v6\",\"slots\":[],\"timelineSegments\":[],\"layers\":[{\"layerId\":\"text\",\"type\":\"text\"}],\"textLayers\":[{\"segmentId\":\"text\",\"nativeTextSource\":{\"schemaVersion\":\"native-text-source-v1\",\"status\":\"material_projected\"}}]}",Map.class);
            if ("photo".equals(variant)) ((Map)((List)scene.get("layers")).get(0)).put("type","photo");
            if ("unowned".equals(variant)) scene.put("slots",Collections.singletonList(Collections.singletonMap("slotKey","photo")));
            if ("missingSource".equals(variant)) ((Map)((List)scene.get("textLayers")).get(0)).remove("nativeTextSource");
            Map<String,Object> stored=new LinkedHashMap<>();stored.put("status","notReady".equals(variant)?"pending":"ready");stored.put("scene_json",mapper.writeValueAsString(scene));
            if (!"missing".equals(variant)) when(repository.browserScene("tplver_1")).thenReturn(stored);
            service.prepareBrowserAsync("owner","text-job");
            if ("valid".equals(variant)) {
                verify(repository).completeBrowserPreparation(eq("text-job"),anyString());
                verify(repository,never()).slotDefaultMedia(anyString(),anySet());
            } else {
                verify(repository,never()).completeBrowserPreparation(anyString(),anyString());
                verify(repository).failBrowserPreparation(eq("text-job"),eq("MV_RENDER_TEMPLATE_HAS_NO_SLOTS"),anyString(),eq(false));
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyBindingsRequireVerifiedNativeFixedImageOwnership() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (String variant : Arrays.asList("valid", "mixedText", "videoV4", "videoV3", "videoPolicyMissing", "legacy", "missingDelivery", "missingResource",
                "missingClip", "photo", "duplicateLayer", "missingDependency", "extraBinding", "notReady")) {
            MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
            AiMusicJobRepository music = mock(AiMusicJobRepository.class);
            MusicMvRenderJobService service = new MusicMvRenderJobService(repository, music,
                    mock(MusicMvRenderArtifactStorageService.class), inputAssets(), mapper, true, 2);
            MusicMvRenderJobCreateRequest request = request();
            if (!"extraBinding".equals(variant)) request.setSlotBindings(Collections.emptyList());
            when(music.ownedCandidate("owner", "song_1")).thenReturn(candidate());
            when(repository.claimBrowserPreparation("fixed-job")).thenReturn(preparingRow("fixed-job", request));
            when(repository.updateBrowserPreparation("fixed-job", "preparing_template", 0.55d))
                    .thenReturn(preparingRow("fixed-job", request));
            when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(new RenderContract(version(), Collections.emptyList()));
            Map<String,Object> scene = mapper.readValue("{\"schemaVersion\":\"browser-template-scene-v6\",\"slots\":[],\"timelineSegments\":[],\"layers\":[{\"layerId\":\"fixed\",\"segmentId\":\"fixed\",\"type\":\"static_image\",\"resourceKey\":\"image\",\"originalTimeline\":{},\"clip\":{},\"videoCrop\":{},\"common_keyframes\":[],\"animations\":[],\"effects\":[]}],\"textLayers\":[],\"resources\":[{\"kind\":\"image\",\"resourceKey\":\"image\"}]}", Map.class);
            Map<String,Object> descriptor = BrowserNativeRuntimeContractTest.descriptor();
            descriptor.put("schemaVersion", "legacy".equals(variant) ? "browser-native-scene-runtime-v2" : "browser-native-scene-runtime-v3");
            Map<String,Object> delivery = new LinkedHashMap<>();
            delivery.put("schemaVersion", "browser-runtime-delivery-v1");
            delivery.put("nativeEngine", descriptor);
            delivery.put("resources", "missingDependency".equals(variant) ? Collections.emptyList()
                    : Collections.singletonList(Collections.singletonMap("resourceId", "effect")));
            scene.put("runtimeDelivery", delivery);
            List<Map<String,Object>> layers = (List<Map<String,Object>>) scene.get("layers");
            if (variant.startsWith("video")) {
                layers.get(0).put("type", "video");
                ((List<Map<String,Object>>)scene.get("resources")).get(0).put("kind", "video");
                if (!"videoV3".equals(variant)) descriptor.put("schemaVersion", "browser-native-scene-runtime-v4");
                if (!"videoPolicyMissing".equals(variant)) descriptor.put("videoAudioPolicy", "external_music_only");
            }
            if ("mixedText".equals(variant)) {
                layers.add(mapper.readValue("{\"layerId\":\"text\",\"type\":\"text\"}", Map.class));
                scene.put("textLayers", Collections.singletonList(mapper.readValue("{\"segmentId\":\"text\",\"nativeTextSource\":{\"schemaVersion\":\"native-text-source-v1\",\"status\":\"material_projected\"}}", Map.class)));
            }
            if ("missingDelivery".equals(variant)) scene.remove("runtimeDelivery");
            if ("missingResource".equals(variant)) scene.put("resources", Collections.emptyList());
            if ("missingClip".equals(variant)) layers.get(0).remove("clip");
            if ("photo".equals(variant)) layers.get(0).put("type", "photo");
            if ("duplicateLayer".equals(variant)) layers.add(new LinkedHashMap<>(layers.get(0)));
            Map<String,Object> stored = new LinkedHashMap<>();
            stored.put("status", "notReady".equals(variant) ? "pending" : "ready");
            stored.put("scene_json", mapper.writeValueAsString(scene));
            when(repository.browserScene("tplver_1")).thenReturn(stored);
            service.prepareBrowserAsync("owner", "fixed-job");
            if ("valid".equals(variant) || "mixedText".equals(variant) || "videoV4".equals(variant)) {
                verify(repository).completeBrowserPreparation(eq("fixed-job"), anyString());
                verify(repository, never()).slotDefaultMedia(anyString(), anySet());
            } else {
                verify(repository, never()).completeBrowserPreparation(anyString(), anyString());
                verify(repository).failBrowserPreparation(eq("fixed-job"), eq("MV_RENDER_TEMPLATE_HAS_NO_SLOTS"), anyString(), eq(false));
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyBindingsRequireVerifiedNativeStickerOwnership() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (String variant : Arrays.asList("valid", "missingPolicy", "legacy", "missingSource", "unbound", "mismatch",
                "missingBinding", "wrongKind", "missingDependency", "missingLua", "effect", "photo", "extraBinding", "notReady")) {
            MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
            AiMusicJobRepository music = mock(AiMusicJobRepository.class);
            MusicMvRenderJobService service = new MusicMvRenderJobService(repository, music,
                    mock(MusicMvRenderArtifactStorageService.class), inputAssets(), mapper, true, 2);
            MusicMvRenderJobCreateRequest request = request();
            if (!"extraBinding".equals(variant)) request.setSlotBindings(Collections.emptyList());
            when(music.ownedCandidate("owner", "song_1")).thenReturn(candidate());
            when(repository.claimBrowserPreparation("sticker-job")).thenReturn(preparingRow("sticker-job", request));
            when(repository.updateBrowserPreparation("sticker-job", "preparing_template", 0.55d))
                    .thenReturn(preparingRow("sticker-job", request));
            when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(new RenderContract(version(), Collections.emptyList()));
            Map<String,Object> scene;
            try (java.io.InputStream stream = getClass().getResourceAsStream("/musicmv/native-zero-slot-sticker-scene.json")) {
                scene = mapper.readValue(stream, Map.class);
            }
            Map<String,Object> delivery = (Map<String,Object>) scene.get("runtimeDelivery");
            Map<String,Object> descriptor = (Map<String,Object>) delivery.get("nativeEngine");
            Map<String,Object> layer = ((List<Map<String,Object>>) scene.get("layers")).get(0);
            Map<String,Object> source = (Map<String,Object>) layer.get("nativeStickerSource");
            if ("missingPolicy".equals(variant)) descriptor.remove("stickerPolicy");
            if ("legacy".equals(variant)) descriptor.put("schemaVersion", "browser-native-scene-runtime-v3");
            if ("missingSource".equals(variant)) layer.remove("nativeStickerSource");
            if ("unbound".equals(variant)) source.put("attachmentStatus", "unresolved");
            if ("mismatch".equals(variant)) source.put("segmentId", "another-segment");
            if ("missingBinding".equals(variant)) descriptor.put("bindings", Collections.emptyList());
            if ("wrongKind".equals(variant)) ((List<Map<String,Object>>) descriptor.get("bindings")).get(0).put("kind", "filter");
            if ("missingDependency".equals(variant)) delivery.put("resources", Collections.emptyList());
            if ("missingLua".equals(variant)) ((List<String>) descriptor.get("files")).removeIf(file -> file.endsWith("/infoSticker.lua"));
            if ("effect".equals(variant)) layer.put("effects", Collections.singletonList(Collections.emptyMap()));
            if ("photo".equals(variant)) layer.put("type", "photo");
            Map<String,Object> stored = new LinkedHashMap<>();
            stored.put("status", "notReady".equals(variant) ? "pending" : "ready");
            stored.put("scene_json", mapper.writeValueAsString(scene));
            when(repository.browserScene("tplver_1")).thenReturn(stored);
            service.prepareBrowserAsync("owner", "sticker-job");
            if ("valid".equals(variant)) {
                verify(repository).completeBrowserPreparation(eq("sticker-job"), anyString());
                verify(repository, never()).slotDefaultMedia(anyString(), anySet());
            } else {
                verify(repository, never()).completeBrowserPreparation(anyString(), anyString());
                verify(repository).failBrowserPreparation(eq("sticker-job"), eq("MV_RENDER_TEMPLATE_HAS_NO_SLOTS"), anyString(), eq(false));
            }
        }
    }

    @Test
    void completedLibraryUsesExtraRowOnlyForHasMore() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                mock(AiMusicJobRepository.class), mock(MusicMvRenderArtifactStorageService.class),
                inputAssets(), new ObjectMapper(), true, 2);
        Map<String, Object> first = row("mvr_1", "fingerprint");
        first.put("status", "completed");
        first.put("output_storage_key", "r2:video.mp4");
        when(repository.ownedJobs("usr_1", 2, true, 24)).thenReturn(Arrays.asList(first, first));
        Map<String, Object> page = service.listCompleted("usr_1", 1, 24);
        assertEquals(1, ((List<?>) page.get("items")).size());
        assertEquals(Boolean.TRUE, page.get("hasMore"));
        when(repository.ownedJobs("usr_1", 2, true, 25)).thenReturn(Collections.singletonList(first));
        assertEquals(Boolean.FALSE, service.listCompleted("usr_1", 1, 25).get("hasMore"));
        when(repository.ownedJobs("usr_1", 2, true, 26)).thenReturn(Collections.emptyList());
        assertEquals(0, service.listCompleted("usr_1", 1, 26).get("count"));
    }

    @Test
    void createsExactPublishedTemplateJobAndSupportsIdempotentReplay() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderArtifactStorageService artifacts = mock(MusicMvRenderArtifactStorageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(
                repository, aiMusicJobs, artifacts, inputAssets(), new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        Map<String, Object> created = service.create("website-backend", request);

        assertEquals("preparing", created.get("status"));
        assertEquals("preparing_queued", created.get("stage"));
        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        verify(repository).createBrowserPreparing(anyString(), eq("website-backend"), eq("req_1"),
                eq("tpl_1"), eq("tplver_1"), fingerprint.capture(), anyString(),
                anyString(), anyString());

        when(repository.byClientRequest("website-backend", "req_1"))
                .thenReturn(row("mvr_existing", fingerprint.getValue()));
        Map<String, Object> replay = service.create("website-backend", request);
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals("mvr_existing", replay.get("jobId"));

        MusicMvRenderJobCreateRequest.Crop changedCrop = new MusicMvRenderJobCreateRequest.Crop();
        changedCrop.setX(Double.valueOf(65.0d));
        changedCrop.setY(Double.valueOf(50.0d));
        changedCrop.setZoom(Double.valueOf(1.0d));
        request.getSlotBindings().get(0).setCrop(changedCrop);
        ApiException conflict = assertThrows(ApiException.class,
                () -> service.create("website-backend", request));
        assertEquals("MV_RENDER_IDEMPOTENCY_CONFLICT", conflict.getCode());
    }

    @Test
    void defaultPhotoCropParticipatesInIdempotency() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderArtifactStorageService artifacts = mock(MusicMvRenderArtifactStorageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(
                repository, aiMusicJobs, artifacts, inputAssets(), new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        request.getSlotBindings().get(0).setAsset(null);
        request.getSlotBindings().get(0).setUseTemplateDefault(Boolean.TRUE);
        Map<String, Object> created = service.create("website-backend", request);

        assertEquals("preparing", created.get("status"));
        assertEquals("preparing_queued", created.get("stage"));
        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        verify(repository).createBrowserPreparing(anyString(), eq("website-backend"), eq("req_1"),
                eq("tpl_1"), eq("tplver_1"), fingerprint.capture(), anyString(),
                anyString(), anyString());

        when(repository.byClientRequest("website-backend", "req_1"))
                .thenReturn(row("mvr_existing", fingerprint.getValue()));
        Map<String, Object> replay = service.create("website-backend", request);
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals("mvr_existing", replay.get("jobId"));

        MusicMvRenderJobCreateRequest.Crop changedCrop = new MusicMvRenderJobCreateRequest.Crop();
        changedCrop.setX(Double.valueOf(65.0d));
        changedCrop.setY(Double.valueOf(50.0d));
        changedCrop.setZoom(Double.valueOf(1.0d));
        request.getSlotBindings().get(0).setCrop(changedCrop);
        ApiException conflict = assertThrows(ApiException.class,
                () -> service.create("website-backend", request));
        assertEquals("MV_RENDER_IDEMPOTENCY_CONFLICT", conflict.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void carriesValidatedDownloadSettingsIntoBrowserRenderContract() throws Exception {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                aiMusicJobs, mock(MusicMvRenderArtifactStorageService.class),
                inputAssets(), new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        request.setOutputFileName("family-story.mp4");
        MusicMvRenderJobCreateRequest.OutputVideo output =
                new MusicMvRenderJobCreateRequest.OutputVideo();
        output.setWidth(Integer.valueOf(720));
        output.setHeight(Integer.valueOf(1280));
        output.setFps(Integer.valueOf(60));
        output.setQuality("high");
        output.setFormat("mp4");
        request.setOutputVideo(output);
        when(repository.claimBrowserPreparation("mvr_1"))
                .thenReturn(preparingRow("mvr_1", request));
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());
        when(repository.updateBrowserPreparation("mvr_1", "preparing_template", 0.55d))
                .thenReturn(preparingRow("mvr_1", request));
        when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(
                new RenderContract(version(), Arrays.asList(slot("photo_01"), slot("photo_02"))));
        when(repository.slotDefaultMedia(eq("tplver_1"), anySet()))
                .thenReturn(Collections.<String, Map<String, Object>>emptyMap());
        when(repository.completeBrowserPreparation(eq("mvr_1"), anyString()))
                .thenReturn(row("mvr_1", null));

        service.prepareBrowserAsync("website-backend", "mvr_1");

        ArgumentCaptor<String> prepared = ArgumentCaptor.forClass(String.class);
        verify(repository).completeBrowserPreparation(eq("mvr_1"), prepared.capture());
        Map<String, Object> payload = new ObjectMapper().readValue(prepared.getValue(), Map.class);
        Map<String, Object> preparedOutput = (Map<String, Object>) payload.get("outputVideo");
        assertEquals(720, ((Number) preparedOutput.get("width")).intValue());
        assertEquals(1280, ((Number) preparedOutput.get("height")).intValue());
        assertEquals(60, ((Number) preparedOutput.get("fps")).intValue());
        assertEquals("high", preparedOutput.get("quality"));
        assertEquals("mp4", preparedOutput.get("format"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsDownloadSettingsToThePublishedTemplateOutput() throws Exception {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                aiMusicJobs, mock(MusicMvRenderArtifactStorageService.class),
                inputAssets(), new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        request.setOutputVideo(null);
        when(repository.claimBrowserPreparation("mvr_1"))
                .thenReturn(preparingRow("mvr_1", request));
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());
        when(repository.updateBrowserPreparation("mvr_1", "preparing_template", 0.55d))
                .thenReturn(preparingRow("mvr_1", request));
        when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(
                new RenderContract(version(), Arrays.asList(slot("photo_01"), slot("photo_02"))));
        when(repository.slotDefaultMedia(eq("tplver_1"), anySet()))
                .thenReturn(Collections.<String, Map<String, Object>>emptyMap());
        when(repository.completeBrowserPreparation(eq("mvr_1"), anyString()))
                .thenReturn(row("mvr_1", null));

        service.prepareBrowserAsync("website-backend", "mvr_1");

        ArgumentCaptor<String> prepared = ArgumentCaptor.forClass(String.class);
        verify(repository).completeBrowserPreparation(eq("mvr_1"), prepared.capture());
        Map<String, Object> payload = new ObjectMapper().readValue(prepared.getValue(), Map.class);
        Map<String, Object> output = (Map<String, Object>) payload.get("outputVideo");
        assertEquals(Integer.valueOf(1080), output.get("width"));
        assertEquals(Integer.valueOf(1920), output.get("height"));
        assertEquals(Integer.valueOf(30), output.get("fps"));
        assertEquals("recommended", output.get("quality"));
        assertEquals("mp4", output.get("format"));
    }

    @Test
    void acceptsEverySupportedDownloadFrameRateAndQuality() {
        for (int fps : new int[] { 24, 30, 60 }) {
            for (String quality : Arrays.asList("standard", "recommended", "high")) {
                MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
                MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                        mock(AiMusicJobRepository.class), mock(MusicMvRenderArtifactStorageService.class),
                        inputAssets(), new ObjectMapper(), true, 2);
                MusicMvRenderJobCreateRequest request = request();
                request.setRequestId("req_" + fps + "_" + quality);
                request.setOutputVideo(outputVideo(1080, 1920, fps, quality, "mp4"));

                Map<String, Object> created = service.create("website-backend", request);

                assertEquals("preparing", created.get("status"));
                verify(repository).createBrowserPreparing(anyString(), eq("website-backend"),
                        eq(request.getRequestId()), eq("tpl_1"), eq("tplver_1"),
                        anyString(), anyString(), anyString(), anyString());
            }
        }
    }

    @Test
    void rejectsUnsupportedDownloadResolutionFrameRateQualityAndFormat() {
        assertInvalidOutput(outputVideo(1, 1920, 30, "recommended", "mp4"),
                "MV_RENDER_OUTPUT_RESOLUTION_INVALID");
        assertInvalidOutput(outputVideo(1080, 1920, 25, "recommended", "mp4"),
                "MV_RENDER_OUTPUT_FPS_INVALID");
        assertInvalidOutput(outputVideo(1080, 1920, 30, "ultra", "mp4"),
                "MV_RENDER_OUTPUT_QUALITY_INVALID");
        assertInvalidOutput(outputVideo(1080, 1920, 30, "recommended", "webm"),
                "MV_RENDER_OUTPUT_FORMAT_INVALID");
    }

    @Test
    void rejectsDownloadResolutionThatChangesTemplateAspectRatio() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                aiMusicJobs, mock(MusicMvRenderArtifactStorageService.class),
                inputAssets(), new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        request.setOutputVideo(outputVideo(1080, 1080, 30, "recommended", "mp4"));
        when(repository.claimBrowserPreparation("mvr_1"))
                .thenReturn(preparingRow("mvr_1", request));
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());
        when(repository.updateBrowserPreparation("mvr_1", "preparing_template", 0.55d))
                .thenReturn(preparingRow("mvr_1", request));
        when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(
                new RenderContract(version(), Arrays.asList(slot("photo_01"), slot("photo_02"))));
        when(repository.slotDefaultMedia(eq("tplver_1"), anySet()))
                .thenReturn(Collections.<String, Map<String, Object>>emptyMap());

        service.prepareBrowserAsync("website-backend", "mvr_1");

        verify(repository).failBrowserPreparation("mvr_1", "MV_RENDER_OUTPUT_ASPECT_INVALID",
                "Output resolution must keep the template aspect ratio", false);
        verify(repository, never()).completeBrowserPreparation(anyString(), anyString());
    }

    @Test
    void rejectsIncompleteSlotContract() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                aiMusicJobs, mock(MusicMvRenderArtifactStorageService.class),
                inputAssets(), new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        request.setSlotBindings(Collections.singletonList(request.getSlotBindings().get(0)));
        when(repository.claimBrowserPreparation("mvr_1"))
                .thenReturn(preparingRow("mvr_1", request));
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());
        when(repository.updateBrowserPreparation("mvr_1", "preparing_template", 0.55d))
                .thenReturn(preparingRow("mvr_1", request));
        when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(
                new RenderContract(version(), Arrays.asList(slot("photo_01"), slot("photo_02"))));

        service.prepareBrowserAsync("website-backend", "mvr_1");

        verify(repository).failBrowserPreparation("mvr_1",
                "MV_RENDER_SLOT_BINDINGS_INCOMPLETE", "Every template material slot must have exactly one image", false);
    }

    @Test
    void acceptsReadyTemplatePhotoWithoutCreatingAUserAssetReference() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvInputAssetStorageService inputAssets = inputAssets();
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                aiMusicJobs, mock(MusicMvRenderArtifactStorageService.class),
                inputAssets, new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        request.getSlotBindings().get(0).setAsset(null);
        request.getSlotBindings().get(0).setCrop(null);
        request.getSlotBindings().get(0).setUseTemplateDefault(Boolean.TRUE);
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());
        when(repository.claimBrowserPreparation("mvr_1")).thenReturn(preparingRow("mvr_1", request));
        when(repository.updateBrowserPreparation("mvr_1", "preparing_template", 0.55d))
                .thenReturn(preparingRow("mvr_1", request));
        when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(
                new RenderContract(version(), Arrays.asList(slot("photo_01"), slot("photo_02"))));
        Map<String, Object> media = row("media_template_photo", null);
        media.put("status", "ready");
        when(repository.slotDefaultMedia(eq("tplver_1"), anySet())).thenReturn(
                Collections.singletonMap("photo_01", media));
        when(repository.completeBrowserPreparation(eq("mvr_1"), anyString()))
                .thenReturn(row("mvr_1", null));

        service.prepareBrowserAsync("website-backend", "mvr_1");

        verify(repository).completeBrowserPreparation(eq("mvr_1"), anyString());
        verify(inputAssets, never()).requireOwnedCloudAsset(eq("website-backend"),
                eq((MusicMvRenderJobCreateRequest.Asset) null), eq("image"));
    }

    @Test
    void acceptsOnlyCapabilityProtectedLocalInputWhenGeneralLoopbackIsDisabled() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                aiMusicJobs, mock(MusicMvRenderArtifactStorageService.class),
                inputAssets(), new ObjectMapper(), false, 2);
        MusicMvRenderJobCreateRequest request = request();
        String capabilityUrl = "http://127.0.0.1:8080/api/music-mv/v1/assets/"
                + "mva_0123456789abcdef0123456789abcdef?access=" + repeat('d');
        Map<String, Object> candidate = candidate();
        candidate.put("storage_url", capabilityUrl);
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate);
        for (MusicMvRenderJobCreateRequest.SlotBinding binding : request.getSlotBindings()) {
            binding.getAsset().setUrl(capabilityUrl);
        }
        when(repository.claimBrowserPreparation("mvr_1")).thenReturn(preparingRow("mvr_1", request));
        when(repository.updateBrowserPreparation("mvr_1", "preparing_template", 0.55d))
                .thenReturn(preparingRow("mvr_1", request));
        when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(
                new RenderContract(version(), Arrays.asList(slot("photo_01"), slot("photo_02"))));
        when(repository.slotDefaultMedia(eq("tplver_1"), anySet()))
                .thenReturn(Collections.<String, Map<String, Object>>emptyMap());
        when(repository.completeBrowserPreparation(eq("mvr_1"), anyString()))
                .thenReturn(row("mvr_1", null));

        service.prepareBrowserAsync("website-backend", "mvr_1");

        verify(repository).completeBrowserPreparation(eq("mvr_1"), anyString());
    }

    @Test
    void rejectsUnprotectedLocalInputWhenGeneralLoopbackIsDisabled() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(
                repository,
                aiMusicJobs,
                mock(MusicMvRenderArtifactStorageService.class), inputAssets(),
                new ObjectMapper(), false, 2);
        MusicMvRenderJobCreateRequest request = request();
        when(repository.claimBrowserPreparation("mvr_1"))
                .thenReturn(preparingRow("mvr_1", request));
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());

        service.prepareBrowserAsync("website-backend", "mvr_1");

        verify(repository).failBrowserPreparation("mvr_1", "MV_RENDER_ASSET_URL_BLOCKED",
                "Input assets require HTTPS; loopback HTTP is only available in local development", false);
    }

    @Test
    void browserRenderingDoesNotWaitForTheMacRenderer() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository, aiMusicJobs,
                mock(MusicMvRenderArtifactStorageService.class), inputAssets(),
                new ObjectMapper(), true, 2);
        Map<String, Object> offlineVersion = version();
        offlineVersion.put("source_availability", "unavailable");
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());
        MusicMvRenderJobCreateRequest request = request();
        when(repository.claimBrowserPreparation("mvr_1"))
                .thenReturn(preparingRow("mvr_1", request));
        when(repository.updateBrowserPreparation("mvr_1", "preparing_template", 0.55d))
                .thenReturn(preparingRow("mvr_1", request));
        when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(
                new RenderContract(offlineVersion, Arrays.asList(slot("photo_01"), slot("photo_02"))));
        when(repository.slotDefaultMedia(eq("tplver_1"), anySet()))
                .thenReturn(Collections.<String, Map<String, Object>>emptyMap());
        when(repository.completeBrowserPreparation(eq("mvr_1"), anyString()))
                .thenReturn(row("mvr_1", null));

        service.prepareBrowserAsync("website-backend", "mvr_1");

        verify(repository).completeBrowserPreparation(eq("mvr_1"), anyString());
    }

    @Test
    void rejectsLegacyNativeValidatedTemplateFromProductRenderPath() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository, aiMusicJobs,
                mock(MusicMvRenderArtifactStorageService.class), inputAssets(),
                new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        Map<String, Object> nativeVersion = version();
        nativeVersion.put("validation_status", "exact");
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());
        when(repository.claimBrowserPreparation("mvr_1"))
                .thenReturn(preparingRow("mvr_1", request));
        when(repository.updateBrowserPreparation("mvr_1", "preparing_template", 0.55d))
                .thenReturn(preparingRow("mvr_1", request));
        when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(
                new RenderContract(nativeVersion, Arrays.asList(slot("photo_01"), slot("photo_02"))));

        service.prepareBrowserAsync("website-backend", "mvr_1");

        verify(repository).failBrowserPreparation("mvr_1", "MV_RENDER_TEMPLATE_NOT_RENDERABLE",
                "Requested template version is not published and browser-render ready", false);
        verify(repository, never()).completeBrowserPreparation(anyString(), anyString());
    }

    @Test
    void preparesPublishedHistoricalVersionWithoutSwitchingToCurrentVersion() throws Exception {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository music = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository, music,
                mock(MusicMvRenderArtifactStorageService.class), inputAssets(), new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        Map<String, Object> historical = version(); historical.put("current_version_id", "new-version");
        when(music.ownedCandidate("owner", "song_1")).thenReturn(candidate());
        when(repository.claimBrowserPreparation("mvr_history")).thenReturn(preparingRow("mvr_history", request));
        when(repository.updateBrowserPreparation("mvr_history", "preparing_template", 0.55d))
                .thenReturn(preparingRow("mvr_history", request));
        when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(new RenderContract(historical,
                Arrays.asList(slot("photo_01"), slot("photo_02"))));
        when(repository.slotDefaultMedia(eq("tplver_1"), anySet())).thenReturn(Collections.emptyMap());
        service.prepareBrowserAsync("owner", "mvr_history");
        ArgumentCaptor<String> prepared = ArgumentCaptor.forClass(String.class);
        verify(repository).completeBrowserPreparation(eq("mvr_history"), prepared.capture());
        assertEquals("tplver_1", new ObjectMapper().readTree(prepared.getValue()).path("templateVersionId").asText());
        verify(repository, never()).renderContract("tpl_1", "new-version");
    }

    @Test
    void historicalVersionStillRejectsUnpublishedUnreadyAndMismatchedContracts() {
        for (String field : Arrays.asList("template_status", "version_status", "validation_status",
                "browser_scene_status", "template_id", "version_id")) {
            MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
            AiMusicJobRepository music = mock(AiMusicJobRepository.class);
            MusicMvRenderJobService service = new MusicMvRenderJobService(repository, music,
                    mock(MusicMvRenderArtifactStorageService.class), inputAssets(), new ObjectMapper(), true, 2);
            MusicMvRenderJobCreateRequest request = request();
            Map<String, Object> historical = version(); historical.put("current_version_id", "new-version");
            historical.put(field, "invalid");
            when(music.ownedCandidate("owner", "song_1")).thenReturn(candidate());
            when(repository.claimBrowserPreparation("mvr_history")).thenReturn(preparingRow("mvr_history", request));
            when(repository.updateBrowserPreparation("mvr_history", "preparing_template", 0.55d))
                    .thenReturn(preparingRow("mvr_history", request));
            when(repository.renderContract("tpl_1", "tplver_1")).thenReturn(new RenderContract(historical, Collections.emptyList()));
            service.prepareBrowserAsync("owner", "mvr_history");
            verify(repository).failBrowserPreparation("mvr_history", "MV_RENDER_TEMPLATE_NOT_RENDERABLE",
                    "Requested template version is not published and browser-render ready", false);
            verify(repository, never()).completeBrowserPreparation(anyString(), anyString());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliversZeroPhotoStickerSessionWithOriginalSourceAndMinimalDependencies() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository music = mock(AiMusicJobRepository.class);
        CloudflareTemplateMediaProvider media = mock(CloudflareTemplateMediaProvider.class);
        TemplateRuntimePackageService packages = mock(TemplateRuntimePackageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository, music,
                mock(MusicMvRenderArtifactStorageService.class), inputAssets(), media, packages, mapper, true, 2);
        Map<String,Object> scene;
        try (java.io.InputStream stream = getClass().getResourceAsStream("/musicmv/native-zero-slot-sticker-scene.json")) {
            scene = mapper.readValue(stream, Map.class);
        }
        scene.put("capability", browserCapability(true, Collections.emptyList()));
        scene.put("canvas", Collections.singletonMap("durationSeconds", 16.133333d));
        Map<String,Object> sourceAsset = new LinkedHashMap<>();
        sourceAsset.put("sourceSha256", "14e1687cb1bc6f0a68e7cd359ff77b2bf888292ed0a4a35cd8cbc1c7f4f76b1b");
        sourceAsset.put("sourceSizeBytes", 479738);
        Map<String,Object> resource = new LinkedHashMap<>();
        String resourceKey = (String) ((List<Map<String,Object>>) scene.get("layers")).get(0).get("resourceKey");
        resource.put("resourceKey", resourceKey);
        resource.put("role", "browser_resource:" + resourceKey);
        resource.put("kind", "image");
        resource.put("sourceAsset", sourceAsset);
        scene.put("resources", Collections.singletonList(resource));
        Map<String,Object> active = row("sticker-session", null);
        active.put("template_id", "tpl_1");
        active.put("client_id", "usr_owner");
        active.put("request_json", "{\"musicCandidateId\":\"song_1\",\"music\":{},\"slotBindings\":[]}");
        when(repository.byId("sticker-session")).thenReturn(active);
        Map<String,Object> sceneRow = new LinkedHashMap<>();
        sceneRow.put("status", "ready"); sceneRow.put("scene_json", mapper.writeValueAsString(scene));
        when(repository.browserScene("tplver_1")).thenReturn(sceneRow);
        when(repository.events("sticker-session")).thenReturn(Collections.emptyList());
        when(repository.browserMediaByRole(eq("tplver_1"), anySet())).thenReturn(Collections.emptyMap());
        when(music.ownedCandidate("usr_owner", "song_1")).thenReturn(candidate());
        Map<String,Object> exactImage = new LinkedHashMap<>(sourceAsset);
        exactImage.put("url", "https://assets.example/original-sticker.png");
        when(packages.downloadExactImage(org.mockito.ArgumentMatchers.anyMap())).thenReturn(exactImage);
        Map<String,Object> runtime = new LinkedHashMap<>();
        runtime.put("status", "ready");runtime.put("downloadUrl", "https://assets.example/minimal-runtime.zip");
        runtime.put("nativeEngine", ((Map<?,?>) scene.get("runtimeDelivery")).get("nativeEngine"));
        runtime.put("objectKey", "private/runtime.zip");runtime.put("errorMessage", "private provider detail");
        when(packages.downloadForScene(eq("tpl_1"), eq("tplver_1"), org.mockito.ArgumentMatchers.anyMap())).thenReturn(runtime);

        Map<String,Object> result = service.get("usr_owner", "sticker-session");
        Map<String,Object> contract = (Map<String,Object>) result.get("browserRender");
        assertEquals("browser", result.get("renderMode"));
        assertEquals(Collections.emptyList(), contract.get("slotBindings"));
        assertEquals(scene, contract.get("scene"));
        assertEquals(exactImage, ((List<Map<String,Object>>) contract.get("resources")).get(0).get("asset"));
        Map<String,Object> delivered = (Map<String,Object>) contract.get("runtimePackage");
        assertEquals(runtime.get("nativeEngine"), delivered.get("nativeEngine"));
        assertEquals(runtime.get("downloadUrl"), delivered.get("downloadUrl"));
        assertEquals(null, delivered.get("objectKey"));assertEquals(null, delivered.get("errorMessage"));
        assertEquals(null, contract.get("sourceVideo"));
        verify(repository, never()).slotDefaultMedia(anyString(), anySet());
        verify(repository, never()).slotDefaultMedia(anyString(), anyString());
        verify(packages).downloadExactImage(resource);
        verify(packages).downloadForScene("tpl_1", "tplver_1", scene);
        assertThrows(ApiException.class, () -> service.get("another-owner", "sticker-session"));
        ApiException unavailable = new ApiException(org.springframework.http.HttpStatus.CONFLICT,
                "TEMPLATE_EXACT_IMAGE_UNAVAILABLE", "Original resource is unavailable");
        when(packages.downloadExactImage(org.mockito.ArgumentMatchers.anyMap())).thenThrow(unavailable);
        assertEquals(unavailable, assertThrows(ApiException.class, () -> service.get("usr_owner", "sticker-session")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesDefaultTemplatePhotosWithoutUsingAFlattenedVideo() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        CloudflareTemplateMediaProvider templateMedia = mock(CloudflareTemplateMediaProvider.class);
        TemplateRuntimePackageService runtimePackages = mock(TemplateRuntimePackageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository, aiMusicJobs,
                mock(MusicMvRenderArtifactStorageService.class), inputAssets(), templateMedia,
                runtimePackages,
                new ObjectMapper(), true, 2);
        Map<String, Object> active = row("mvr_browser", null);
        active.put("template_id", "tpl_1");
        active.put("client_id", "usr_owner");
        active.put("request_json", "{\"musicCandidateId\":\"song_1\",\"music\":{},"
                + "\"slotBindings\":[{\"slotKey\":\"photo_01\",\"useTemplateDefault\":true}],"
                + "\"volume\":0.5011872336272722,"
                + "\"outputVideo\":{\"width\":1080,\"height\":1920,\"fps\":30}}");
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("canvas", Collections.singletonMap("durationSeconds", Double.valueOf(30.633d)));
        scene.put("capability", browserCapability(true, Collections.<String>emptyList()));
        Map<String, Object> resource = new LinkedHashMap<String, Object>();
        resource.put("resourceKey", "lut_background");
        resource.put("kind", "lut_2d_png");
        resource.put("role", "browser_resource:lut_background");
        Map<String, Object> fontResource = new LinkedHashMap<String, Object>();
        fontResource.put("resourceKey", "font_123");
        fontResource.put("kind", "font");
        fontResource.put("role", "browser_resource:font_123");
        fontResource.put("inlineData", "data:font/ttf;base64,AAECAw==");
        Map<String, Object> videoResource = new LinkedHashMap<String, Object>();
        videoResource.put("resourceKey", "video_background");
        videoResource.put("kind", "video");
        videoResource.put("role", "browser_resource:video_background");
        scene.put("resources", Arrays.asList(resource, fontResource, videoResource));
        Map<String, Object> sceneRow = new LinkedHashMap<String, Object>();
        sceneRow.put("status", "ready");
        sceneRow.put("scene_json", json(scene));
        Map<String, Object> media = new LinkedHashMap<String, Object>();
        media.put("status", "ready");
        media.put("provider", "cloudflare_images");
        media.put("provider_asset_id", "image_1");
        media.put("provider_details_json", "{}");
        Map<String, Object> videoMedia = new LinkedHashMap<String, Object>();
        videoMedia.put("status", "ready");
        videoMedia.put("provider", "cloudflare_stream");
        videoMedia.put("provider_asset_id", "video_1");
        videoMedia.put("provider_details_json", "{}");
        when(repository.byId("mvr_browser")).thenReturn(active);
        when(repository.browserScene("tplver_1")).thenReturn(sceneRow);
        Map<String, Map<String, Object>> mediaRows = new LinkedHashMap<String, Map<String, Object>>();
        mediaRows.put("slot_default:photo_01", media);
        mediaRows.put("browser_resource:lut_background", media);
        mediaRows.put("browser_resource:video_background", videoMedia);
        when(repository.browserMediaByRole(eq("tplver_1"), anySet())).thenReturn(mediaRows);
        when(repository.events("mvr_browser")).thenReturn(Collections.emptyList());
        when(aiMusicJobs.ownedCandidate("usr_owner", "song_1")).thenReturn(candidate());
        when(templateMedia.resolveDeliveryDetails(eq("cloudflare_images"), eq("image_1"),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(Collections.<String, Object>singletonMap(
                        "deliveryUrl", "https://images.example/photo.jpg"));
        when(templateMedia.resolveDeliveryDetails(eq("cloudflare_stream"), eq("video_1"),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(Collections.<String, Object>singletonMap(
                        "playbackUrl", "https://stream.example/video.m3u8"));
        Map<String, Object> runtimePackage = new LinkedHashMap<String, Object>();
        runtimePackage.put("templateId", "tpl_1");
        runtimePackage.put("versionId", "tplver_1");
        runtimePackage.put("status", "ready");
        runtimePackage.put("downloadUrl", "https://r2.example/runtime.zip");
        runtimePackage.put("sourceSha256", "abc123");
        runtimePackage.put("sourceSizeBytes", Long.valueOf(1234L));
        runtimePackage.put("objectKey", "private/runtime.zip");
        when(runtimePackages.downloadForScene(org.mockito.ArgumentMatchers.eq("tpl_1"), org.mockito.ArgumentMatchers.eq("tplver_1"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(runtimePackage);

        Map<String, Object> result = service.get("usr_owner", "mvr_browser");
        Map<String, Object> browserRender = (Map<String, Object>) result.get("browserRender");
        List<Map<String, Object>> bindings =
                (List<Map<String, Object>>) browserRender.get("slotBindings");
        Map<String, Object> asset = (Map<String, Object>) bindings.get(0).get("asset");
        assertEquals("https://images.example/photo.jpg", asset.get("url"));
        List<Map<String, Object>> resources =
                (List<Map<String, Object>>) browserRender.get("resources");
        Map<String, Object> resourceAsset =
                (Map<String, Object>) resources.get(0).get("asset");
        assertEquals("https://images.example/photo.jpg", resourceAsset.get("url"));
        Map<String, Object> fontAsset =
                (Map<String, Object>) resources.get(1).get("asset");
        assertEquals("font", fontAsset.get("kind"));
        assertEquals("data:font/ttf;base64,AAECAw==", fontAsset.get("url"));
        Map<String, Object> videoAsset =
                (Map<String, Object>) resources.get(2).get("asset");
        assertEquals("video", videoAsset.get("kind"));
        assertEquals("https://stream.example/video.m3u8", videoAsset.get("url"));
        Map<String, Object> issuedRuntimePackage =
                (Map<String, Object>) browserRender.get("runtimePackage");
        assertEquals("https://r2.example/runtime.zip", issuedRuntimePackage.get("downloadUrl"));
        assertEquals(null, issuedRuntimePackage.get("objectKey"));
        assertEquals(null, browserRender.get("sourceVideo"));
        verify(repository).browserMediaByRole("tplver_1", new java.util.LinkedHashSet<String>(Arrays.asList(
                "slot_default:photo_01", "browser_resource:lut_background", "browser_resource:video_background")));
        verify(repository, never()).slotDefaultMedia(anyString(), anyString());
        verify(repository, never()).mediaByRole(anyString(), anyString());

        Map<String, Object> outputVideo =
                (Map<String, Object>) browserRender.get("outputVideo");
        assertEquals(1080, ((Number) outputVideo.get("width")).intValue());
        assertEquals(1920, ((Number) outputVideo.get("height")).intValue());
        assertEquals(30, ((Number) outputVideo.get("fps")).intValue());
        assertEquals(0.5011872336272722d, ((Number) browserRender.get("volume")).doubleValue());
        mediaRows.remove("slot_default:photo_01");
        assertEquals("MV_BROWSER_DEFAULT_ASSET_UNAVAILABLE", assertThrows(ApiException.class,
                () -> service.get("usr_owner", "mvr_browser")).getCode());
        mediaRows.put("slot_default:photo_01", media);
        videoMedia.put("status", "uploading");
        assertEquals("MV_BROWSER_RESOURCE_UNAVAILABLE", assertThrows(ApiException.class,
                () -> service.get("usr_owner", "mvr_browser")).getCode());
        videoMedia.put("status", "ready");
        mediaRows.remove("browser_resource:lut_background");
        assertEquals("MV_BROWSER_RESOURCE_UNAVAILABLE", assertThrows(ApiException.class,
                () -> service.get("usr_owner", "mvr_browser")).getCode());

    }

    @Test
    void refusesToIssueRenderContractForIncompleteBrowserScene() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        CloudflareTemplateMediaProvider templateMedia = mock(CloudflareTemplateMediaProvider.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository, aiMusicJobs,
                mock(MusicMvRenderArtifactStorageService.class), inputAssets(), templateMedia,
                new ObjectMapper(), true, 2);
        Map<String, Object> active = row("mvr_browser", null);
        active.put("client_id", "usr_owner");
        active.put("request_json", "{\"musicCandidateId\":\"song_1\",\"music\":{},"
                + "\"slotBindings\":[]}");
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("capability", browserCapability(false,
                Collections.singletonList("video_layers")));
        Map<String, Object> sceneRow = new LinkedHashMap<String, Object>();
        sceneRow.put("status", "ready");
        sceneRow.put("scene_json", json(scene));
        when(repository.byId("mvr_browser")).thenReturn(active);
        when(repository.browserScene("tplver_1")).thenReturn(sceneRow);
        when(repository.events("mvr_browser")).thenReturn(Collections.emptyList());
        when(aiMusicJobs.ownedCandidate("usr_owner", "song_1")).thenReturn(candidate());

        ApiException error = assertThrows(ApiException.class,
                () -> service.get("usr_owner", "mvr_browser"));

        assertEquals("MV_BROWSER_SCENE_EXPORT_INCOMPLETE", error.getCode());
        Map<String, Object> nativeScene = BrowserNativeSceneCapabilitiesTest.fixture();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "requireBrowserSceneExportReady", nativeScene);
        ((Map<String, Object>) nativeScene.get("capability")).put("resourceDiagnostics", Collections.emptyList());
        assertThrows(ApiException.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "requireBrowserSceneExportReady", nativeScene));
    }

    @Test
    void completesBrowserOutputWithoutInventingRuntimeEvidence() throws Exception {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderArtifactStorageService artifacts = mock(MusicMvRenderArtifactStorageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                mock(AiMusicJobRepository.class), artifacts, inputAssets(),
                new ObjectMapper(), true, 2);
        String sha256 = repeat('e');
        Map<String, Object> active = row("mvr_browser", null);
        active.put("client_id", "usr_owner");
        active.put("status", "uploading");
        active.put("stage", "browser_output_uploading");
        Map<String, Object> completed = new LinkedHashMap<String, Object>(active);
        completed.put("status", "completed");
        completed.put("stage", "completed");
        completed.put("semantic_integrity", "unverified");
        completed.put("result_json", "{\"status\":\"completed\",\"renderMode\":\"browser\"}");
        when(repository.byId("mvr_browser")).thenReturn(active);
        when(repository.activeBrowserAttempt("mvr_browser", "usr_owner", "bratt_1",
                "brlease_1")).thenReturn(active);
        when(artifacts.verifyBrowserUpload("mvr_browser", "bratt_1", 1234L,
                "video/mp4", sha256))
                .thenReturn(new MusicMvRenderArtifactStorageService.StoredArtifact(
                        "r2:music-mv-renders/mvr_browser/attempts/bratt_1/result.mp4",
                        1234L, sha256, "video/mp4"));
        when(repository.completeBrowser(eq("mvr_browser"), eq("usr_owner"),
                eq("bratt_1"), eq("brlease_1"), anyString(), eq("video/mp4"),
                eq(1234L), eq(sha256), eq(180.0d), anyString(), anyString()))
                .thenReturn(completed);
        BrowserRenderOutputRequest request = new BrowserRenderOutputRequest();
        request.setAttemptId("bratt_1");
        request.setLeaseToken("brlease_1");
        request.setSha256(sha256);
        request.setSizeBytes(Long.valueOf(1234L));
        request.setContentType("video/mp4");
        request.setDurationSeconds(Double.valueOf(180.0d));

        Map<String, Object> result = service.completeBrowserOutput(
                "usr_owner", "mvr_browser", request);

        assertEquals("browser", result.get("renderMode"));
        assertEquals("unverified", result.get("semanticIntegrity"));
        assertEquals(null, result.get("videoEncodeCount"));
        assertEquals(null, result.get("intermediateVideoCount"));
        assertEquals(null, result.get("writerSidecarCount"));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> evidence = ArgumentCaptor.forClass(String.class);
        verify(repository).completeBrowser(eq("mvr_browser"), eq("usr_owner"),
                eq("bratt_1"), eq("brlease_1"), anyString(), eq("video/mp4"),
                eq(1234L), eq(sha256), eq(180.0d), payload.capture(), evidence.capture());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("unverified", mapper.readTree(payload.getValue()).path("semanticIntegrity").asText());
        com.fasterxml.jackson.databind.JsonNode saved = mapper.readTree(evidence.getValue());
        assertEquals("missing_runtime_evidence", saved.path("verificationStatus").asText());
        assertEquals(false, saved.has("videoEncodeCount"));
        assertEquals(false, saved.has("materializedIntermediateVideoCount"));
        assertEquals(sha256, saved.path("outputSha256").asText());
    }

    @Test
    void persistsClientTelemetryWithoutPromotingSemanticTrust() throws Exception {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderArtifactStorageService artifacts = mock(MusicMvRenderArtifactStorageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                mock(AiMusicJobRepository.class), artifacts, inputAssets(),
                new ObjectMapper(), true, 2);
        String sha256 = repeat('e');
        Map<String, Object> active = row("mvr_browser", null);
        active.put("client_id", "usr_owner");
        active.put("status", "uploading");
        active.put("stage", "browser_output_uploading");
        Map<String, Object> completed = new LinkedHashMap<String, Object>(active);
        completed.put("status", "completed");
        completed.put("stage", "completed");
        completed.put("semantic_integrity", "unverified");
        completed.put("result_json", "{\"status\":\"completed\",\"renderMode\":\"browser\"}");
        when(repository.byId("mvr_browser")).thenReturn(active);
        when(repository.activeBrowserAttempt("mvr_browser", "usr_owner", "bratt_1",
                "brlease_1")).thenReturn(active);
        when(artifacts.verifyBrowserUpload("mvr_browser", "bratt_1", 1234L,
                "video/mp4", sha256))
                .thenReturn(new MusicMvRenderArtifactStorageService.StoredArtifact(
                        "r2:music-mv-renders/mvr_browser/attempts/bratt_1/result.mp4",
                        1234L, sha256, "video/mp4"));
        when(repository.completeBrowser(eq("mvr_browser"), eq("usr_owner"),
                eq("bratt_1"), eq("brlease_1"), anyString(), eq("video/mp4"),
                eq(1234L), eq(sha256), eq(180.0d), anyString(), anyString()))
                .thenReturn(completed);
        BrowserRenderOutputRequest request = new BrowserRenderOutputRequest();
        request.setAttemptId("bratt_1");
        request.setLeaseToken("brlease_1");
        request.setSha256(sha256);
        request.setSizeBytes(Long.valueOf(1234L));
        request.setContentType("video/mp4");
        request.setDurationSeconds(Double.valueOf(180.0d));

        com.example.cursorquitterweb.musicmv.dto.BrowserRenderTelemetry telemetry = new com.example.cursorquitterweb.musicmv.dto.BrowserRenderTelemetry();
        telemetry.setSemanticIntegrity("exact");telemetry.setVideoEncodeCount(1);telemetry.setMaterializedIntermediateVideoCount(0);
        telemetry.setCompletedFrameCount(5400);telemetry.setElapsedSeconds(145.0);
        request.setRenderValidation(telemetry);request.setRendererFingerprint(repeat('a'));
        Map<String, Object> result = service.completeBrowserOutput(
                "usr_owner", "mvr_browser", request);

        assertEquals("browser", result.get("renderMode"));
        assertEquals("unverified", result.get("semanticIntegrity"));
        assertEquals(null, result.get("videoEncodeCount"));
        assertEquals(null, result.get("intermediateVideoCount"));
        assertEquals(null, result.get("writerSidecarCount"));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> evidence = ArgumentCaptor.forClass(String.class);
        verify(repository).completeBrowser(eq("mvr_browser"), eq("usr_owner"),
                eq("bratt_1"), eq("brlease_1"), anyString(), eq("video/mp4"),
                eq(1234L), eq(sha256), eq(180.0d), payload.capture(), evidence.capture());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("unverified", mapper.readTree(payload.getValue()).path("semanticIntegrity").asText());
        com.fasterxml.jackson.databind.JsonNode saved = mapper.readTree(evidence.getValue());
        assertEquals("client_reported_runtime_evidence", saved.path("verificationStatus").asText());
        assertEquals(145.0, saved.path("renderValidation").path("elapsedSeconds").asDouble());
        assertEquals(5400, mapper.readTree(payload.getValue()).path("renderValidation").path("completedFrameCount").asInt());
        assertEquals(repeat('a'), saved.path("rendererFingerprint").asText());
        assertEquals(false, saved.has("videoEncodeCount"));
        assertEquals(false, saved.has("materializedIntermediateVideoCount"));
        assertEquals(sha256, saved.path("outputSha256").asText());
    }

    @Test
    void grantsOnlyOneActiveBrowserAttempt() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderArtifactStorageService artifacts = mock(MusicMvRenderArtifactStorageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                mock(AiMusicJobRepository.class), artifacts,
                inputAssets(), new ObjectMapper(), true, 2);
        Map<String, Object> ready = row("mvr_browser", null);
        ready.put("client_id", "usr_owner");
        Map<String, Object> active = new LinkedHashMap<String, Object>(ready);
        active.put("status", "rendering");
        active.put("stage", "browser_loading_media");
        when(repository.byId("mvr_browser")).thenReturn(ready);
        when(repository.startBrowser(eq("mvr_browser"), eq("usr_owner"), anyString(),
                anyString(), eq(86400))).thenReturn(active).thenReturn(null);
        BrowserRenderAttemptStartRequest request = new BrowserRenderAttemptStartRequest();
        request.setSessionId("brsession_1");

        Map<String, Object> first = service.startBrowser("usr_owner", "mvr_browser", request);
        ApiException second = assertThrows(ApiException.class,
                () -> service.startBrowser("usr_owner", "mvr_browser", request));

        assertEquals("rendering", ((Map<?, ?>) first.get("job")).get("status"));
        assertEquals(Boolean.TRUE, String.valueOf(first.get("attemptId")).startsWith("bratt_"));
        assertEquals("MV_BROWSER_RENDER_ALREADY_ACTIVE", second.getCode());
        verify(artifacts, never()).clearLocalBrowserOutputs();
    }

    @Test
    void interruptionRequiresTheOwningAttemptAndDeletesOnlyItsArtifact() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderArtifactStorageService artifacts = mock(MusicMvRenderArtifactStorageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                mock(AiMusicJobRepository.class), artifacts, inputAssets(),
                new ObjectMapper(), true, 2);
        Map<String, Object> active = row("mvr_browser", null);
        active.put("client_id", "usr_owner");
        active.put("status", "rendering");
        active.put("stage", "browser_encoding");
        Map<String, Object> interrupted = new LinkedHashMap<String, Object>(active);
        interrupted.put("status", "interrupted");
        interrupted.put("stage", "browser_interrupted");
        when(repository.byId("mvr_browser")).thenReturn(active);
        when(repository.failBrowser("mvr_browser", "usr_owner", "bratt_1", "brlease_1",
                "MV_BROWSER_RENDER_FAILED", "tab hidden")).thenReturn(interrupted);
        BrowserRenderFailureRequest request = new BrowserRenderFailureRequest();
        request.setAttemptId("bratt_1");
        request.setLeaseToken("brlease_1");
        request.setMessage("tab hidden");

        Map<String, Object> result = service.failBrowser("usr_owner", "mvr_browser", request);

        assertEquals("interrupted", result.get("status"));
        verify(artifacts).deleteBrowserAttempt("mvr_browser", "bratt_1");
    }

    @Test
    void deletesOwnedCompletedProjectAndItsRenderedOutput() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderArtifactStorageService artifacts = mock(MusicMvRenderArtifactStorageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                mock(AiMusicJobRepository.class), artifacts, inputAssets(),
                new ObjectMapper(), true, 2);
        Map<String, Object> completed = row("mvr_completed", null);
        completed.put("client_id", "usr_owner");
        completed.put("status", "completed");
        completed.put("output_storage_key", "r2:music-mv-renders/mvr_completed.mp4");
        when(repository.byId("mvr_completed"))
                .thenReturn(completed)
                .thenReturn(null);

        Map<String, Object> deleted = service.delete("usr_owner", "mvr_completed");

        assertEquals(Boolean.TRUE, deleted.get("deleted"));
        verify(artifacts).delete("r2:music-mv-renders/mvr_completed.mp4");
        verify(repository).deleteOwnedTerminal("mvr_completed", "usr_owner");
    }

    @Test
    void refusesToDeleteActiveRenderProject() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderArtifactStorageService artifacts = mock(MusicMvRenderArtifactStorageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                mock(AiMusicJobRepository.class), artifacts, inputAssets(),
                new ObjectMapper(), true, 2);
        Map<String, Object> rendering = row("mvr_rendering", null);
        rendering.put("client_id", "usr_owner");
        rendering.put("status", "rendering");
        when(repository.byId("mvr_rendering")).thenReturn(rendering);

        ApiException error = assertThrows(ApiException.class,
                () -> service.delete("usr_owner", "mvr_rendering"));

        assertEquals("MV_RENDER_DELETE_ACTIVE", error.getCode());
        verify(repository, never()).deleteOwnedTerminal(anyString(), anyString());
        verify(artifacts, never()).delete(anyString());
    }

    @Test
    void usesTheRequestedSafeFileNameForTheFinishedDownload() throws Exception {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderArtifactStorageService artifacts = mock(MusicMvRenderArtifactStorageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                mock(AiMusicJobRepository.class), artifacts, inputAssets(),
                new ObjectMapper(), true, 2);
        Map<String, Object> completed = row("mvr_completed", null);
        completed.put("client_id", "usr_owner");
        completed.put("status", "completed");
        completed.put("output_storage_key", "r2:music-mv-renders/mvr_completed.mp4");
        completed.put("output_size_bytes", Long.valueOf(4L));
        completed.put("output_content_type", "video/mp4");
        completed.put("request_json", "{\"outputFileName\":\"Family: story.mp4\"}");
        when(repository.byId("mvr_completed")).thenReturn(completed);
        when(artifacts.exists("r2:music-mv-renders/mvr_completed.mp4")).thenReturn(true);

        MusicMvRenderJobService.OutputAccess output = service.output("usr_owner", "mvr_completed");

        assertEquals("Family__story.mp4", output.getFileName());
        output.temporaryDownloadUrl(false);
        verify(artifacts).temporaryDownloadUrl("r2:music-mv-renders/mvr_completed.mp4",
                false, "Family__story.mp4");
    }

    private MusicMvRenderJobCreateRequest request() {
        MusicMvRenderJobCreateRequest request = new MusicMvRenderJobCreateRequest();
        request.setRequestId("req_1");
        request.setTemplateId("tpl_1");
        request.setTemplateVersionId("tplver_1");
        request.setMusicCandidateId("song_1");
        request.setMusic(asset("http://127.0.0.1:8080/uploads/music.m4a", "music.m4a", "audio/mp4", 100L, 'a'));
        request.setSlotBindings(Arrays.asList(
                binding("photo_01", asset("http://127.0.0.1:8080/uploads/1.jpg", "1.jpg", "image/jpeg", 50L, 'b')),
                binding("photo_02", asset("http://127.0.0.1:8080/uploads/2.jpg", "2.jpg", "image/jpeg", 60L, 'c'))));
        request.setAllowTemplateLoop(Boolean.TRUE);
        request.setFadeOutSeconds(Double.valueOf(0.0d));
        return request;
    }

    private MusicMvRenderJobCreateRequest.OutputVideo outputVideo(
            int width, int height, int fps, String quality, String format) {
        MusicMvRenderJobCreateRequest.OutputVideo output =
                new MusicMvRenderJobCreateRequest.OutputVideo();
        output.setWidth(Integer.valueOf(width));
        output.setHeight(Integer.valueOf(height));
        output.setFps(Integer.valueOf(fps));
        output.setQuality(quality);
        output.setFormat(format);
        return output;
    }

    private void assertInvalidOutput(MusicMvRenderJobCreateRequest.OutputVideo output,
                                     String expectedCode) {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                mock(AiMusicJobRepository.class), mock(MusicMvRenderArtifactStorageService.class),
                inputAssets(), new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        request.setOutputVideo(output);

        ApiException error = assertThrows(ApiException.class,
                () -> service.create("website-backend", request));

        assertEquals(expectedCode, error.getCode());
        verify(repository, never()).createBrowserPreparing(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private MusicMvInputAssetStorageService inputAssets() {
        return mock(MusicMvInputAssetStorageService.class);
    }

    private MusicMvRenderJobCreateRequest.Asset asset(String url, String name,
                                                        String type, long size, char hash) {
        MusicMvRenderJobCreateRequest.Asset asset = new MusicMvRenderJobCreateRequest.Asset();
        asset.setUrl(url);
        asset.setFileName(name);
        asset.setContentType(type);
        asset.setSizeBytes(Long.valueOf(size));
        asset.setSha256(repeat(hash));
        return asset;
    }

    private MusicMvRenderJobCreateRequest.SlotBinding binding(
            String key, MusicMvRenderJobCreateRequest.Asset asset) {
        MusicMvRenderJobCreateRequest.SlotBinding binding = new MusicMvRenderJobCreateRequest.SlotBinding();
        binding.setSlotKey(key);
        binding.setAsset(asset);
        MusicMvRenderJobCreateRequest.Crop crop = new MusicMvRenderJobCreateRequest.Crop();
        crop.setX(Double.valueOf(50.0d));
        crop.setY(Double.valueOf(50.0d));
        crop.setZoom(Double.valueOf(1.0d));
        binding.setCrop(crop);
        return binding;
    }

    private Map<String, Object> version() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("template_id", "tpl_1");
        row.put("version_id", "tplver_1");
        row.put("template_status", "published");
        row.put("version_status", "published");
        row.put("validation_status", "browser_ready");
        row.put("source_availability", "available");
        row.put("current_version_id", "tplver_1");
        row.put("source_node_id", "mac-music-mv-primary");
        row.put("browser_scene_status", "ready");
        row.put("width", Integer.valueOf(1080));
        row.put("height", Integer.valueOf(1920));
        row.put("fps", Integer.valueOf(30));
        return row;
    }

    private Map<String, Object> candidate() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("candidate_id", "song_1");
        row.put("status", "stored");
        row.put("storage_url", "http://127.0.0.1:8080/uploads/music.m4a");
        row.put("storage_sha256", repeat('a'));
        row.put("storage_size_bytes", Long.valueOf(100L));
        row.put("storage_file_name", "music.m4a");
        row.put("storage_content_type", "audio/mp4");
        return row;
    }

    private Map<String, Object> browserCapability(boolean ready, List<String> blockingFeatures) {
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("browserExportReady", Boolean.valueOf(ready));
        capability.put("blockingFeatures", blockingFeatures);
        return capability;
    }

    private Map<String, Object> slot(String key) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("slot_key", key);
        row.put("slot_type", "image");
        return row;
    }

    private Map<String, Object> row(String jobId, String fingerprint) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("job_id", jobId);
        row.put("status", "ready");
        row.put("stage", "browser_ready");
        row.put("version_id", "tplver_1");
        row.put("request_fingerprint", fingerprint);
        return row;
    }

    private Map<String, Object> preparingRow(String jobId,
                                             MusicMvRenderJobCreateRequest request) {
        Map<String, Object> row = row(jobId, null);
        row.put("client_id", "website-backend");
        row.put("status", "preparing");
        row.put("stage", "preparing_music");
        row.put("request_json", json(request));
        return row;
    }

    private String repeat(char value) {
        char[] chars = new char[64];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private String json(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
