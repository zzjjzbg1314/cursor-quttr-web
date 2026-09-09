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

class MusicMvDefaultPhotoCropTest {
    @Test
    @SuppressWarnings("unchecked")
    void returnsDefaultPhotoCropInTheBrowserRenderContract() {
        MusicMvRenderJobRepository repository = mock(MusicMvRenderJobRepository.class);
        AiMusicJobRepository aiMusicJobs = mock(AiMusicJobRepository.class);
        MusicMvRenderJobService service = service(repository, aiMusicJobs);
        Map<String, Object> active = row("mvr_text");
        active.put("client_id", "owner");
        active.put("template_id", "tpl_1");
        active.put("request_json", "{\"musicCandidateId\":\"song_1\",\"music\":{},"
                + "\"slotBindings\":[{\"slotKey\":\"photo_01\",\"useTemplateDefault\":true,\"crop\":{\"x\":75,\"y\":25,\"zoom\":1.5}}]}");
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

        Map<String,Object> media=new LinkedHashMap<String,Object>();
        media.put("provider_details_json","{}");media.put("status","ready");media.put("provider","r2");media.put("provider_asset_id","photo");
        when(repository.slotDefaultMedia("tplver_1","photo_01")).thenReturn(media);
        Map<String, Object> result = service.get("owner", "mvr_text");
        Map<String, Object> browserRender = (Map<String, Object>) result.get("browserRender");
        Map<String,Object> binding=(Map<String,Object>)((java.util.List<?>)browserRender.get("slotBindings")).get(0);
        Map<String,Object> crop=(Map<String,Object>)binding.get("crop");
        assertEquals(75,((Number)crop.get("x")).intValue());
        assertEquals(25,((Number)crop.get("y")).intValue());
        assertEquals(1.5d,((Number)crop.get("zoom")).doubleValue());
        assertEquals(Boolean.TRUE,binding.get("useTemplateDefault"));
        assertEquals("https://media.storyai.test/photo.png",((Map<?,?>)binding.get("asset")).get("url"));
    }

    private MusicMvRenderJobService service(MusicMvRenderJobRepository repository,
                                             AiMusicJobRepository aiMusicJobs) {
        CloudflareTemplateMediaProvider media=mock(CloudflareTemplateMediaProvider.class);
        when(media.resolveDeliveryDetails(eq("r2"),eq("photo"),org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Collections.<String,Object>singletonMap("deliveryUrl","https://media.storyai.test/photo.png"));
        return new MusicMvRenderJobService(repository, aiMusicJobs,
                mock(MusicMvRenderArtifactStorageService.class),
                mock(MusicMvInputAssetStorageService.class),
                media, new ObjectMapper(), true, 2);
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
