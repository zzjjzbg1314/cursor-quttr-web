package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

class MusicMvRenderArtifactStorageServiceTest {
    @Test
    void browserAttemptsReceiveDifferentImmutableObjectKeys() {
        R2StorageService r2 = mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(true);
        when(r2.presignedPutUrl(any(), any(), anyLong(), anyMap(), any()))
                .thenReturn("https://upload.example/signed");
        MusicMvRenderArtifactStorageService storage =
                new MusicMvRenderArtifactStorageService(r2, "storage/test-render", 1024L, "r2");
        String sha256 = repeat('a');

        MusicMvRenderArtifactStorageService.BrowserUploadSession first =
                storage.createBrowserUploadSession("mvr_1", "bratt_1", 100L,
                        "video/mp4", sha256);
        MusicMvRenderArtifactStorageService.BrowserUploadSession second =
                storage.createBrowserUploadSession("mvr_1", "bratt_2", 100L,
                        "video/mp4", sha256);

        assertEquals("music-mv-renders/mvr_1/attempts/bratt_1/result.mp4",
                first.getObjectKey());
        assertEquals("music-mv-renders/mvr_1/attempts/bratt_2/result.mp4",
                second.getObjectKey());
        verify(r2).presignedPutUrl(eq(first.getObjectKey()), eq("video/mp4"), eq(100L),
                anyMap(), any());
        verify(r2).presignedPutUrl(eq(second.getObjectKey()), eq("video/mp4"), eq(100L),
                anyMap(), any());
    }

    @Test
    void browserOutputCanBeStoredAndVerifiedLocally(@TempDir Path directory) throws Exception {
        byte[] video = "local-browser-video".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String sha256 = hex(MessageDigest.getInstance("SHA-256").digest(video));
        MusicMvRenderArtifactStorageService storage =
                new MusicMvRenderArtifactStorageService(mock(R2StorageService.class),
                        directory.toString(), 1024L, "local");

        MusicMvRenderArtifactStorageService.BrowserUploadSession session =
                storage.createBrowserUploadSession("mvr_1", "bratt_1", video.length,
                        "video/mp4", sha256);
        MusicMvRenderArtifactStorageService.StoredArtifact uploaded =
                storage.storeBrowserUpload("mvr_1", "bratt_1",
                        new ByteArrayInputStream(video), video.length, "video/mp4", sha256);
        MusicMvRenderArtifactStorageService.StoredArtifact verified =
                storage.verifyBrowserUpload("mvr_1", "bratt_1", video.length,
                        "video/mp4", sha256);

        assertTrue(session.isLocal());
        assertEquals("/api/music-mv/v1/render-jobs/mvr_1/browser-output/local-upload",
                session.getUploadUrl());
        assertEquals("local:outputs/mvr_1/attempts/bratt_1/result.mp4",
                uploaded.getStorageKey());
        assertEquals(uploaded.getStorageKey(), verified.getStorageKey());
        assertTrue(Files.isRegularFile(directory.resolve(
                "outputs/mvr_1/attempts/bratt_1/result.mp4")));
    }

    @Test
    void clearsAllHistoricalFilesBeforeALocalBrowserRender(@TempDir Path directory)
            throws Exception {
        Path first = directory.resolve("outputs/mvr_old/attempts/bratt_1/result.mp4");
        Path partial = directory.resolve("outputs/mvr_partial/attempts/bratt_2/result.mp4.part");
        Files.createDirectories(first.getParent());
        Files.createDirectories(partial.getParent());
        Files.write(first, new byte[] {1, 2, 3});
        Files.write(partial, new byte[] {4, 5, 6});
        MusicMvRenderArtifactStorageService storage =
                new MusicMvRenderArtifactStorageService(mock(R2StorageService.class),
                        directory.toString(), 1024L, "local");

        storage.clearLocalBrowserOutputs();

        assertTrue(Files.isDirectory(directory.resolve("outputs")));
        try (java.util.stream.Stream<Path> files = Files.list(directory.resolve("outputs"))) {
            assertEquals(0L, files.count());
        }
    }

    private String repeat(char value) {
        char[] chars = new char[64];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
