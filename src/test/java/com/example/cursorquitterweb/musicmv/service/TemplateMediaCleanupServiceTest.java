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
    private final TemplateMediaCleanupService service = new TemplateMediaCleanupService(repository, provider, d1);

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
}
