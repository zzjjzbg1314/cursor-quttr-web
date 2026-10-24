package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TemplateTitleSyncTest {
    private final MusicMvTemplateCatalogRepository repository=mock(MusicMvTemplateCatalogRepository.class);
    private final TemplateTitleLocalizationService translator=mock(TemplateTitleLocalizationService.class);
    private final MusicMvTemplateCatalogService service=new MusicMvTemplateCatalogService(repository,mock(CloudflareTemplateMediaProvider.class),mock(TemplateRuntimePackageService.class),mock(D1DatabaseClient.class),new ObjectMapper(),translator);
    private void existing() {
        Map<String,Object> template=new HashMap<>();template.put("revision",7);template.put("template_id","tpl");
        when(repository.template("tpl")).thenReturn(template);
        when(repository.translations("tpl")).thenReturn(TemplateTitleLocalizationServiceTest.rows(TemplateTitleLocalizationServiceTest.source()));
        when(repository.updateTitles(eq("tpl"),eq(7L),anyString(),anyString(),anyMap())).thenReturn(true);
    }
    @Test void catalogPreservesEverySupportedLocale() {
        for(String locale:TemplateTitleLocalizationService.LOCALES)
            assertEquals(locale,org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeLocale",locale.toLowerCase(java.util.Locale.ROOT)));
        assertEquals("en",org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"normalizeLocale","unknown"));
    }

    @Test void dryRunReturnsBackupAndMissingLanguagesWithoutProviderOrWrites() {
        existing();Map<String,Object> result=service.synchronizeTitles("tpl",true,7L);
        assertEquals(8,((List<?>)result.get("missingLocales")).size());assertEquals(false,result.get("changed"));assertEquals(2,((List<?>)result.get("backup")).size());
        verifyNoInteractions(translator);verify(repository,never()).updateTitles(anyString(),anyLong(),anyString(),anyString(),anyMap());
    }
    @Test void suppliedDryRunAndApplyPreserveSourcesAndSkipProvider() {
        existing();Map<String,Object> preview=service.synchronizeTitles("tpl",true,7L,TemplateTitleLocalizationServiceTest.translated(),TemplateTitleLocalizationServiceTest.source());
        assertEquals(TemplateTitleLocalizationServiceTest.complete(),preview.get("after"));assertEquals(true,preview.get("wouldChange"));
        Map<String,Object> applied=service.synchronizeTitles("tpl",false,7L,TemplateTitleLocalizationServiceTest.translated(),TemplateTitleLocalizationServiceTest.source());
        assertEquals(true,applied.get("changed"));assertEquals(8L,applied.get("revision"));
        verifyNoInteractions(translator);verify(repository).updateTitles("tpl",7L,"水面倒影黑胶","Ripple Reflection Vinyl",TemplateTitleLocalizationServiceTest.complete());
        verify(repository,never()).publish(anyString(),anyString());
    }
    @Test void refusesStaleOrUnboundManualTranslationsBeforeWrites() {
        existing();
        assertThrows(ApiException.class,()->service.synchronizeTitles("tpl",false,8L,TemplateTitleLocalizationServiceTest.translated(),TemplateTitleLocalizationServiceTest.source()));
        assertThrows(ApiException.class,()->service.synchronizeTitles("tpl",false,null,TemplateTitleLocalizationServiceTest.translated(),TemplateTitleLocalizationServiceTest.source()));
        Map<String,String> stale=TemplateTitleLocalizationServiceTest.source();stale.put("en","Old title");
        assertThrows(ApiException.class,()->service.synchronizeTitles("tpl",false,7L,TemplateTitleLocalizationServiceTest.translated(),stale));
        verifyNoInteractions(translator);verify(repository,never()).updateTitles(anyString(),anyLong(),anyString(),anyString(),anyMap());
    }
    @Test void providerFailureDoesNotWriteAndConcurrentChangesAreRejected() {
        existing();when(translator.prepare(anyMap(),anyString(),anyString())).thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"TEMPLATE_TITLE_TRANSLATION_FAILED","failed"));
        assertThrows(ApiException.class,()->service.synchronizeTitles("tpl",false,7L));verify(repository,never()).updateTitles(anyString(),anyLong(),anyString(),anyString(),anyMap());
        when(repository.updateTitles(anyString(),anyLong(),anyString(),anyString(),anyMap())).thenReturn(false);
        assertEquals("TEMPLATE_TITLE_SOURCE_CHANGED",assertThrows(ApiException.class,()->service.synchronizeTitles("tpl",false,7L,TemplateTitleLocalizationServiceTest.translated(),TemplateTitleLocalizationServiceTest.source())).getCode());
    }
    @Test void completeTitlesDoNotInvokeProviderAgain() {
        existing();when(repository.translations("tpl")).thenReturn(TemplateTitleLocalizationServiceTest.rows(TemplateTitleLocalizationServiceTest.complete()));
        assertEquals(false,service.synchronizeTitles("tpl",false,7L).get("changed"));verifyNoInteractions(translator);
        verify(repository,never()).updateTitles(anyString(),anyLong(),anyString(),anyString(),anyMap());
    }
}
