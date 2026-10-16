package com.example.cursorquitterweb.musicmv.service;

import com.example.cursorquitterweb.musicmv.dto.TemplateMediaUploadSessionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TemplateVideoR2UploadTest {
    private CloudflareTemplateMediaProvider provider(R2StorageService r2) {
        CloudflareTemplateMediaProvider p = new CloudflareTemplateMediaProvider(new ObjectMapper(),"https://unused.invalid",
                "","","","","","");
        p.setTemplateVideoStorage(r2);
        return p;
    }
    @Test void newVideosUseSignedR2PutWithoutStreamConfiguration() {
        R2StorageService r2=mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(true);
        when(r2.presignedPutUrl(anyString(),eq("video/mp4"),eq(123L),anyMap(),any())).thenReturn("https://r2.example/put");
        CloudflareTemplateMediaProvider p=provider(r2);
        TemplateMediaUploadSessionRequest request=new TemplateMediaUploadSessionRequest();
        request.setSourceSha256(String.join("",Collections.nCopies(64,"a")));
        request.setSourceSizeBytes(123L);
        CloudflareTemplateMediaProvider.UploadSession session=p.createTemplateVideoUpload("asset",request);
        assertEquals("cloudflare_r2",session.getProvider());
        assertEquals("PUT",session.getProviderDetails().get("uploadMethod"));
        assertEquals("https://r2.example/put",session.getUploadUrl());
        assertFalse(p.streamConfigured());
        assertTrue(p.templateVideoStorageConfigured());
    }
    @Test void completedVideoRequiresMatchingBytesTypeAndHash() {
        R2StorageService r2=mock(R2StorageService.class);
        when(r2.isConfigured()).thenReturn(true);
        when(r2.objectInfo("key")).thenReturn(new R2StorageService.ObjectInfo(123L,"video/mp4",Collections.singletonMap("sha256","hash")));
        CloudflareTemplateMediaProvider p=provider(r2);
        assertEquals("ready",p.templateVideoState("key","hash",123).getStatus());
        assertThrows(IllegalStateException.class,()->p.templateVideoState("key","wrong",123));
        assertThrows(IllegalStateException.class,()->p.templateVideoState("key","hash",124));
    }
    @Test void r2DeliveryIsSignedAtReadTimeAndHistoricalStreamDetailsAreRetained() {
        R2StorageService r2=mock(R2StorageService.class);when(r2.isConfigured()).thenReturn(true);
        when(r2.presignedGetUrl(eq("key"),any())).thenReturn("https://r2.example/fresh");
        CloudflareTemplateMediaProvider p=provider(r2);
        Map<String,Object> old=Collections.<String,Object>singletonMap("playbackUrl","old");
        assertEquals("https://r2.example/fresh",p.resolveDeliveryDetails("cloudflare_r2","key",old).get("playbackUrl"));
        assertEquals("old",p.resolveDeliveryDetails("cloudflare_stream","legacy",old).get("playbackUrl"));
    }
}
