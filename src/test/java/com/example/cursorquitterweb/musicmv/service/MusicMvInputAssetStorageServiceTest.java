package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

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
                r2, tempDir.toString(), "", false, 3650L);
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
                r2, tempDir.toString(), "", false, 3650L);
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
                r2, tempDir.toString(), "", false, 3650L);

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
        when(r2.exists(org.mockito.ArgumentMatchers.argThat(
                key -> key != null && key.startsWith("images/user/"))))
                .thenReturn(true);
        when(r2.presignedGetUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn("https://temporary-r2.example/song");
        MusicMvInputAssetStorageService service = new MusicMvInputAssetStorageService(
                r2, tempDir.toString(), "https://api.storyai.example", true, 3650L);
        byte[] bytes = "photo-input".getBytes(StandardCharsets.UTF_8);

        StoredInputAsset stored = service.store("usr_1", "image", "photo.jpg",
                "image/jpeg", bytes.length, new ByteArrayInputStream(bytes),
                "http://127.0.0.1:8080");

        assertEquals("r2", stored.getStorage());
        assertTrue(stored.getUrl().startsWith("https://api.storyai.example/api/music-mv/v1/assets/"));
        assertTrue(!stored.getUrl().contains("temporary-r2.example"));
        String access = stored.getUrl().substring(stored.getUrl().indexOf("access=") + 7);
        Properties metadata = new Properties();
        metadata.setProperty("expiresAt", Instant.now().plusSeconds(3600L).toString());
        ByteArrayOutputStream metadataBytes = new ByteArrayOutputStream();
        metadata.store(metadataBytes, "test");
        when(r2.read(org.mockito.ArgumentMatchers.argThat(key -> key.endsWith(".properties"))))
                .thenReturn(metadataBytes.toByteArray());
        MusicMvInputAssetStorageService.InputAssetAccess result = service.access(
                stored.getAssetId(), access);
        assertEquals("https://temporary-r2.example/song", result.getRedirectUrl());
        verify(r2).presignedGetUrl(
                org.mockito.ArgumentMatchers.argThat(key -> key.startsWith("images/user/")),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void rejectsUploadWhenCloudStorageIsRequiredButUnavailable() {
        R2StorageService r2 = mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(false);
        MusicMvInputAssetStorageService service = new MusicMvInputAssetStorageService(
                r2, tempDir.toString(), "", true, 3650L);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.store("website-user-1", "image", "photo.jpg",
                        "image/jpeg", 3, new ByteArrayInputStream(new byte[] {1, 2, 3}),
                        "http://127.0.0.1:8080"));

        assertEquals("MV_INPUT_CLOUD_STORAGE_UNAVAILABLE", exception.getCode());
    }

    @Test
    void cleanupRemovesExpiredR2ObjectAndMetadataTogether() {
        R2StorageService r2 = mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(true);
        String expiredToken = "0000000000000001"
                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String objectKey = "images/user/mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/"
                + expiredToken;
        when(r2.listKeys("music-mv-inputs/music/"))
                .thenReturn(Collections.emptyList());
        when(r2.listKeys("images/user/"))
                .thenReturn(Arrays.asList(objectKey, objectKey + ".properties"));
        MusicMvInputAssetStorageService service = new MusicMvInputAssetStorageService(
                r2, tempDir.toString(), "", true, 3650L);

        service.cleanupExpiredAssets();

        verify(r2).delete(objectKey);
        verify(r2).delete(objectKey + ".properties");
        verify(r2, times(2)).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void registeredDeleteIsIdempotentWithoutPrivateMetadataLookup() throws Exception {
        R2StorageService r2 = mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(true);
        MusicMvInputAssetStorageService service = new MusicMvInputAssetStorageService(
                r2, tempDir.toString(), "https://api.storyai.example", true, 3650L);
        String token = String.format("%016x", java.time.Instant.now().plusSeconds(3600L)
                        .getEpochSecond())
                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        StoredInputAsset stored = new StoredInputAsset(
                "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "image",
                "https://api.storyai.example/api/music-mv/v1/assets/"
                        + "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa?access=" + token,
                "hash", "photo.jpg", "image/jpeg", 42L,
                java.time.Instant.now().plusSeconds(3600L), "r2");

        service.deleteRegisteredAsset(stored);

        verify(r2).delete("images/user/mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/"
                + token);
        verify(r2).delete("images/user/mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/"
                + token + ".properties");
        verify(r2, never()).exists(org.mockito.ArgumentMatchers.anyString());
    }
}
