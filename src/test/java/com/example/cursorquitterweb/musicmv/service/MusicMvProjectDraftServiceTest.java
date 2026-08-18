package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.cursorquitterweb.musicmv.dto.MusicMvProjectDraftRequest;
import com.example.cursorquitterweb.musicmv.dto.MusicMvProjectDraftRequest.ProjectAsset;
import com.example.cursorquitterweb.musicmv.repository.MusicMvProjectDraftRepository;
import com.example.cursorquitterweb.musicmv.repository.MusicMvUserAssetRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class MusicMvProjectDraftServiceTest {

    @Test
    void savesProjectAndAssetSnapshotWithRevisionProtection() {
        MusicMvProjectDraftRepository projects = mock(MusicMvProjectDraftRepository.class);
        MusicMvUserAssetRepository assets = mock(MusicMvUserAssetRepository.class);
        MusicMvProjectDraftService service = new MusicMvProjectDraftService(
                projects, assets, new ObjectMapper());
        MusicMvProjectDraftRequest request = request(3);
        when(projects.saveSnapshot(eq("usr_owner"), eq("mvp_project_123"), eq("My video"),
                eq("draft"), eq("photos"), eq("candidate_123"), eq("template_123"),
                eq("version_123"), anyString(), eq(3), anyString(), anyList(), anyList()))
                .thenReturn(true);
        when(projects.findOwned("usr_owner", "mvp_project_123")).thenReturn(projectRow(3));
        when(projects.listAssets("mvp_project_123")).thenReturn(Collections.emptyList());

        Map<String, Object> saved = service.save("usr_owner", "mvp_project_123", request);

        assertEquals(Integer.valueOf(3), saved.get("revision"));
        verify(projects).saveSnapshot(eq("usr_owner"), eq("mvp_project_123"), eq("My video"),
                eq("draft"), eq("photos"), eq("candidate_123"), eq("template_123"),
                eq("version_123"), anyString(), eq(3), anyString(), anyList(), anyList());
    }

    @Test
    void rejectsStaleRevisionWithoutTouchingAssets() {
        MusicMvProjectDraftRepository projects = mock(MusicMvProjectDraftRepository.class);
        MusicMvUserAssetRepository assets = mock(MusicMvUserAssetRepository.class);
        MusicMvProjectDraftService service = new MusicMvProjectDraftService(
                projects, assets, new ObjectMapper());
        when(projects.saveSnapshot(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(),
                anyString(), anyList(), anyList())).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.save("usr_owner", "mvp_project_123", request(2)));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("MUSIC_MV_PROJECT_REVISION_CONFLICT", error.getCode());
        verify(assets, never()).touch(anyString(), anyString());
    }

    @Test
    void restoresCloudAssetMetadataAndCropForAnotherDevice() throws Exception {
        MusicMvProjectDraftRepository projects = mock(MusicMvProjectDraftRepository.class);
        MusicMvUserAssetRepository assets = mock(MusicMvUserAssetRepository.class);
        MusicMvProjectDraftService service = new MusicMvProjectDraftService(
                projects, assets, new ObjectMapper());
        Map<String, Object> project = projectRow(4);
        project.put("draft_json", "{\"selectedTemplate\":\"template_123\"}");
        Map<String, Object> asset = new LinkedHashMap<String, Object>();
        asset.put("asset_id", "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        asset.put("slot_key", "photo-1");
        asset.put("timeline_order", Integer.valueOf(0));
        asset.put("crop_json", "{\"x\":0.25,\"y\":0.75,\"zoom\":1.4}");
        asset.put("asset_url", "https://api.example/assets/photo?access=token");
        asset.put("file_name", "family.jpg");
        asset.put("content_type", "image/jpeg");
        asset.put("size_bytes", Long.valueOf(42L));
        asset.put("sha256", "hash");
        asset.put("expires_at", "2026-09-17T00:00:00Z");
        when(projects.findOwned("usr_owner", "mvp_project_123")).thenReturn(project);
        when(projects.listAssets("mvp_project_123"))
                .thenReturn(Collections.singletonList(asset));

        Map<String, Object> restored = service.get("usr_owner", "mvp_project_123");

        assertEquals(Integer.valueOf(4), restored.get("revision"));
        com.fasterxml.jackson.databind.JsonNode draft =
                (com.fasterxml.jackson.databind.JsonNode) restored.get("draft");
        assertEquals("template_123", draft.get("selectedTemplate").asText());
        java.util.List<?> restoredAssets = (java.util.List<?>) restored.get("assets");
        assertEquals(1, restoredAssets.size());
        Map<?, ?> restoredAsset = (Map<?, ?>) restoredAssets.get(0);
        assertEquals("photo-1", restoredAsset.get("slotKey"));
        assertEquals("family.jpg", restoredAsset.get("fileName"));
        com.fasterxml.jackson.databind.JsonNode crop =
                (com.fasterxml.jackson.databind.JsonNode) restoredAsset.get("crop");
        assertEquals(1.4d, crop.get("zoom").asDouble(), 0.0001d);
    }

    @Test
    void rejectsAssetThatDoesNotBelongToProjectOwner() {
        MusicMvProjectDraftRepository projects = mock(MusicMvProjectDraftRepository.class);
        MusicMvUserAssetRepository assets = mock(MusicMvUserAssetRepository.class);
        MusicMvProjectDraftService service = new MusicMvProjectDraftService(
                projects, assets, new ObjectMapper());
        MusicMvProjectDraftRequest request = request(1);
        ProjectAsset asset = new ProjectAsset();
        asset.setAssetId("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        asset.setSlotKey("photo-1");
        request.setAssets(Arrays.asList(asset));
        when(assets.findOwned("usr_owner", asset.getAssetId())).thenReturn(null);

        ApiException error = assertThrows(ApiException.class,
                () -> service.save("usr_owner", "mvp_project_123", request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("MUSIC_MV_PROJECT_ASSET_INVALID", error.getCode());
        verify(projects, never()).saveSnapshot(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyString(), anyList(), anyList());
    }

    private MusicMvProjectDraftRequest request(int revision) {
        MusicMvProjectDraftRequest request = new MusicMvProjectDraftRequest();
        request.setName("My video");
        request.setStatus("draft");
        request.setCurrentStep("photos");
        request.setSongCandidateId("candidate_123");
        request.setTemplateId("template_123");
        request.setTemplateVersionId("version_123");
        request.setRevision(Integer.valueOf(revision));
        return request;
    }

    private Map<String, Object> projectRow(int revision) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("project_id", "mvp_project_123");
        row.put("user_id", "usr_owner");
        row.put("name", "My video");
        row.put("status", "draft");
        row.put("current_step", "photos");
        row.put("song_candidate_id", "candidate_123");
        row.put("template_id", "template_123");
        row.put("template_version_id", "version_123");
        row.put("draft_json", "{}");
        row.put("revision", Integer.valueOf(revision));
        row.put("created_at", "2026-08-17 00:00:00");
        row.put("updated_at", "2026-08-17 00:00:01");
        return row;
    }
}
