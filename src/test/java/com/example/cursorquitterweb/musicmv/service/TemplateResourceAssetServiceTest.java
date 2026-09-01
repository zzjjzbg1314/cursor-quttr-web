package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cursorquitterweb.musicmv.dto.TemplateResourceAssetUploadRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplateResourceAssetServiceTest {
    private MusicMvTemplateCatalogRepository repository;
    private R2StorageService r2;
    private TemplateResourceAssetService service;

    @BeforeEach
    void setUp() {
        repository = mock(MusicMvTemplateCatalogRepository.class);
        r2 = mock(R2StorageService.class);
        service = new TemplateResourceAssetService(repository, r2);
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(Collections.<String, Object>singletonMap("version_id", "tplver_1"));
        when(r2.isConfigured()).thenReturn(Boolean.TRUE);
    }

    @Test
    void createsContentAddressedUploadAndVersionReference() {
        when(repository.templateResourceAsset(resourceId(), hash())).thenReturn(null);
        when(r2.presignedPutUrl(eq(objectKey()), eq("application/zip"), eq(1234L),
                anyMap(), eq(Duration.ofMinutes(30))))
                .thenReturn("https://upload.example/resource");

        Map<String, Object> result = service.createUploadSession(
                "tpl_1", "tplver_1", resourceId(), request());

        assertEquals("awaiting_upload", result.get("status"));
        assertEquals(Boolean.FALSE, result.get("reused"));
        assertEquals("https://upload.example/resource", result.get("uploadUrl"));
        verify(repository).upsertTemplateResourceAsset(resourceId(), hash(), objectKey(),
                1234L, "application/zip", "awaiting_upload");
        verify(repository).upsertTemplateVersionResourceRef(
                "tpl_1", "tplver_1", resourceId(), hash(), "fonts");
    }

    @Test
    void reusesReadyResourceAcrossTemplateVersionsWithoutUploadSession() {
        Map<String, Object> ready = row("ready");
        when(repository.templateResourceAsset(resourceId(), hash())).thenReturn(ready);
        when(r2.objectInfo(objectKey())).thenReturn(new R2StorageService.ObjectInfo(
                1234L, "application/zip", Collections.singletonMap("sha256", hash())));

        Map<String, Object> result = service.createUploadSession(
                "tpl_1", "tplver_1", resourceId(), request());

        assertEquals("ready", result.get("status"));
        assertEquals(Boolean.TRUE, result.get("reused"));
        verify(r2, never()).presignedPutUrl(eq(objectKey()), eq("application/zip"),
                eq(1234L), anyMap(), eq(Duration.ofMinutes(30)));
        verify(repository).upsertTemplateVersionResourceRef(
                "tpl_1", "tplver_1", resourceId(), hash(), "fonts");
    }

    @Test
    void completesOnlyWhenResourceIdSizeAndHashMetadataMatch() {
        when(repository.templateResourceAsset(resourceId(), hash()))
                .thenReturn(row("awaiting_upload"));
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("sha256", hash());
        metadata.put("resource-id", resourceId());
        when(r2.objectInfo(objectKey())).thenReturn(new R2StorageService.ObjectInfo(
                1234L, "application/zip", metadata));

        Map<String, Object> result = service.complete(
                "tpl_1", "tplver_1", resourceId(), hash());

        assertEquals("ready", result.get("status"));
        verify(repository).markTemplateResourceAssetReady(resourceId(), hash());
    }

    @Test
    void rejectsMismatchedResourceMetadata() {
        when(repository.templateResourceAsset(resourceId(), hash()))
                .thenReturn(row("awaiting_upload"));
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("sha256", hash());
        metadata.put("resource-id", "different");
        when(r2.objectInfo(objectKey())).thenReturn(new R2StorageService.ObjectInfo(
                1234L, "application/zip", metadata));

        ApiException exception = assertThrows(ApiException.class, () -> service.complete(
                "tpl_1", "tplver_1", resourceId(), hash()));

        assertEquals("TEMPLATE_RESOURCE_INTEGRITY_MISMATCH", exception.getCode());
        verify(repository).markTemplateResourceAssetFailed(eq(resourceId()), eq(hash()),
                eq("模板资源大小、ID 或 SHA-256 元数据不匹配"));
    }

    private TemplateResourceAssetUploadRequest request() {
        TemplateResourceAssetUploadRequest request = new TemplateResourceAssetUploadRequest();
        request.setSourceSha256(hash());
        request.setSourceSizeBytes(Long.valueOf(1234L));
        request.setFilename("package.zip");
        request.setContentType("application/zip");
        request.setPanel("fonts");
        return request;
    }

    private Map<String, Object> row(String status) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("resource_id", resourceId());
        row.put("source_sha256", hash());
        row.put("source_size_bytes", Long.valueOf(1234L));
        row.put("content_type", "application/zip");
        row.put("object_key", objectKey());
        row.put("status", status);
        return row;
    }

    private String resourceId() { return "7493843661713706257"; }
    private String hash() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }
    private String objectKey() {
        return "music-mv-template-resources/" + resourceId() + "/" + hash() + ".zip";
    }
}
