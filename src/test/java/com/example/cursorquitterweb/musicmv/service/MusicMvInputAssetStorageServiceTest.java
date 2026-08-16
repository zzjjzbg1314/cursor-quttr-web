package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.StreamUtils;

import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService.LocalAsset;
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService.StoredInputAsset;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicMvInputAssetStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void storesAndServesLocalInputWithCapabilityToken() throws Exception {
        R2StorageService r2 = mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(false);
        MusicMvInputAssetStorageService service = new MusicMvInputAssetStorageService(
                r2, tempDir.toString(), "", false);
        byte[] bytes = "music-input".getBytes(StandardCharsets.UTF_8);

        StoredInputAsset stored = service.store("website-user-1", "music", "song.mp3",
                "audio/mpeg", bytes.length, new ByteArrayInputStream(bytes),
                "http://127.0.0.1:8080");

        assertEquals("local", stored.getStorage());
        assertEquals("music", stored.getKind());
        assertEquals(bytes.length, stored.getSizeBytes());
        assertTrue(stored.getSha256().matches("[0-9a-f]{64}"));
        String access = stored.getUrl().substring(stored.getUrl().indexOf("access=") + 7);
        LocalAsset local = service.localAsset(stored.getAssetId(), access);
        assertArrayEquals(bytes, StreamUtils.copyToByteArray(local.getResource().getInputStream()));
        assertEquals("audio/mpeg", local.getContentType());
        assertTrue(Files.isDirectory(tempDir.resolve("inputs").resolve(stored.getAssetId())));
    }

    @Test
    void rejectsWrongCapabilityToken() throws Exception {
        R2StorageService r2 = mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(false);
        MusicMvInputAssetStorageService service = new MusicMvInputAssetStorageService(
                r2, tempDir.toString(), "", false);
        byte[] bytes = new byte[] {1, 2, 3};
        StoredInputAsset stored = service.store("website-user-1", "image", "photo.jpg",
                "image/jpeg", bytes.length, new ByteArrayInputStream(bytes),
                "http://127.0.0.1:8080");

        ApiException exception = assertThrows(ApiException.class,
                () -> service.localAsset(stored.getAssetId(), "wrong"));
        assertEquals("MV_INPUT_ASSET_NOT_FOUND", exception.getCode());
    }

    @Test
    void rejectsContentTypeThatDoesNotMatchKind() {
        R2StorageService r2 = mock(R2StorageService.class);
        MusicMvInputAssetStorageService service = new MusicMvInputAssetStorageService(
                r2, tempDir.toString(), "", false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.store("website-user-1", "image", "payload.mp3",
                        "audio/mpeg", 3, new ByteArrayInputStream(new byte[] {1, 2, 3}),
                        "http://127.0.0.1:8080"));
        assertEquals("MV_INPUT_ASSET_TYPE_INVALID", exception.getCode());
    }

    @Test
    void publishesStableCapabilityUrlForR2AndSignsOnlyAtDownloadTime() throws Exception {
        R2StorageService r2 = mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(true);
        when(r2.exists(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(r2.presignedGetUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn("https://temporary-r2.example/song");
        MusicMvInputAssetStorageService service = new MusicMvInputAssetStorageService(
                r2, tempDir.toString(), "https://api.storyai.example", true);
        byte[] bytes = "photo-input".getBytes(StandardCharsets.UTF_8);

        StoredInputAsset stored = service.store("usr_1", "image", "photo.jpg",
                "image/jpeg", bytes.length, new ByteArrayInputStream(bytes),
                "http://127.0.0.1:8080");

        assertEquals("r2", stored.getStorage());
        assertTrue(stored.getUrl().startsWith("https://api.storyai.example/api/music-mv/v1/assets/"));
        assertTrue(!stored.getUrl().contains("temporary-r2.example"));
        String access = stored.getUrl().substring(stored.getUrl().indexOf("access=") + 7);
        MusicMvInputAssetStorageService.InputAssetAccess result = service.access(
                stored.getAssetId(), access);
        assertEquals("https://temporary-r2.example/song", result.getRedirectUrl());
        verify(r2).presignedGetUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void rejectsUploadWhenCloudStorageIsRequiredButUnavailable() {
        R2StorageService r2 = mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(false);
        MusicMvInputAssetStorageService service = new MusicMvInputAssetStorageService(
                r2, tempDir.toString(), "", true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.store("website-user-1", "image", "photo.jpg",
                        "image/jpeg", 3, new ByteArrayInputStream(new byte[] {1, 2, 3}),
                        "http://127.0.0.1:8080"));

        assertEquals("MV_INPUT_CLOUD_STORAGE_UNAVAILABLE", exception.getCode());
    }
}
