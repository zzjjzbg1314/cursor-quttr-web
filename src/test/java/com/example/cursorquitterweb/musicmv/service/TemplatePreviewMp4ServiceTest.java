package com.example.cursorquitterweb.musicmv.service;

import com.example.cursorquitterweb.musicmv.dto.TemplatePreviewMp4Request;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TemplatePreviewMp4ServiceTest {
    final MusicMvTemplateCatalogRepository repository=mock(MusicMvTemplateCatalogRepository.class);
    final R2StorageService r2=mock(R2StorageService.class);
    final MusicMvTemplateCatalogService catalog=mock(MusicMvTemplateCatalogService.class);
    final CloudflareTemplateMediaProvider provider=mock(CloudflareTemplateMediaProvider.class);
    final TemplatePreviewMp4Service service=new TemplatePreviewMp4Service(repository,
            new TemplatePreviewStorage(r2),provider,catalog,new ObjectMapper());
    TemplatePreviewMp4Request request() {
        TemplatePreviewMp4Request r=new TemplatePreviewMp4Request();
        r.sourceMediaId="media_1"; r.sourceSha256=String.join("",Collections.nCopies(64,"a"));
        r.sha256=String.join("",Collections.nCopies(64,"b"));r.sizeBytes=1234L;
        r.width=720;r.height=1280;r.durationSeconds=30.0;
        when(repository.version("tpl_1","ver_1")).thenReturn(Collections.emptyMap());
        Map<String,Object> source=new HashMap<>();source.put("status","ready");source.put("provider","cloudflare_stream");
        source.put("media_id",r.sourceMediaId);source.put("source_sha256",r.sourceSha256);
        when(repository.mediaByRole("ver_1","browser_parity_reference")).thenReturn(source);
        when(r2.isConfigured()).thenReturn(true);
        return r;
    }
    @Test void existingPreviewQueryResolvesCurrentDeliveryUrl() {
        TemplatePreviewMp4Request r=request();
        Map<String,Object> existing=new HashMap<>();
        existing.put("status","ready");existing.put("provider","cloudflare_r2_preview");
        existing.put("provider_asset_id","templates/version/hash.mp4");
        Map<String,Object> saved=new HashMap<>();
        saved.put("sourceMediaId",r.sourceMediaId);saved.put("sourceSha256",r.sourceSha256);
        saved.put("playbackUrl","https://video.musicvideomaker.net/templates/version/hash.mp4");
        try { existing.put("provider_details_json",new ObjectMapper().writeValueAsString(saved)); }
        catch(Exception e) { throw new AssertionError(e); }
        when(repository.mediaByRole("ver_1","preview_mp4")).thenReturn(existing);
        Map<String,Object> resolved=Collections.singletonMap("playbackUrl","https://video.songtovideo.ai/templates/version/hash.mp4");
        when(provider.resolveDeliveryDetails("cloudflare_r2_preview","templates/version/hash.mp4",saved)).thenReturn(resolved);
        assertEquals(resolved,service.source("tpl_1","ver_1").get("previewMp4"));
        verifyNoInteractions(r2);
    }

    @Test void rejects1080AndStaleSourceBeforeUpload() {
        TemplatePreviewMp4Request r=request();r.width=1080;
        assertEquals("PREVIEW_METADATA_INVALID",assertThrows(ApiException.class,()->service.upload("tpl_1","ver_1",r)).getCode());
        r.width=720;r.sourceMediaId="stale";
        assertEquals("PREVIEW_SOURCE_CHANGED",assertThrows(ApiException.class,()->service.upload("tpl_1","ver_1",r)).getCode());
        verifyNoInteractions(r2);
    }
    @Test void signsImmutableMp4WithoutChangingStreamOrDatabase() {
        TemplatePreviewMp4Request r=request();
        Map<String,Object> result=service.upload("tpl_1","ver_1",r);
        assertEquals(TemplatePreviewMp4Service.CACHE_CONTROL,((Map<?,?>)result.get("headers")).get("Cache-Control"));
        verify(r2).presignedPutUrl(eq(TemplatePreviewMp4Service.objectKey("ver_1",r)),eq("video/mp4"),eq(1234L),anyMap(),
                eq(TemplatePreviewMp4Service.CACHE_CONTROL),eq(java.time.Duration.ofMinutes(30)));
        verify(repository,never()).upsertMedia(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),any(),any(),any(),anyString());
    }
    @Test void onlyPublishesAfterIntegrityAndSourceValidation() {
        TemplatePreviewMp4Request r=request();String key=TemplatePreviewMp4Service.objectKey("ver_1",r);
        when(r2.objectInfo(key)).thenReturn(new R2StorageService.ObjectInfo(12L,"video/mp4",Collections.singletonMap("sha256",r.sha256)));
        assertEquals("PREVIEW_INTEGRITY_MISMATCH",assertThrows(ApiException.class,()->service.complete("tpl_1","ver_1",r)).getCode());
        verifyNoInteractions(catalog);
        when(r2.objectInfo(key)).thenReturn(new R2StorageService.ObjectInfo(1234L,"video/mp4",Collections.singletonMap("sha256",r.sha256)));
        when(repository.mediaByRole("ver_1","preview_mp4")).thenReturn(Collections.singletonMap("provider_asset_id",key));
        when(r2.publicUrl(key)).thenReturn("https://video.example/"+key);
        assertEquals("https://video.example/"+key,service.complete("tpl_1","ver_1",r).get("playbackUrl"));
        verify(repository).publishPreviewMp4(eq("tpl_1"),eq("ver_1"),eq(r.sourceMediaId),eq(r.sourceSha256),eq(key),eq(r.sha256),eq(1234L),eq(720),eq(1280),eq(30.0),anyString());
        verify(catalog).invalidateDetail("tpl_1");
    }
    @Test void rejectsOfficialResearchPreview() {
        TemplatePreviewMp4Request r=request();
        Map<String,Object> source=repository.mediaByRole("ver_1","browser_parity_reference");
        source.put("provider_details_json","{\"sourceType\":\"capcut_official_template_preview\"}");
        assertEquals("PREVIEW_SOURCE_NOT_READY",assertThrows(ApiException.class,()->service.upload("tpl_1","ver_1",r)).getCode());
    }
    @Test void acceptsR2SourceAndStillPublishesOnly720Preview() {
        TemplatePreviewMp4Request r=request();
        repository.mediaByRole("ver_1","browser_parity_reference").put("provider","cloudflare_r2");
        assertNotNull(service.upload("tpl_1","ver_1",r).get("headers"));
        r.width=1080;
        assertEquals("PREVIEW_METADATA_INVALID",assertThrows(ApiException.class,()->service.upload("tpl_1","ver_1",r)).getCode());
    }
    @Test void sourceUsesR2DeliveryWithoutResolvingStream() {
        request();
        Map<String,Object> source=repository.mediaByRole("ver_1","browser_parity_reference");
        source.put("provider","cloudflare_r2");source.put("provider_asset_id","source-key");
        when(provider.resolveDeliveryDetails(eq("cloudflare_r2"),eq("source-key"),anyMap()))
                .thenReturn(Collections.<String,Object>singletonMap("playbackUrl","https://r2.example/source.mp4"));
        assertEquals("https://r2.example/source.mp4",service.source("tpl_1","ver_1").get("playbackUrl"));
        verify(provider,never()).resolveDeliveryDetails(eq("cloudflare_stream"),anyString(),anyMap());
    }

    @Test void lowRejects720AndPublishesSeparateRoleAndKey() {
        TemplatePreviewMp4Request r=request();
        assertThrows(ApiException.class,()->service.upload("tpl_1","ver_1",r,true));
        r.width=480;r.height=854;
        String key=TemplatePreviewMp4Service.objectKey("ver_1",r,true);
        assertTrue(key.contains("/480/"));
        assertNotEquals(key,TemplatePreviewMp4Service.objectKey("ver_1",r));
        service.upload("tpl_1","ver_1",r,true);
        when(r2.objectInfo(key)).thenReturn(new R2StorageService.ObjectInfo(1234L,"video/mp4",Collections.singletonMap("sha256",r.sha256)));
        when(repository.mediaByRole("ver_1","preview_mp4_low")).thenReturn(Collections.singletonMap("provider_asset_id",key));
        assertEquals(480,service.complete("tpl_1","ver_1",r,true).get("maxShortEdge"));
        verify(repository).publishPreviewMp4Low(eq("tpl_1"),eq("ver_1"),eq(r.sourceMediaId),eq(r.sourceSha256),
                eq(key),eq(r.sha256),eq(1234L),eq(480),eq(854),eq(30.0),anyString());
        verify(repository,never()).publishPreviewMp4(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyInt(),anyInt(),anyDouble(),anyString());
    }

    @Test void lowUsesVerifiedHighInputAndKeepsOriginalIdentity() throws Exception {
        TemplatePreviewMp4Request r=request();
        Map<String,Object> details=new HashMap<>();
        details.put("sourceMediaId",r.sourceMediaId);details.put("sourceSha256",r.sourceSha256);
        details.put("sha256",r.sha256);
        Map<String,Object> high=new HashMap<>();
        high.put("status","ready");high.put("provider","cloudflare_r2_preview");
        high.put("provider_asset_id","high.mp4");high.put("provider_details_json",new ObjectMapper().writeValueAsString(details));
        when(repository.mediaByRole("ver_1","preview_mp4")).thenReturn(high);
        when(provider.resolveDeliveryDetails(eq("cloudflare_r2_preview"),eq("high.mp4"),anyMap()))
                .thenReturn(Collections.singletonMap("playbackUrl","https://video.songtovideo.ai/high.mp4"));
        Map<String,Object> result=service.source("tpl_1","ver_1",true);
        assertEquals(r.sourceSha256,result.get("sourceSha256"));
        assertEquals(r.sha256,result.get("inputSha256"));
        assertEquals("https://video.songtovideo.ai/high.mp4",result.get("playbackUrl"));
        details.put("sourceSha256","stale");
        high.put("provider_details_json",new ObjectMapper().writeValueAsString(details));
        assertFalse(service.source("tpl_1","ver_1",true).containsKey("inputSha256"));
    }
}
