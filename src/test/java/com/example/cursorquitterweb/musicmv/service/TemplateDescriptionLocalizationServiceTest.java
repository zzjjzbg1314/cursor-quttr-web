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

class TemplateDescriptionLocalizationServiceTest {
    static Map<String,String> source() {
        Map<String,String> values=new LinkedHashMap<>();values.put("zh-CN","唱片与水面倒影组合成柔和的音乐画面。");
        values.put("en","A spinning record sits above soft rippling reflections.");return values;
    }
    static Map<String,String> translated() {
        Map<String,String> values=new LinkedHashMap<>();
        values.put("zh-TW","唱片與水面倒影交織，呈現柔和的音樂畫面。");
        values.put("ja","回転するレコードと水面の反射が、やわらかな音楽の世界を演出します。");
        values.put("ko","회전하는 레코드와 수면의 반사가 부드러운 음악 장면을 연출합니다.");
        values.put("es","Un disco gira sobre suaves reflejos de agua.");
        values.put("pt-BR","Um disco gira sobre reflexos suaves na água.");
        values.put("fr","Un disque tourne au-dessus de doux reflets sur l’eau.");
        values.put("de","Eine Schallplatte dreht sich über sanften Spiegelungen im Wasser.");
        values.put("it","Un disco ruota sopra delicati riflessi sull’acqua.");return values;
    }
    static Map<String,String> complete() {Map<String,String> values=source();values.putAll(translated());return values;}
    static List<Map<String,Object>> rows() {
        List<Map<String,Object>> rows=TemplateTitleLocalizationServiceTest.rows(TemplateTitleLocalizationServiceTest.complete());
        for(Map<String,Object> row:rows) row.put("description",complete().get(row.get("locale")));return rows;
    }
    private String response(Map<String,String> values) throws Exception {
        return new ObjectMapper().writeValueAsString(Collections.singletonMap("choices",Collections.singletonList(Collections.singletonMap("message",Collections.singletonMap("content",new ObjectMapper().writeValueAsString(values))))));
    }
    @Test void translatesDescriptionsInOneRequestWithoutTranslatingTitles() throws Exception {
        RestTemplate client=new RestTemplate();MockRestServiceServer server=MockRestServiceServer.createServer(client);
        server.expect(requestTo("https://example.com"))
            .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("descriptions")))
            .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("targetLocales")))
            .andRespond(withSuccess(response(translated()),MediaType.APPLICATION_JSON));
        assertEquals(complete(),new TemplateTitleLocalizationService(client,"key","https://example.com","test")
            .prepareDescriptions(source(),source().get("zh-CN"),source().get("en")));server.verify();
    }
    @Test void completeUnchangedDescriptionsNeedNoProviderAndChangedSourceRetranslatesAll() {
        assertEquals(complete(),new TemplateTitleLocalizationService("","","").prepareDescriptions(complete(),source().get("zh-CN"),source().get("en")));
        assertEquals(8,TemplateTitleLocalizationService.missingDescriptions(complete(),"新的中文说明",source().get("en")).size());
        assertEquals(8,TemplateTitleLocalizationService.missingDescriptions(complete(),source().get("zh-CN"),"Changed description").size());
    }
    @Test void onlyMissingDescriptionIsTranslated() throws Exception {
        Map<String,String> existing=complete();existing.put("it","");
        RestTemplate client=new RestTemplate();MockRestServiceServer server=MockRestServiceServer.createServer(client);
        server.expect(requestTo("https://example.com"))
            .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("\"targetLocales\":[\"it\"]")))
            .andRespond(withSuccess(response(Collections.singletonMap("it",translated().get("it"))),MediaType.APPLICATION_JSON));
        assertEquals(complete(),new TemplateTitleLocalizationService(client,"key","https://example.com","test").prepareDescriptions(existing,source().get("zh-CN"),source().get("en")));server.verify();
    }
    @Test void rejectsMissingSourceAndConfigurationAndInvalidManualTranslations() {
        TemplateTitleLocalizationService service=new TemplateTitleLocalizationService("","","");
        assertEquals("TEMPLATE_DESCRIPTION_SOURCE_REQUIRED",assertThrows(ApiException.class,()->service.prepareDescriptions(source(),"",source().get("en"))).getCode());
        assertEquals("TEMPLATE_DESCRIPTION_AI_UNAVAILABLE",assertThrows(ApiException.class,()->service.prepareDescriptions(source(),source().get("zh-CN"),source().get("en"))).getCode());
        assertEquals(complete(),TemplateTitleLocalizationService.validateSuppliedDescriptions(source(),translated()));
        for(String invalid:Arrays.asList("",source().get("en"),"CapCut description","<script>",String.join("",Collections.nCopies(2001,"a")))) {
            Map<String,String> values=translated();values.put("it",invalid);
            assertThrows(ApiException.class,()->TemplateTitleLocalizationService.validateSuppliedDescriptions(source(),values));
        }
        Map<String,String> extra=translated();extra.put("en","Overwrite");assertThrows(ApiException.class,()->TemplateTitleLocalizationService.validateSuppliedDescriptions(source(),extra));
    }
    @Test void invalidProviderOutputFailsWithoutInventingTranslations() throws Exception {
        for(Map<String,String> values:Arrays.asList(Collections.<String,String>emptyMap(),Collections.singletonMap("it","English"))) {
            RestTemplate client=new RestTemplate();MockRestServiceServer server=MockRestServiceServer.createServer(client);
            server.expect(requestTo("https://example.com")).andRespond(withSuccess(response(values),MediaType.APPLICATION_JSON));
            assertEquals("TEMPLATE_DESCRIPTION_TRANSLATION_FAILED",assertThrows(ApiException.class,()->new TemplateTitleLocalizationService(client,"key","https://example.com","test").prepareDescriptions(source(),source().get("zh-CN"),source().get("en"))).getCode());server.verify();
        }
    }
}
