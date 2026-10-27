package com.example.cursorquitterweb.musicmv.service;

import com.example.cursorquitterweb.musicmv.controller.RendererTemplatePreviewLowController;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用本地实际转码产物验证控制器流程，存储和数据库替身不访问线上。 */
@EnabledIfSystemProperty(named="preview.low.fixture",matches=".+")
class TemplatePreviewLowLocalFlowTest {
    @TempDir Path storage;
    @Test void actualMp4RegistersAndReadsBackWithoutTouchingHighPreview() throws Exception {
        Path fixture=Paths.get(System.getProperty("preview.low.fixture"));
        ObjectMapper mapper=new ObjectMapper();
        Map<String,Object> request=mapper.readValue(fixture.resolve("metadata.json").toFile(),Map.class);
        byte[] bytes=Files.readAllBytes(fixture.resolve("480.mp4"));
        assertEquals(((Number)request.get("sizeBytes")).longValue(),bytes.length);
        request.put("sourceMediaId","source");
        MusicMvTemplateCatalogRepository repository=mock(MusicMvTemplateCatalogRepository.class);
        R2StorageService r2=mock(R2StorageService.class);
        MusicMvTemplateCatalogService catalog=mock(MusicMvTemplateCatalogService.class);
        CloudflareTemplateMediaProvider provider=mock(CloudflareTemplateMediaProvider.class);
        TemplateSyncAuthenticationService auth=mock(TemplateSyncAuthenticationService.class);
        when(repository.version("t","v")).thenReturn(Collections.emptyMap());
        Map<String,Object> source=new HashMap<>();
        source.put("media_id","source");source.put("source_sha256",request.get("sourceSha256"));
        source.put("status","ready");source.put("provider","cloudflare_r2");
        when(repository.mediaByRole("v","browser_parity_reference")).thenReturn(source);
        Map<String,Object> low=new HashMap<>();
        when(repository.mediaByRole("v","preview_mp4_low")).thenAnswer(call->low.isEmpty()?null:low);
        when(provider.resolveDeliveryDetails(any(),any(),anyMap())).thenAnswer(call->call.getArgument(2));
        when(r2.isConfigured()).thenReturn(true);
        when(r2.presignedPutUrl(anyString(),anyString(),anyLong(),anyMap(),anyString(),any())).thenReturn("http://localhost/local-upload");
        when(r2.publicUrl(anyString())).thenAnswer(call->"https://local.example/"+call.getArgument(0));
        when(r2.objectInfo(anyString())).thenAnswer(call->new R2StorageService.ObjectInfo(
                Files.size(storage.resolve("uploaded.mp4")),"video/mp4",
                Collections.singletonMap("sha256",String.valueOf(request.get("sha256")))));
        doAnswer(call->{
            low.put("status","ready");low.put("provider","cloudflare_r2_preview");
            low.put("provider_asset_id",call.getArgument(4));low.put("provider_details_json",call.getArgument(10));
            return null;
        }).when(repository).publishPreviewMp4Low(anyString(),anyString(),anyString(),anyString(),
                anyString(),anyString(),anyLong(),anyInt(),anyInt(),anyDouble(),anyString());
        TemplatePreviewMp4Service service=new TemplatePreviewMp4Service(repository,new TemplatePreviewStorage(r2),provider,catalog,mapper);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new RendererTemplatePreviewLowController(auth,service)).build();
        String endpoint="/internal/music-mv/v1/templates/t/versions/v/preview-mp4-low";
        mvc.perform(get(endpoint+"/source").header("X-Music-Mv-Template-Sync-Token","local")).andExpect(status().isOk());
        mvc.perform(post(endpoint+"/upload-session").header("X-Music-Mv-Template-Sync-Token","local")
                .contentType("application/json").content(mapper.writeValueAsBytes(request))).andExpect(status().isOk());
        Files.write(storage.resolve("uploaded.mp4"),bytes);
        mvc.perform(post(endpoint+"/complete").header("X-Music-Mv-Template-Sync-Token","local")
                .contentType("application/json").content(mapper.writeValueAsBytes(request))).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxShortEdge").value(480));
        mvc.perform(get(endpoint+"/source").header("X-Music-Mv-Template-Sync-Token","local")).andExpect(status().isOk())
                .andExpect(jsonPath("$.previewMp4Low.width").value(480))
                .andExpect(jsonPath("$.previewMp4Low.sha256").value(request.get("sha256")));
        assertArrayEquals(bytes,Files.readAllBytes(storage.resolve("uploaded.mp4")));
        verify(auth,times(4)).requireAuthorized("local");
        verify(repository,never()).publishPreviewMp4(anyString(),anyString(),anyString(),anyString(),
                anyString(),anyString(),anyLong(),anyInt(),anyInt(),anyDouble(),anyString());
    }
}
