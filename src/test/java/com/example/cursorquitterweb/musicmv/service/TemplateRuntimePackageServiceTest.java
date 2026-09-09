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
    void deliversOriginalGifFromR2AndRejectsChangedSize() {
        Map<String, Object> row = new LinkedHashMap<>(); row.put("status", "ready");
        row.put("object_key", "motion.gif"); row.put("source_size_bytes", 123L); row.put("content_type", "image/gif");
        when(repository.templateResourceAsset("sha_" + hash(), hash())).thenReturn(row);
        when(r2.presignedGetUrl("motion.gif", Duration.ofMinutes(15))).thenReturn("https://download.example/atlas");
        Map<String, Object> source = new LinkedHashMap<>(); source.put("assetId", "sha_" + hash());
        source.put("sourceSha256", hash()); source.put("sourceSizeBytes", 123L); source.put("contentType", "image/gif");
        Map<String, Object> descriptor = new LinkedHashMap<>(); descriptor.put("sourceAsset", source);
        descriptor.put("kind", "animated_image");
        assertEquals("https://download.example/atlas", service.downloadExactImage(descriptor).get("url"));
        source.put("sourceSizeBytes", 124L);
        assertThrows(ApiException.class, () -> service.downloadExactImage(descriptor));
        source.put("sourceSizeBytes", 123L); source.put("contentType", "image/png");
        assertThrows(ApiException.class, () -> service.downloadExactImage(descriptor));
    }

    @Test
    void deliversExactAtlasFromR2AndRejectsChangedSize() {
        Map<String, Object> row = new LinkedHashMap<>(); row.put("status", "ready");
        row.put("object_key", "atlas.png"); row.put("source_size_bytes", 123L); row.put("content_type", "image/png");
        when(repository.templateResourceAsset("sha_" + hash(), hash())).thenReturn(row);
        when(r2.presignedGetUrl("atlas.png", Duration.ofMinutes(15))).thenReturn("https://download.example/atlas");
        Map<String, Object> source = new LinkedHashMap<>(); source.put("assetId", "sha_" + hash());
        source.put("sourceSha256", hash()); source.put("sourceSizeBytes", 123L); source.put("contentType", "image/png");
        Map<String, Object> descriptor = new LinkedHashMap<>(); descriptor.put("sourceAsset", source);
        descriptor.put("kind", "image"); descriptor.put("spriteAtlas", Collections.emptyMap());
        assertEquals("https://download.example/atlas", service.downloadExactImage(descriptor).get("url"));
        source.put("sourceSizeBytes", 124L);
        assertThrows(ApiException.class, () -> service.downloadExactImage(descriptor));
        source.put("sourceSizeBytes", 123L); descriptor.remove("spriteAtlas");
        assertThrows(ApiException.class, () -> service.downloadExactImage(descriptor));
    }

    @Test
    void fullPackageUploadCompletionAndFallbackAreRetired() {
        for (Runnable operation : java.util.Arrays.<Runnable>asList(
                () -> service.createUploadSession("tpl_1", "tplver_1", request()),
                () -> service.complete("tpl_1", "tplver_1"),
                () -> service.downloadSession("tpl_1", "tplver_1"),
                () -> service.downloadForScene("tpl_1", "tplver_1", Collections.emptyMap()))) {
            assertEquals("FULL_RUNTIME_PACKAGE_RETIRED", assertThrows(ApiException.class, operation::run).getCode());
        }
        org.mockito.Mockito.verifyNoInteractions(r2);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).runtimePackage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void cleanupChecksSceneAndDeletesOnlyLegacyVersionObjects() {
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("template_id", "tpl_1"); scene.put("manifest_sha256", hash());
        scene.put("scene_json", "{\"runtimeDelivery\":{\"schemaVersion\":\"browser-runtime-delivery-v1\",\"resources\":[],\"totalSizeBytes\":0}}");
        when(repository.browserScene("tplver_1")).thenReturn(scene);
        String prefix = "music-mv-template-runtime/tpl_1/tplver_1/";
        when(r2.listKeys(prefix)).thenReturn(Collections.singletonList(objectKey()), Collections.emptyList());
        when(r2.objectInfo(objectKey())).thenReturn(new R2StorageService.ObjectInfo(1234L, "application/zip", Collections.emptyMap()));
        assertThrows(ApiException.class, () -> service.cleanupLegacyPackage("tpl_1", "tplver_1", "stale"));
        org.mockito.Mockito.verify(r2, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.anyString());
        Map<String, Object> result = service.cleanupLegacyPackage("tpl_1", "tplver_1", hash());
        assertEquals(1234L, result.get("deletedBytes"));
        verify(r2).delete(objectKey());
        verify(repository).deleteRuntimePackage("tpl_1", "tplver_1");
    }

    @Test
    void cleanupBlocksMissingDeliveryAndUnknownObjects() {
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("template_id", "tpl_1"); scene.put("manifest_sha256", hash()); scene.put("scene_json", "{}");
        when(repository.browserScene("tplver_1")).thenReturn(scene);
        assertThrows(ApiException.class, () -> service.cleanupLegacyPackage("tpl_1", "tplver_1", hash()));
        scene.put("scene_json", "{\"runtimeDelivery\":{\"schemaVersion\":\"browser-runtime-delivery-v1\",\"resources\":[],\"totalSizeBytes\":0}}");
        when(r2.listKeys("music-mv-template-runtime/tpl_1/tplver_1/"))
                .thenReturn(Collections.singletonList("music-mv-template-runtime/tpl_1/tplver_1/unknown.json"));
        assertThrows(ApiException.class, () -> service.cleanupLegacyPackage("tpl_1", "tplver_1", hash()));
        org.mockito.Mockito.verify(r2, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).deleteRuntimePackage(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void emptyDeliveryDoesNotRequireLegacyPackageOrStorage() {
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("schemaVersion", "browser-runtime-delivery-v1");
        delivery.put("resources", Collections.emptyList()); delivery.put("totalSizeBytes", 0);
        when(r2.isConfigured()).thenReturn(false);
        Map<String, Object> result = service.downloadForScene("tpl_1", "tplver_1",
                Collections.singletonMap("runtimeDelivery", delivery));
        assertEquals("", result.get("downloadUrl"));
        assertEquals(0L, result.get("sourceSizeBytes"));
        assertEquals(Collections.emptyList(), result.get("resources"));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).runtimePackage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deliveryBindsTheExpectedAssetAndRejectsSizeMismatch() {
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("status", "ready"); asset.put("source_size_bytes", 1234L);
        asset.put("object_key", "shared.zip"); asset.put("content_type", "application/zip");
        when(repository.templateResourceAsset("sha_" + hash(), hash())).thenReturn(asset);
        when(r2.presignedGetUrl("shared.zip", Duration.ofMinutes(15))).thenReturn("https://r2.example/shared.zip");
        Map<String, Object> dependency = new LinkedHashMap<>();
        dependency.put("resourceId", "arbitrary"); dependency.put("assetId", "sha_" + hash());
        dependency.put("sourceSha256", hash()); dependency.put("sourceSizeBytes", 1234L);
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("schemaVersion", "browser-runtime-delivery-v1");
        delivery.put("resources", Collections.singletonList(dependency)); delivery.put("totalSizeBytes", 1234L);
        Map<String, Object> scene = Collections.singletonMap("runtimeDelivery", delivery);
        Map<String, Object> result = service.downloadForScene("tpl_1", "tplver_1", scene);
        Map<?, ?> download = (Map<?, ?>) ((java.util.List<?>) result.get("resources")).get(0);
        assertEquals("arbitrary", download.get("resourceId"));
        assertEquals("https://r2.example/shared.zip", download.get("downloadUrl"));
        assertEquals(false, download.containsKey("objectKey"));
        Map<String,Object> nativeEngine=BrowserNativeRuntimeContractTest.descriptor();
        Map<String,Object> binding=new LinkedHashMap<>();binding.put("resourceId","arbitrary");binding.put("kind","filter");binding.put("path","arbitrary/");
        binding.put("models",Collections.singletonMap("tt_face","arbitrary/models/face.model"));
        nativeEngine.put("files",java.util.Arrays.asList("arbitrary/config.json","arbitrary/models/face.model"));nativeEngine.put("bindings",Collections.singletonList(binding));
        delivery.put("nativeEngine",nativeEngine);
        assertEquals(nativeEngine,service.downloadForScene("tpl_1","tplver_1",scene).get("nativeEngine"));
        nativeEngine.put("schemaVersion","browser-native-scene-runtime-v2");
        binding.put("kind","animation");
        binding.remove("models");
        assertEquals(nativeEngine,service.downloadForScene("tpl_1","tplver_1",scene).get("nativeEngine"));
        dependency.put("sourceSizeBytes", 1235L);
        assertThrows(ApiException.class, () -> service.downloadForScene("tpl_1", "tplver_1", scene));
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
