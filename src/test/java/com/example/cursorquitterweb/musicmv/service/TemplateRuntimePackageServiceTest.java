package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.dto.TemplateRuntimePackageUploadRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class TemplateRuntimePackageServiceTest {
    private MusicMvTemplateCatalogRepository repository;
    private R2StorageService r2;
    private TemplateRuntimePackageService service;

    @BeforeEach
    void setUp() {
        repository = mock(MusicMvTemplateCatalogRepository.class);
        r2 = mock(R2StorageService.class);
        service = new TemplateRuntimePackageService(repository, r2);
        when(repository.version("tpl_1", "tplver_1"))
                .thenReturn(Collections.<String, Object>singletonMap("version_id", "tplver_1"));
        when(r2.isConfigured()).thenReturn(Boolean.TRUE);
    }

    @Test
    void createsPrivateR2UploadSessionWithIntegrityHeaders() {
        TemplateRuntimePackageUploadRequest request = request();
        Map<String, Object> awaiting = packageRow("awaiting_upload");
        when(repository.runtimePackage("tplver_1")).thenReturn(null, awaiting);
        when(r2.presignedPutUrl(eq(objectKey()), eq("application/zip"), eq(1234L),
                anyMap(), eq(Duration.ofMinutes(30)))).thenReturn("https://upload.example/package");

        Map<String, Object> result = service.createUploadSession("tpl_1", "tplver_1", request);

        assertEquals("https://upload.example/package", result.get("uploadUrl"));
        assertEquals("PUT", result.get("method"));
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) result.get("headers");
        assertEquals(hash(), headers.get("x-amz-meta-sha256"));
        verify(repository).upsertRuntimePackage("tpl_1", "tplver_1", objectKey(),
                hash(), 1234L, "application/zip", "awaiting_upload");
    }

    @Test
    void completesAndExposesShortLivedDownloadSession() {
        Map<String, Object> awaiting = packageRow("awaiting_upload");
        Map<String, Object> ready = packageRow("ready");
        when(repository.runtimePackage("tplver_1")).thenReturn(awaiting, ready, ready);
        when(r2.objectInfo(objectKey())).thenReturn(new R2StorageService.ObjectInfo(
                1234L, "application/zip", Collections.singletonMap("sha256", hash())));
        when(r2.presignedGetUrl(eq(objectKey()), eq(
                "attachment; filename=\"template-runtime-tplver_1.zip\""),
                eq(Duration.ofMinutes(15)))).thenReturn("https://download.example/package");

        Map<String, Object> completed = service.complete("tpl_1", "tplver_1");
        Map<String, Object> download = service.downloadSession("tpl_1", "tplver_1");

        assertEquals("ready", completed.get("status"));
        assertEquals("https://download.example/package", download.get("downloadUrl"));
        assertEquals(Long.valueOf(900L), download.get("expiresInSeconds"));
        verify(repository).markRuntimePackageReady("tplver_1");
    }

    @Test
    void rejectsUploadedObjectWithMismatchedIntegrityMetadata() {
        when(repository.runtimePackage("tplver_1")).thenReturn(packageRow("awaiting_upload"));
        when(r2.objectInfo(objectKey())).thenReturn(new R2StorageService.ObjectInfo(
                1200L, "application/zip", Collections.singletonMap("sha256", "bad")));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.complete("tpl_1", "tplver_1"));

        assertEquals("TEMPLATE_RUNTIME_PACKAGE_INTEGRITY_MISMATCH", exception.getCode());
        verify(repository).markRuntimePackageFailed(eq("tplver_1"),
                eq("运行包大小或 SHA-256 元数据不匹配"));
    }

    private TemplateRuntimePackageUploadRequest request() {
        TemplateRuntimePackageUploadRequest request = new TemplateRuntimePackageUploadRequest();
        request.setSourceSha256(hash());
        request.setSourceSizeBytes(Long.valueOf(1234L));
        request.setFilename("runtime-bundle.zip");
        return request;
    }

    private Map<String, Object> packageRow(String status) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("template_id", "tpl_1");
        row.put("version_id", "tplver_1");
        row.put("status", status);
        row.put("object_key", objectKey());
        row.put("source_sha256", hash());
        row.put("source_size_bytes", Long.valueOf(1234L));
        row.put("content_type", "application/zip");
        return row;
    }

    private String objectKey() {
        return "music-mv-template-runtime/tpl_1/tplver_1/" + hash() + ".zip";
    }

    private String hash() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }
}
