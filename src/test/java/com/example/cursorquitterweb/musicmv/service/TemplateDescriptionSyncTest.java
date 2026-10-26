package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TemplateDescriptionSyncTest {
    private final MusicMvTemplateCatalogRepository repository=mock(MusicMvTemplateCatalogRepository.class);
    private final TemplateTitleLocalizationService translator=mock(TemplateTitleLocalizationService.class);
    private final MusicMvTemplateCatalogService service=new MusicMvTemplateCatalogService(repository,mock(CloudflareTemplateMediaProvider.class),mock(TemplateRuntimePackageService.class),mock(D1DatabaseClient.class),new ObjectMapper(),translator);
    private List<Map<String,Object>> descriptionRows(boolean complete) {
        Map<String,String> values=complete?TemplateDescriptionLocalizationServiceTest.complete():TemplateDescriptionLocalizationServiceTest.source();
        List<Map<String,Object>> rows=new ArrayList<>();values.forEach((locale,text)->{Map<String,Object> row=new HashMap<>();row.put("locale",locale);row.put("description",text);rows.add(row);});return rows;
    }
    private void existing() {
        Map<String,Object> template=new HashMap<>();template.put("revision",7);template.put("template_id","tpl");
        when(repository.template("tpl")).thenReturn(template);
        when(repository.translations("tpl")).thenReturn(descriptionRows(false));
        when(repository.updateDescriptions(eq("tpl"),eq(7L),anyString(),anyString(),anyMap())).thenReturn(true);
    }
    @Test void catalogPreservesEverySupportedLocale() {
        for(String locale:TemplateTitleLocalizationService.LOCALES)
            assertEquals(locale,org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeLocale",locale.toLowerCase(java.util.Locale.ROOT)));
        assertEquals("en",org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeLocale","unknown"));
    }

    @Test void dryRunReturnsBackupAndMissingLanguagesWithoutProviderOrWrites() {
        existing();Map<String,Object> result=service.synchronizeDescriptions("tpl",true,7L,null,null);
        assertEquals(8,((List<?>)result.get("missingLocales")).size());assertEquals(false,result.get("changed"));assertEquals(2,((List<?>)result.get("backup")).size());
        verifyNoInteractions(translator);verify(repository,never()).updateDescriptions(anyString(),anyLong(),anyString(),anyString(),anyMap());
    }
    @Test void suppliedDryRunAndApplyPreserveSourcesAndSkipProvider() {
        existing();Map<String,Object> preview=service.synchronizeDescriptions("tpl",true,7L,TemplateDescriptionLocalizationServiceTest.translated(),TemplateDescriptionLocalizationServiceTest.source());
        assertEquals(TemplateDescriptionLocalizationServiceTest.complete(),preview.get("after"));assertEquals(true,preview.get("wouldChange"));
        Map<String,Object> applied=service.synchronizeDescriptions("tpl",false,7L,TemplateDescriptionLocalizationServiceTest.translated(),TemplateDescriptionLocalizationServiceTest.source());
        assertEquals(true,applied.get("changed"));assertEquals(8L,applied.get("revision"));
        verifyNoInteractions(translator);verify(repository).updateDescriptions("tpl",7L,TemplateDescriptionLocalizationServiceTest.source().get("zh-CN"),TemplateDescriptionLocalizationServiceTest.source().get("en"),TemplateDescriptionLocalizationServiceTest.complete());
        verify(repository,never()).publish(anyString(),anyString());
    }
    @Test void refusesStaleOrUnboundManualTranslationsBeforeWrites() {
        existing();
        assertThrows(ApiException.class,()->service.synchronizeDescriptions("tpl",false,8L,TemplateDescriptionLocalizationServiceTest.translated(),TemplateDescriptionLocalizationServiceTest.source()));
        assertThrows(ApiException.class,()->service.synchronizeDescriptions("tpl",false,null,TemplateDescriptionLocalizationServiceTest.translated(),TemplateDescriptionLocalizationServiceTest.source()));
        Map<String,String> stale=TemplateDescriptionLocalizationServiceTest.source();stale.put("en","Old title");
        assertThrows(ApiException.class,()->service.synchronizeDescriptions("tpl",false,7L,TemplateDescriptionLocalizationServiceTest.translated(),stale));
        verifyNoInteractions(translator);verify(repository,never()).updateDescriptions(anyString(),anyLong(),anyString(),anyString(),anyMap());
    }
    @Test void providerFailureDoesNotWriteAndConcurrentChangesAreRejected() {
        existing();when(translator.prepareDescriptions(anyMap(),anyString(),anyString())).thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"TEMPLATE_DESCRIPTION_TRANSLATION_FAILED","failed"));
        assertThrows(ApiException.class,()->service.synchronizeDescriptions("tpl",false,7L,null,null));verify(repository,never()).updateDescriptions(anyString(),anyLong(),anyString(),anyString(),anyMap());
        when(repository.updateDescriptions(anyString(),anyLong(),anyString(),anyString(),anyMap())).thenReturn(false);
        assertEquals("TEMPLATE_DESCRIPTION_SOURCE_CHANGED",assertThrows(ApiException.class,()->service.synchronizeDescriptions("tpl",false,7L,TemplateDescriptionLocalizationServiceTest.translated(),TemplateDescriptionLocalizationServiceTest.source())).getCode());
    }
    @Test void completeTitlesDoNotInvokeProviderAgain() {
        existing();when(repository.translations("tpl")).thenReturn(descriptionRows(true));
        assertEquals(false,service.synchronizeDescriptions("tpl",false,7L,null,null).get("changed"));verifyNoInteractions(translator);
        verify(repository,never()).updateDescriptions(anyString(),anyLong(),anyString(),anyString(),anyMap());
    }
    @Test void promotionPreparesBothFieldsAndDoesNotWriteWhenDescriptionTranslationFails() {
        existing();
        com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest request=new com.example.cursorquitterweb.musicmv.dto.TemplatePromotionRequest();
        request.setTemplateId("tpl");request.setNameZh("水面倒影黑胶");request.setNameEn("Ripple Reflection Vinyl");
        request.setDescriptionZh(TemplateDescriptionLocalizationServiceTest.source().get("zh-CN"));
        request.setDescriptionEn(TemplateDescriptionLocalizationServiceTest.source().get("en"));
        when(translator.prepare(anyMap(),anyString(),anyString())).thenReturn(TemplateTitleLocalizationServiceTest.complete());
        when(translator.prepareDescriptions(anyMap(),anyString(),anyString())).thenReturn(TemplateDescriptionLocalizationServiceTest.complete());
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"preparePromotionTitles",request);
        assertEquals(TemplateTitleLocalizationServiceTest.complete(),request.getLocalizedTitles());
        assertEquals(TemplateDescriptionLocalizationServiceTest.complete(),request.getLocalizedDescriptions());
        when(translator.prepareDescriptions(anyMap(),anyString(),anyString())).thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"TEMPLATE_DESCRIPTION_TRANSLATION_FAILED","failed"));
        assertThrows(ApiException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"preparePromotionTitles",request));
        verify(repository,never()).promote(any(),anyString(),anyInt(),anyString(),anyString(),anyString());
    }
}
