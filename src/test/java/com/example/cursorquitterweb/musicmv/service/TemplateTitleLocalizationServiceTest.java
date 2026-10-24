package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TemplateTitleLocalizationServiceTest {
    static Map<String,String> translated() {
        Map<String,String> result=new LinkedHashMap<>();
        result.put("zh-TW","水面倒影黑膠");result.put("ja","水面に映るレコード");result.put("ko","수면에 비친 레코드");
        result.put("es","Vinilo entre reflejos");result.put("pt-BR","Vinil entre reflexos");result.put("fr","Vinyle aux reflets d’eau");
        result.put("de","Schallplatte im Wasserspiegel");result.put("it","Vinile tra i riflessi");return result;
    }
    static Map<String,String> source() { Map<String,String> result=new LinkedHashMap<>();result.put("zh-CN","水面倒影黑胶");result.put("en","Ripple Reflection Vinyl");return result; }
    static Map<String,String> complete() {Map<String,String> result=source();result.putAll(translated());return result;}
    static List<Map<String,Object>> rows(Map<String,String> titles) {
        List<Map<String,Object>> result=new ArrayList<>();
        titles.forEach((locale,title)->{Map<String,Object> row=new LinkedHashMap<>();row.put("locale",locale);row.put("name",title);row.put("description",locale.equals("zh-CN")?"唱片与水面倒影组合成柔和的音乐画面。":locale.equals("en")?"A spinning record sits above soft rippling reflections.":"");result.add(row);});return result;
    }
    @Test void translatesEightTitlesInOneRequestAndPreservesSources() throws Exception {
        RestTemplate client=new RestTemplate();MockRestServiceServer server=MockRestServiceServer.createServer(client);
        String payload=new ObjectMapper().writeValueAsString(Collections.singletonMap("choices",Collections.singletonList(Collections.singletonMap("message",Collections.singletonMap("content",new ObjectMapper().writeValueAsString(translated()))))));
        server.expect(requestTo("https://example.com"))
            .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("targetLocales")))
            .andRespond(withSuccess(payload,MediaType.APPLICATION_JSON));
        assertEquals(complete(),new TemplateTitleLocalizationService(client,"key","https://example.com","test").prepare(source(),source().get("zh-CN"),source().get("en")));server.verify();
    }
    @Test void completeUnchangedTitlesNeedNoProviderConfiguration() {
        assertEquals(complete(),new TemplateTitleLocalizationService("","","").prepare(complete(),source().get("zh-CN"),source().get("en")));
        assertEquals(8,TemplateTitleLocalizationService.missing(complete(),"新的中文标题",source().get("en")).size());
        assertEquals(8,TemplateTitleLocalizationService.missing(complete(),source().get("zh-CN"),"A new English title").size());
    }
    @Test void onlyMissingTitleIsRequestedAndExistingTranslationsRemain() throws Exception {
        Map<String,String> existing=complete();existing.remove("it");
        RestTemplate client=new RestTemplate();MockRestServiceServer server=MockRestServiceServer.createServer(client);
        String payload=new ObjectMapper().writeValueAsString(Collections.singletonMap("choices",Collections.singletonList(Collections.singletonMap("message",Collections.singletonMap("content","{\"it\":\"Vinile tra i riflessi\"}")))));
        server.expect(requestTo("https://example.com"))
            .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("\"targetLocales\":[\"it\"]")))
            .andRespond(withSuccess(payload,MediaType.APPLICATION_JSON));
        assertEquals(complete(),new TemplateTitleLocalizationService(client,"key","https://example.com","test").prepare(existing,source().get("zh-CN"),source().get("en")));server.verify();
    }

    @Test void missingConfigurationAndInvalidSourcesFailExplicitly() {
        TemplateTitleLocalizationService service=new TemplateTitleLocalizationService("","","");
        assertEquals("TEMPLATE_TITLE_AI_UNAVAILABLE",assertThrows(ApiException.class,()->service.prepare(source(),source().get("zh-CN"),source().get("en"))).getCode());
        assertEquals("TEMPLATE_TITLE_SOURCE_REQUIRED",assertThrows(ApiException.class,()->service.prepare(source(),"",source().get("en"))).getCode());
    }
    @Test void incompleteOrInvalidProviderResponseNeverFabricatesTitles() {
        for(String payload:Arrays.asList("{}","{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}","{\"choices\":[{\"message\":{\"content\":\"not json\"}}]}")) {
            RestTemplate client=new RestTemplate();MockRestServiceServer server=MockRestServiceServer.createServer(client);
            server.expect(requestTo("https://example.com")).andRespond(withSuccess(payload,MediaType.APPLICATION_JSON));
            assertEquals("TEMPLATE_TITLE_TRANSLATION_FAILED",assertThrows(ApiException.class,()->new TemplateTitleLocalizationService(client,"key","https://example.com","test").prepare(source(),source().get("zh-CN"),source().get("en"))).getCode());server.verify();
        }
    }
    @Test void suppliedTitlesRequireExactlyEightValidLanguages() {
        assertEquals(complete(),TemplateTitleLocalizationService.validateSupplied(source(),translated()));
        Map<String,String> wrong=translated();wrong.put("en","Overwrite");final Map<String,String> extra=wrong;assertThrows(ApiException.class,()->TemplateTitleLocalizationService.validateSupplied(source(),extra));
        wrong=translated();wrong.put("ja","English title");final Map<String,String> wrongScript=wrong;assertThrows(ApiException.class,()->TemplateTitleLocalizationService.validateSupplied(source(),wrongScript));
        Map<String,String> missing=translated();missing.remove("it");assertThrows(ApiException.class,()->TemplateTitleLocalizationService.validateSupplied(source(),missing));
    }
}
