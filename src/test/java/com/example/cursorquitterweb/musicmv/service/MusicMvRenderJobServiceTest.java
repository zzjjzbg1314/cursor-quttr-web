package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderCompleteRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
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
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());
        when(repository.renderableVersion("tpl_1", "tplver_1")).thenReturn(version());
        when(repository.slots("tplver_1")).thenReturn(Arrays.asList(slot("photo_01"), slot("photo_02")));
        when(repository.byId(anyString())).thenAnswer(invocation -> row(invocation.getArgument(0), null));

        Map<String, Object> created = service.create("website-backend", request);

        assertEquals("rendering", created.get("status"));
        assertEquals("browser_ready", created.get("stage"));
        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        verify(repository).createBrowser(anyString(), eq("website-backend"), eq("req_1"),
                eq("tpl_1"), eq("tplver_1"), fingerprint.capture(), anyString());

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
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());
        request.setSlotBindings(Collections.singletonList(request.getSlotBindings().get(0)));
        when(repository.renderableVersion("tpl_1", "tplver_1")).thenReturn(version());
        when(repository.slots("tplver_1")).thenReturn(Arrays.asList(slot("photo_01"), slot("photo_02")));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create("website-backend", request));
        assertEquals("MV_RENDER_SLOT_BINDINGS_INCOMPLETE", exception.getCode());
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
        when(repository.renderableVersion("tpl_1", "tplver_1")).thenReturn(version());
        when(repository.slots("tplver_1")).thenReturn(Arrays.asList(
                slot("photo_01"), slot("photo_02")));
        when(repository.slotDefaultMedia("tplver_1", "photo_01"))
                .thenReturn(row("media_template_photo", null));
        Map<String, Object> defaultMedia = repository.slotDefaultMedia("tplver_1", "photo_01");
        defaultMedia.put("status", "ready");
        when(repository.byId(anyString())).thenAnswer(invocation -> row(invocation.getArgument(0), null));

        assertEquals("rendering", service.create("website-backend", request).get("status"));
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
        when(repository.renderableVersion("tpl_1", "tplver_1")).thenReturn(version());
        when(repository.slots("tplver_1")).thenReturn(Arrays.asList(slot("photo_01"), slot("photo_02")));
        when(repository.byId(anyString())).thenAnswer(invocation -> row(invocation.getArgument(0), null));

        assertEquals("rendering", service.create("website-backend", request).get("status"));
    }

    @Test
    void rejectsUnprotectedLocalInputWhenGeneralLoopbackIsDisabled() {
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(
                mock(MusicMvRenderJobRepository.class),
                aiMusicJobs,
                mock(MusicMvRenderArtifactStorageService.class), inputAssets(),
                new ObjectMapper(), false, 2);
        when(aiMusicJobs.ownedCandidate("website-backend", "song_1")).thenReturn(candidate());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create("website-backend", request()));
        assertEquals("MV_RENDER_ASSET_URL_BLOCKED", exception.getCode());
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
        when(repository.renderableVersion("tpl_1", "tplver_1")).thenReturn(offlineVersion);
        when(repository.slots("tplver_1"))
                .thenReturn(Arrays.asList(slot("photo_01"), slot("photo_02")));
        when(repository.byId(anyString())).thenAnswer(invocation -> row(invocation.getArgument(0), null));

        Map<String, Object> created = service.create("website-backend", request());

        assertEquals("browser_ready", created.get("stage"));
        verify(repository).createBrowser(anyString(), eq("website-backend"), eq("req_1"),
                eq("tpl_1"), eq("tplver_1"), anyString(), anyString());
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
        Map<String, Object> completed = new LinkedHashMap<String, Object>(active);
        completed.put("status", "completed");
        completed.put("stage", "completed");
        completed.put("semantic_integrity", "exact");
        completed.put("video_encode_count", Integer.valueOf(1));
        completed.put("intermediate_video_count", Integer.valueOf(0));
        completed.put("writer_sidecar_count", Integer.valueOf(0));
        completed.put("result_json", "{\"status\":\"completed\",\"renderMode\":\"browser\"}");
        when(repository.byId("mvr_browser")).thenReturn(active);
        when(artifacts.verifyBrowserUpload("mvr_browser", 1234L, "video/mp4", sha256))
                .thenReturn(new MusicMvRenderArtifactStorageService.StoredArtifact(
                        "r2:music-mv-renders/mvr_browser/result.mp4", 1234L, sha256, "video/mp4"));
        when(repository.completeBrowser(eq("mvr_browser"), eq("usr_owner"), anyString(),
                eq("video/mp4"), eq(1234L), eq(sha256), eq(180.0d), anyString(), anyString()))
                .thenReturn(completed);
        BrowserRenderOutputRequest request = new BrowserRenderOutputRequest();
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
        row.put("status", "rendering");
        row.put("stage", "browser_ready");
        row.put("version_id", "tplver_1");
        row.put("request_fingerprint", fingerprint);
        return row;
    }

    private String repeat(char value) {
        char[] chars = new char[64];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
