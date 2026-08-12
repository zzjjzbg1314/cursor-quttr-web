package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class MusicMvTemplateCatalogServiceTest {
    private MusicMvTemplateCatalogRepository repository;
    private MusicMvTemplateCatalogService service;

    @BeforeEach
    void setUp() {
        repository = mock(MusicMvTemplateCatalogRepository.class);
        service = new MusicMvTemplateCatalogService(repository,
                mock(CloudflareTemplateMediaProvider.class), mock(D1DatabaseClient.class),
                new ObjectMapper());
        Map<String, Object> category = new LinkedHashMap<String, Object>();
        category.put("enabled", Integer.valueOf(1));
        when(repository.category("birthday")).thenReturn(category);
    }

    @Test
    void promotesExactNativeEvidenceIntoNewImmutableVersion() {
        when(repository.versionByValidationJob("native_1")).thenReturn(null);
        when(repository.nextVersionNumber("tpl_1")).thenReturn(Integer.valueOf(3));
        Map<String, Object> result = service.promote(validPromotion());

        assertEquals("tpl_1", result.get("templateId"));
        assertEquals("validated", result.get("status"));
        assertFalse((Boolean) result.get("idempotentReplay"));
        verify(repository).promote(any(TemplatePromotionRequest.class), anyString(),
                anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsValidationThatUsedMoreThanOneVideoEncode() {
        TemplatePromotionRequest request = validPromotion();
        request.setVideoEncodeCount(Integer.valueOf(2));

        ApiException error = assertThrows(ApiException.class, () -> service.promote(request));
        assertEquals("TEMPLATE_VALIDATION_NOT_EXACT", error.getCode());
        verify(repository, never()).promote(any(), anyString(), anyInt(), anyString(),
                anyString(), anyString());
    }

    @Test
    void refusesPublishUntilBothImagesAndStreamMediaAreReady() {
        Map<String, Object> template = row("template_id", "tpl_1");
        Map<String, Object> version = row("validation_status", "exact");
        version.put("source_availability", "available");
        when(repository.template("tpl_1")).thenReturn(template);
        when(repository.version("tpl_1", "tplver_1")).thenReturn(version);
        when(repository.mediaByRole("tplver_1", "cover")).thenReturn(row("status", "ready"));
        when(repository.mediaByRole("tplver_1", "full_mv")).thenReturn(row("status", "processing"));

        ApiException error = assertThrows(ApiException.class,
                () -> service.publish("tpl_1", "tplver_1"));
        assertEquals("TEMPLATE_MEDIA_NOT_READY", error.getCode());
        verify(repository, never()).publish(anyString(), anyString());
    }

    private TemplatePromotionRequest validPromotion() {
        TemplatePromotionRequest request = new TemplatePromotionRequest();
        request.setTemplateId("tpl_1");
        request.setSlug("birthday-one");
        request.setCategoryKey("birthday");
        request.setNameZh("生日");
        request.setNameEn("Birthday");
        request.setWidth(Integer.valueOf(1080));
        request.setHeight(Integer.valueOf(1920));
        request.setFps(Double.valueOf(30));
        request.setDurationSeconds(Double.valueOf(180));
        request.setBaseDurationSeconds(Double.valueOf(13));
        request.setCycleDurationSeconds(Double.valueOf(13));
        request.setValidationRenderJobId("native_1");
        request.setValidationMasterSha256(hash('a'));
        request.setDraftSnapshotSha256(hash('b'));
        request.setTimelineEvidenceSha256(hash('c'));
        request.setNativeRuntimeVersion("9.2.0");
        request.setNativeRuntimeSha256(hash('d'));
        request.setRendererVersion("renderer-1");
        request.setSourceNodeId("mac-1");
        request.setSourceLocalKey("templates/tpl_1/tplver_1");
        request.setSemanticIntegrity("exact");
        request.setVideoEncodeCount(Integer.valueOf(1));
        request.setIntermediateVideoCount(Integer.valueOf(0));
        request.setExternalResourceReadCount(Integer.valueOf(0));
        request.setMissingResourceCount(Integer.valueOf(0));
        request.setValidationElapsedSeconds(Double.valueOf(12));
        TemplatePromotionRequest.Slot slot = new TemplatePromotionRequest.Slot();
        slot.setSlotKey("photo_1");
        slot.setSlotType("image");
        slot.setDisplayName("照片 1");
        slot.setTimelineOrder(Integer.valueOf(0));
        slot.setCropPolicy("fill");
        slot.setRepeatPolicy("cycle");
        request.getSlots().add(slot);
        return request;
    }

    private Map<String, Object> row(String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<String, Object>(); row.put(key, value); return row;
    }

    private String hash(char value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 64; i++) result.append(value);
        return result.toString();
    }
}
