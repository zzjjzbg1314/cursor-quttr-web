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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.dto.BrowserRenderOutputRequest;
import com.example.cursorquitterweb.musicmv.dto.BrowserRenderAttemptStartRequest;
import com.example.cursorquitterweb.musicmv.dto.BrowserRenderFailureRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderCompleteRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository.RenderContract;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicMvRenderJobServiceTest {
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
    void rejectsCompletionWithoutSingleExactNativeEncode() {
        MusicMvRenderJobService service = new MusicMvRenderJobService(
                mock(MusicMvRenderJobRepository.class),
                mock(AiMusicJobRepository.class),
                mock(MusicMvRenderArtifactStorageService.class), inputAssets(),
                new ObjectMapper(), false, 2);
        MusicMvRenderCompleteRequest request = new MusicMvRenderCompleteRequest();
        request.setSemanticIntegrity("degraded");
        request.setVideoEncodeCount(Integer.valueOf(1));
        request.setIntermediateVideoCount(Integer.valueOf(0));
        request.setWriterSidecarCount(Integer.valueOf(0));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.complete("mvr_1", request));
        assertEquals("MV_RENDER_NATIVE_EVIDENCE_NOT_EXACT", exception.getCode());
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
    @SuppressWarnings("unchecked")
    void publishesAnExplicitOfficialPreviewLoopClock() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        CloudflareTemplateMediaProvider templateMedia = mock(CloudflareTemplateMediaProvider.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository, aiMusicJobs,
                mock(MusicMvRenderArtifactStorageService.class), inputAssets(), templateMedia,
                new ObjectMapper(), true, 2);
        Map<String, Object> active = row("mvr_browser", null);
        active.put("client_id", "usr_owner");
        active.put("request_json", "{\"musicCandidateId\":\"song_1\",\"music\":{},\"slotBindings\":[]}");
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("canvas", Collections.singletonMap("durationSeconds", Double.valueOf(30.633d)));
        Map<String, Object> sceneRow = new LinkedHashMap<String, Object>();
        sceneRow.put("status", "ready");
        sceneRow.put("scene_json", json(scene));
        Map<String, Object> media = new LinkedHashMap<String, Object>();
        media.put("status", "ready");
        media.put("provider", "cloudflare_stream");
        media.put("provider_asset_id", "stream_1");
        media.put("duration_seconds", Double.valueOf(26.633d));
        media.put("provider_details_json",
                "{\"sourceType\":\"capcut_official_template_preview\"}");
        when(repository.byId("mvr_browser")).thenReturn(active);
        when(repository.browserScene("tplver_1")).thenReturn(sceneRow);
        when(repository.fullMvMedia("tplver_1")).thenReturn(media);
        when(repository.events("mvr_browser")).thenReturn(Collections.emptyList());
        when(aiMusicJobs.ownedCandidate("usr_owner", "song_1")).thenReturn(candidate());
        when(templateMedia.resolveDeliveryDetails(eq("cloudflare_stream"), eq("stream_1"),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(Collections.<String, Object>singletonMap(
                        "playbackUrl", "https://stream.example/video.m3u8"));

        Map<String, Object> result = service.get("usr_owner", "mvr_browser");
        Map<String, Object> browserRender = (Map<String, Object>) result.get("browserRender");
        Map<String, Object> sourceVideo = (Map<String, Object>) browserRender.get("sourceVideo");

        assertEquals(Double.valueOf(26.633d), sourceVideo.get("durationSeconds"));
        assertEquals(Double.valueOf(26.633d), sourceVideo.get("loopDurationSeconds"));
        assertEquals(Double.valueOf(0.0d), sourceVideo.get("loopStartSeconds"));
    }

    @Test
    void completesBrowserOutputAsExactSingleEncode() {
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
        completed.put("semantic_integrity", "exact");
        completed.put("video_encode_count", Integer.valueOf(1));
        completed.put("intermediate_video_count", Integer.valueOf(0));
        completed.put("writer_sidecar_count", Integer.valueOf(0));
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
        assertEquals("exact", result.get("semanticIntegrity"));
        assertEquals(Integer.valueOf(1), result.get("videoEncodeCount"));
        assertEquals(Integer.valueOf(0), result.get("intermediateVideoCount"));
        assertEquals(Integer.valueOf(0), result.get("writerSidecarCount"));
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
        verify(artifacts).clearLocalBrowserOutputs();
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
        row.put("template_status", "published");
        row.put("version_status", "published");
        row.put("validation_status", "exact");
        row.put("source_availability", "available");
        row.put("current_version_id", "tplver_1");
        row.put("source_node_id", "mac-music-mv-primary");
        row.put("browser_scene_status", "ready");
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
