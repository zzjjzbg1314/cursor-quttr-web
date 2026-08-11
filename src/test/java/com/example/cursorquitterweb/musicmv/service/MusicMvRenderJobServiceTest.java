package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderCompleteRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicMvRenderJobServiceTest {
    @Test
    void createsExactPublishedTemplateJobAndSupportsIdempotentReplay() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderArtifactStorageService artifacts = mock(MusicMvRenderArtifactStorageService.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(
                repository, artifacts, new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        when(repository.renderableVersion("tpl_1", "tplver_1")).thenReturn(version());
        when(repository.slots("tplver_1")).thenReturn(Arrays.asList(slot("photo_01"), slot("photo_02")));
        when(repository.byId(anyString())).thenAnswer(invocation -> row(invocation.getArgument(0), null));

        Map<String, Object> created = service.create("website-backend", request);

        assertEquals("queued", created.get("status"));
        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        verify(repository).create(anyString(), eq("website-backend"), eq("req_1"),
                eq("tpl_1"), eq("tplver_1"), eq(2), fingerprint.capture(),
                anyString(), eq("video/mp4"));

        when(repository.byClientRequest("website-backend", "req_1"))
                .thenReturn(row("mvr_existing", fingerprint.getValue()));
        Map<String, Object> replay = service.create("website-backend", request);
        assertEquals(Boolean.TRUE, replay.get("idempotentReplay"));
        assertEquals("mvr_existing", replay.get("jobId"));
    }

    @Test
    void rejectsIncompleteSlotContract() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderJobService service = new MusicMvRenderJobService(repository,
                mock(MusicMvRenderArtifactStorageService.class), new ObjectMapper(), true, 2);
        MusicMvRenderJobCreateRequest request = request();
        request.setSlotBindings(Collections.singletonList(request.getSlotBindings().get(0)));
        when(repository.renderableVersion("tpl_1", "tplver_1")).thenReturn(version());
        when(repository.slots("tplver_1")).thenReturn(Arrays.asList(slot("photo_01"), slot("photo_02")));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create("website-backend", request));
        assertEquals("MV_RENDER_SLOT_BINDINGS_INCOMPLETE", exception.getCode());
    }

    @Test
    void rejectsCompletionWithoutSingleExactNativeEncode() {
        MusicMvRenderJobService service = new MusicMvRenderJobService(
                mock(MusicMvRenderJobRepository.class),
                mock(MusicMvRenderArtifactStorageService.class), new ObjectMapper(), false, 2);
        MusicMvRenderCompleteRequest request = new MusicMvRenderCompleteRequest();
        request.setSemanticIntegrity("degraded");
        request.setVideoEncodeCount(Integer.valueOf(1));
        request.setIntermediateVideoCount(Integer.valueOf(0));
        request.setWriterSidecarCount(Integer.valueOf(0));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.complete("mvr_1", request));
        assertEquals("MV_RENDER_NATIVE_EVIDENCE_NOT_EXACT", exception.getCode());
    }

    private MusicMvRenderJobCreateRequest request() {
        MusicMvRenderJobCreateRequest request = new MusicMvRenderJobCreateRequest();
        request.setRequestId("req_1");
        request.setTemplateId("tpl_1");
        request.setTemplateVersionId("tplver_1");
        request.setMusic(asset("http://127.0.0.1:8080/uploads/music.m4a", "music.m4a", "audio/mp4", 100L, 'a'));
        request.setSlotBindings(Arrays.asList(
                binding("photo_01", asset("http://127.0.0.1:8080/uploads/1.jpg", "1.jpg", "image/jpeg", 50L, 'b')),
                binding("photo_02", asset("http://127.0.0.1:8080/uploads/2.jpg", "2.jpg", "image/jpeg", 60L, 'c'))));
        request.setAllowTemplateLoop(Boolean.TRUE);
        request.setFadeOutSeconds(Double.valueOf(0.0d));
        return request;
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
        row.put("status", "queued");
        row.put("request_fingerprint", fingerprint);
        return row;
    }

    private String repeat(char value) {
        char[] chars = new char[64];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
