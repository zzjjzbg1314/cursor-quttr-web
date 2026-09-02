package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvRenderJobRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicMvRenderTextOverrideTest {
    @Test
    void storesTextOverridesAndIncludesThemInIdempotency() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        MusicMvRenderJobService service = service(repository, mock(AiMusicJobRepository.class));
        MusicMvRenderJobCreateRequest request = request("Our family");

        service.create("website-backend", request);

        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> requestJson = ArgumentCaptor.forClass(String.class);
        verify(repository).createBrowserPreparing(anyString(), eq("website-backend"), eq("req_text"),
                eq("tpl_1"), eq("tplver_1"), fingerprint.capture(), requestJson.capture(),
                anyString(), anyString());
        assertEquals(true, requestJson.getValue().contains(
                "\"textOverrides\":{\"title\":\"Our family\"}"));

        Map<String, Object> existing = row("mvr_existing");
        existing.put("status", "completed");
        existing.put("stage", "completed");
        existing.put("request_fingerprint", fingerprint.getValue());
        when(repository.byClientRequest("website-backend", "req_text")).thenReturn(existing);

        request.setTextOverrides(Collections.singletonMap("title", "A different title"));
        ApiException conflict = assertThrows(ApiException.class,
                () -> service.create("website-backend", request));
        assertEquals("MV_RENDER_IDEMPOTENCY_CONFLICT", conflict.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsTextOverridesInTheBrowserRenderContract() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = service(repository, aiMusicJobs);
        Map<String, Object> active = row("mvr_text");
        active.put("client_id", "owner");
        active.put("template_id", "tpl_1");
        active.put("request_json", "{\"musicCandidateId\":\"song_1\",\"music\":{},"
                + "\"slotBindings\":[],\"textOverrides\":{\"title\":\"Our family\"}}");
        Map<String, Object> capability = new LinkedHashMap<String, Object>();
        capability.put("browserExportReady", Boolean.TRUE);
        capability.put("blockingFeatures", Collections.emptyList());
        Map<String, Object> scene = new LinkedHashMap<String, Object>();
        scene.put("canvas", Collections.singletonMap("durationSeconds", Double.valueOf(10.0d)));
        scene.put("capability", capability);
        Map<String, Object> sceneRow = new LinkedHashMap<String, Object>();
        sceneRow.put("status", "ready");
        sceneRow.put("scene_json", json(scene));
        sceneRow.put("manifest_sha256", "scene-hash");
        when(repository.byId("mvr_text")).thenReturn(active);
        when(repository.browserScene("tplver_1")).thenReturn(sceneRow);
        when(repository.events("mvr_text")).thenReturn(Collections.emptyList());
        when(aiMusicJobs.ownedCandidate("owner", "song_1")).thenReturn(candidate());

        Map<String, Object> result = service.get("owner", "mvr_text");
        Map<String, Object> browserRender = (Map<String, Object>) result.get("browserRender");
        assertEquals(Collections.singletonMap("title", "Our family"),
                browserRender.get("textOverrides"));
    }

    private MusicMvRenderJobService service(MusicMvRenderJobRepository repository,
                                             AiMusicJobRepository aiMusicJobs) {
        return new MusicMvRenderJobService(repository, aiMusicJobs,
                mock(MusicMvRenderArtifactStorageService.class),
                mock(MusicMvInputAssetStorageService.class),
                mock(CloudflareTemplateMediaProvider.class), new ObjectMapper(), true, 2);
    }

    private MusicMvRenderJobCreateRequest request(String title) {
        MusicMvRenderJobCreateRequest request = new MusicMvRenderJobCreateRequest();
        request.setRequestId("req_text");
        request.setTemplateId("tpl_1");
        request.setTemplateVersionId("tplver_1");
        request.setMusicCandidateId("song_1");
        request.setTextOverrides(Collections.singletonMap("title", title));
        request.setAllowTemplateLoop(Boolean.TRUE);
        request.setFadeOutSeconds(Double.valueOf(0.0d));
        return request;
    }

    private Map<String, Object> row(String jobId) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("job_id", jobId);
        row.put("status", "ready");
        row.put("stage", "browser_ready");
        row.put("version_id", "tplver_1");
        return row;
    }

    private Map<String, Object> candidate() {
        Map<String, Object> candidate = new LinkedHashMap<String, Object>();
        candidate.put("status", "stored");
        candidate.put("storage_url", "http://127.0.0.1:8080/music.m4a");
        candidate.put("storage_sha256", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        candidate.put("storage_size_bytes", Long.valueOf(100L));
        candidate.put("storage_file_name", "music.m4a");
        candidate.put("storage_content_type", "audio/mp4");
        return candidate;
    }

    private static String json(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
