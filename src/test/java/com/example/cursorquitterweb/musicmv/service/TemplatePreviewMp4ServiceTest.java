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
    final TemplatePreviewMp4Service service=new TemplatePreviewMp4Service(repository,
            new TemplatePreviewStorage(r2),mock(CloudflareTemplateMediaProvider.class),catalog,new ObjectMapper());
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
}
