package com.example.cursorquitterweb.musicmv.service;

import static org.mockito.Mockito.*;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;

class TemplateMediaCleanupServiceTest {
    private final MusicMvTemplateCatalogRepository repository = mock(MusicMvTemplateCatalogRepository.class);
    private final CloudflareTemplateMediaProvider provider = mock(CloudflareTemplateMediaProvider.class);
    private final D1DatabaseClient d1 = mock(D1DatabaseClient.class);
    private final R2StorageService r2 = mock(R2StorageService.class);
    private final TemplateMediaCleanupService service = new TemplateMediaCleanupService(repository, provider, d1, r2);

    private void candidate() {
        when(d1.isConfigured()).thenReturn(true);
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("provider", "cloudflare_images");
        row.put("provider_asset_id", "old");
        row.put("template_id", "t");
        row.put("version_id", "v");
        when(repository.cleanupCandidates()).thenReturn(Collections.singletonList(row));
    }

    @Test void deletesUnreferencedAssetThenAcknowledges() {
        candidate();
        service.cleanup();
        org.mockito.InOrder order = inOrder(repository, provider);
        order.verify(repository).retireUnusedTemplateContents();
        order.verify(repository).cleanupCandidates();
        order.verify(repository).cleanupAssetReferenced("cloudflare_images", "old", "t", "v");
        order.verify(provider).deleteAsset("cloudflare_images", "old");
        order.verify(repository).completeCleanup("cloudflare_images", "old");
    }

    @Test void referencedAssetsAreRetained() {
        candidate();
        when(repository.cleanupAssetReferenced(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        service.cleanup();
        verifyNoInteractions(provider);
        verify(repository).deferCleanup("cloudflare_images", "old");
    }

    @Test void failedDeleteRemainsQueued() {
        candidate();
        doThrow(new IllegalStateException("offline")).when(provider).deleteAsset(anyString(), anyString());
        service.cleanup();
        verify(repository).deferCleanup("cloudflare_images", "old");
        verify(repository, never()).completeCleanup(anyString(), anyString());
    }

    @Test void unknownReferencesNeverDelete() {
        candidate();
        when(repository.cleanupAssetReferenced(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("offline"));
        service.cleanup();
        verifyNoInteractions(provider);
        verify(repository).deferCleanup("cloudflare_images", "old");
    }

    @Test void unconfiguredDatabaseDoesNothing() {
        service.cleanup();
        verifyNoInteractions(repository, provider);
    }
    private void runtimeCandidate(String key) {
        candidate();
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("provider", "r2"); row.put("provider_asset_id", key);
        row.put("template_id", "t"); row.put("version_id", "v");
        when(repository.cleanupCandidates()).thenReturn(Collections.singletonList(row));
    }

    @Test void deletesOnlyUnreferencedTemplateRuntime() {
        String key = "music-mv-template-runtime/t/v/" + String.join("", Collections.nCopies(64, "a")) + ".zip";
        runtimeCandidate(key);
        service.cleanup();
        org.mockito.InOrder order = inOrder(repository, r2);
        order.verify(repository).retireUnusedTemplateContents();
        order.verify(repository).cleanupCandidates();
        order.verify(repository).cleanupRuntimeReferenced(key, "t", "v");
        order.verify(r2).delete(key);
        order.verify(repository).completeCleanup("r2", key);
        verifyNoInteractions(provider);
    }

    @Test void retainsReferencedRuntime() {
        String key = "music-mv-template-runtime/t/v/" + String.join("", Collections.nCopies(64, "a")) + ".zip";
        runtimeCandidate(key);
        when(repository.cleanupRuntimeReferenced(key, "t", "v")).thenReturn(true);
        service.cleanup();
        verifyNoInteractions(r2, provider);
        verify(repository).deferCleanup("r2", key);
    }

    @Test void neverDeletesUserObjectsOrOtherTemplateDirectories() {
        for (String key : new String[]{"music-mv-user-assets/photo.jpg", "music-mv-template-runtime/other/v/a.zip",
                "music-mv-template-runtime/t/v/../photo.jpg"}) {
            runtimeCandidate(key);
            service.cleanup();
            verify(repository).deferCleanup("r2", key);
        }
        verifyNoInteractions(r2, provider);
    }

    @Test void failedRuntimeDeleteRemainsQueued() {
        String key = "music-mv-template-runtime/t/v/" + String.join("", Collections.nCopies(64, "a")) + ".zip";
        runtimeCandidate(key);
        doThrow(new IllegalStateException("offline")).when(r2).delete(key);
        service.cleanup();
        verify(repository).deferCleanup("r2", key);
        verify(repository, never()).completeCleanup("r2", key);
    }

}
