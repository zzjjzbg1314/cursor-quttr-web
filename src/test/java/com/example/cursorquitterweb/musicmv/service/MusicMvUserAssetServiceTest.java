package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.cursorquitterweb.musicmv.repository.MusicMvUserAssetRepository;
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService.StoredInputAsset;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicMvUserAssetServiceTest {

    @Test
    void reusesMatchingAssetAndRemovesRedundantCloudUpload() throws Exception {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        StoredInputAsset uploaded = new StoredInputAsset("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "image", "https://api.example/assets/new?access=token", "same-hash",
                "photo.jpg", "image/jpeg", 42L, Instant.now().plusSeconds(3600L), "r2");
        when(storage.store(eq("usr_1"), eq("image"), eq("photo.jpg"), eq("image/jpeg"),
                eq(42L), any(ByteArrayInputStream.class), eq("https://api.example")))
                .thenReturn(uploaded);
        Map<String, Object> existing = new LinkedHashMap<String, Object>();
        existing.put("asset_id", "mva_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        existing.put("project_id", "project_1");
        existing.put("kind", "image");
        existing.put("asset_url", "https://api.example/assets/existing?access=token");
        existing.put("sha256", "same-hash");
        existing.put("file_name", "photo.jpg");
        existing.put("content_type", "image/jpeg");
        existing.put("size_bytes", Long.valueOf(42L));
        existing.put("expires_at", Instant.now().plusSeconds(3600L).toString());
        existing.put("storage", "r2");
        existing.put("created_at", Instant.now().toString());
        when(repository.findReusable("usr_1", "image", "same-hash", 42L))
                .thenReturn(existing);

        ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3});
        Map<String, Object> result = service.upload("usr_1", "project_1", "image",
                "photo.jpg", "image/jpeg", 42L, input, "https://api.example");

        assertEquals(Boolean.TRUE, result.get("deduplicated"));
        assertEquals("mva_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", result.get("assetId"));
        verify(storage).deleteOwnedAsset("usr_1", uploaded);
        verify(repository).touch("usr_1", "mva_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        verify(repository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void storesNewAssetWhenNoReusableMatchExists() throws Exception {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        StoredInputAsset uploaded = new StoredInputAsset("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "image", "https://api.example/assets/new?access=token", "new-hash",
                "photo.jpg", "image/jpeg", 42L, Instant.now().plusSeconds(3600L), "r2");
        when(storage.store(eq("usr_1"), eq("image"), eq("photo.jpg"), eq("image/jpeg"),
                eq(42L), any(ByteArrayInputStream.class), eq("https://api.example")))
                .thenReturn(uploaded);
        when(repository.findReusable("usr_1", "image", "new-hash", 42L))
                .thenReturn(null);
        when(repository.insertIfAbsent("usr_1", "project_1", uploaded)).thenReturn(true);

        ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {1, 2, 3});
        Map<String, Object> result = service.upload("usr_1", "project_1", "image",
                "photo.jpg", "image/jpeg", 42L, input, "https://api.example");

        assertEquals(Boolean.FALSE, result.get("deduplicated"));
        assertEquals(uploaded.getAssetId(), result.get("assetId"));
        verify(repository).insertIfAbsent("usr_1", "project_1", uploaded);
        verify(storage, never()).deleteOwnedAsset(any(), any());
    }

    @Test
    void concurrentMatchingUploadKeepsOnlyPersistedAsset() throws Exception {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        StoredInputAsset uploaded = stored("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "same-hash");
        when(storage.store(eq("usr_1"), eq("image"), eq("photo.jpg"), eq("image/jpeg"),
                eq(42L), any(ByteArrayInputStream.class), eq("https://api.example")))
                .thenReturn(uploaded);
        Map<String, Object> existing = row("mva_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "same-hash");
        when(repository.findReusable("usr_1", "image", "same-hash", 42L))
                .thenReturn(null, existing);
        when(repository.insertIfAbsent("usr_1", "project_1", uploaded)).thenReturn(false);

        Map<String, Object> result = upload(service);

        assertEquals(Boolean.TRUE, result.get("deduplicated"));
        assertEquals("mva_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", result.get("assetId"));
        verify(storage).deleteOwnedAsset("usr_1", uploaded);
        verify(repository).touch("usr_1", "mva_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    }

    @Test
    void d1FailureRemovesUnindexedCloudUpload() throws Exception {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        StoredInputAsset uploaded = stored("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "new-hash");
        when(storage.store(eq("usr_1"), eq("image"), eq("photo.jpg"), eq("image/jpeg"),
                eq(42L), any(ByteArrayInputStream.class), eq("https://api.example")))
                .thenReturn(uploaded);
        IllegalStateException failure = new IllegalStateException("D1 unavailable");
        when(repository.findReusable("usr_1", "image", "new-hash", 42L))
                .thenThrow(failure).thenReturn(null);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> upload(service));

        assertEquals(failure, thrown);
        verify(storage).deleteOwnedAsset("usr_1", uploaded);
    }

    @Test
    void ambiguousInsertSuccessKeepsTheIndexedUpload() throws Exception {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        StoredInputAsset uploaded = stored("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "new-hash");
        when(storage.store(eq("usr_1"), eq("image"), eq("photo.jpg"), eq("image/jpeg"),
                eq(42L), any(ByteArrayInputStream.class), eq("https://api.example")))
                .thenReturn(uploaded);
        when(repository.findReusable("usr_1", "image", "new-hash", 42L))
                .thenReturn(null, row(uploaded.getAssetId(), "new-hash"));
        when(repository.insertIfAbsent("usr_1", "project_1", uploaded))
                .thenThrow(new IllegalStateException("response lost after commit"));

        Map<String, Object> result = upload(service);

        assertEquals(Boolean.FALSE, result.get("deduplicated"));
        assertEquals(uploaded.getAssetId(), result.get("assetId"));
        verify(storage, never()).deleteOwnedAsset(any(), any());
    }

    @Test
    void deleteTransitionsThroughRecoverableState() throws Exception {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        Map<String, Object> row = row("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "hash");
        when(repository.findOwned("usr_1", "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .thenReturn(row);
        when(repository.isReferencedByActiveProject("usr_1",
                "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")).thenReturn(false);

        service.delete("usr_1", "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        verify(repository).markDeleting("usr_1", "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        verify(storage).deleteRegisteredAsset(any(StoredInputAsset.class));
        verify(repository).markDeleted("usr_1", "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        verify(repository, never()).restoreActive(any(), any());
    }

    @Test
    void deleteRestoresActiveStateWhenStorageFails() throws Exception {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        Map<String, Object> row = row("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "hash");
        when(repository.findOwned("usr_1", "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .thenReturn(row);
        when(repository.isReferencedByActiveProject("usr_1",
                "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")).thenReturn(false);
        doThrow(new IOException("R2 unavailable")).when(storage)
                .deleteRegisteredAsset(any(StoredInputAsset.class));

        assertThrows(IOException.class,
                () -> service.delete("usr_1", "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        verify(repository).restoreActive("usr_1", "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        verify(repository, never()).markDeleted(any(), any());
    }

    @Test
    void reconcilesDeletionThatWasInterruptedAfterDatabaseTransition() throws Exception {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        Map<String, Object> pending = row("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "hash");
        pending.put("user_id", "usr_1");
        when(repository.listDeleting(100)).thenReturn(Arrays.asList(pending));

        service.reconcileDeletingAssets();

        verify(storage).deleteRegisteredAsset(any(StoredInputAsset.class));
        verify(repository).markDeleted("usr_1", "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Test
    void listsRecentAssetsWithNormalizedKindAndLimit() {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        Map<String, Object> asset = row("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "hash");
        when(repository.listRecent("usr_1", "image", 100))
                .thenReturn(Collections.singletonList(asset));

        Map<String, Object> result = service.list("usr_1", "recent", null,
                " IMAGE ", 500);

        assertEquals("recent", result.get("scope"));
        assertEquals(Integer.valueOf(1), result.get("count"));
        verify(repository).listRecent("usr_1", "image", 100);
        verify(repository, never()).listProject(any(), any(), any(), anyInt());
    }

    @Test
    void listsOnlyAssetsAttachedToRequestedProject() {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        when(repository.listProject("usr_1", "mvp_project_123", "image", 20))
                .thenReturn(Collections.singletonList(
                        row("mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "hash")));

        Map<String, Object> result = service.list("usr_1", "PROJECT",
                "mvp_project_123", "image", 20);

        assertEquals("project", result.get("scope"));
        assertEquals(Integer.valueOf(1), result.get("count"));
        verify(repository).listProject("usr_1", "mvp_project_123", "image", 20);
        verify(repository, never()).listRecent(any(), any(), anyInt());
    }

    @Test
    void rejectsDeleteWhileAssetIsUsedBySavedProject() throws Exception {
        MusicMvInputAssetStorageService storage = mock(MusicMvInputAssetStorageService.class);
        MusicMvUserAssetRepository repository = mock(MusicMvUserAssetRepository.class);
        MusicMvUserAssetService service = new MusicMvUserAssetService(storage, repository);
        String assetId = "mva_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        when(repository.findOwned("usr_1", assetId)).thenReturn(row(assetId, "hash"));
        when(repository.isReferencedByActiveProject("usr_1", assetId)).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.delete("usr_1", assetId));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("MV_INPUT_ASSET_IN_USE", error.getCode());
        verify(repository, never()).markDeleting(any(), any());
        verify(storage, never()).deleteRegisteredAsset(any());
    }

    private Map<String, Object> upload(MusicMvUserAssetService service) throws Exception {
        return service.upload("usr_1", "project_1", "image", "photo.jpg", "image/jpeg",
                42L, new ByteArrayInputStream(new byte[] {1, 2, 3}), "https://api.example");
    }

    private StoredInputAsset stored(String assetId, String sha256) {
        return new StoredInputAsset(assetId, "image",
                "https://api.example/assets/" + assetId
                        + "?access=0000000000000001aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                sha256, "photo.jpg", "image/jpeg", 42L,
                Instant.now().plusSeconds(3600L), "r2");
    }

    private Map<String, Object> row(String assetId, String sha256) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("asset_id", assetId);
        row.put("user_id", "usr_1");
        row.put("project_id", "project_1");
        row.put("kind", "image");
        row.put("asset_url", "https://api.example/assets/" + assetId
                + "?access=0000000000000001aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        row.put("sha256", sha256);
        row.put("file_name", "photo.jpg");
        row.put("content_type", "image/jpeg");
        row.put("size_bytes", Long.valueOf(42L));
        row.put("expires_at", Instant.now().plusSeconds(3600L).toString());
        row.put("storage", "r2");
        row.put("created_at", Instant.now().toString());
        return row;
    }
}
