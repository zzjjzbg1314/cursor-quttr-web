package com.example.cursorquitterweb.musicmv.service;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.example.cursorquitterweb.musicmv.support.ApiException;
class TemplatePublicationCopyTest {
    private List<Map<String,Object>> copy(String enName, String enDescription) {
        Map<String,Object> zh=new HashMap<>(), en=new HashMap<>();
        zh.put("locale","zh-CN");zh.put("name","水面倒影黑胶");zh.put("description","唱片与水面倒影组合成柔和的音乐画面。");
        en.put("locale","en");en.put("name",enName);en.put("description",enDescription);
        List<Map<String,Object>> rows=new ArrayList<>(Arrays.asList(zh,en));
        for(Map.Entry<String,String> title:TemplateTitleLocalizationServiceTest.translated().entrySet()) {
            Map<String,Object> row=new HashMap<>();row.put("locale",title.getKey());row.put("name",title.getValue());row.put("description",TemplateDescriptionLocalizationServiceTest.translated().get(title.getKey()));rows.add(row);
        }
        return rows;
    }
    @Test void rejectsMissingTargetTitle() {
        List<Map<String,Object>> rows=copy("Ripple Reflection Vinyl","A spinning record sits above soft rippling reflections.");
        rows.remove(rows.size()-1);
        assertEquals("TEMPLATE_MULTILINGUAL_TITLES_REQUIRED",assertThrows(ApiException.class,()->TemplatePublicationCopy.requireComplete(rows)).getCode());
    }
    @Test void rejectsMissingTargetDescription() {
        List<Map<String,Object>> rows=copy("Ripple Reflection Vinyl","A spinning record sits above soft rippling reflections.");
        rows.get(2).put("description","");
        assertEquals("TEMPLATE_MULTILINGUAL_DESCRIPTIONS_REQUIRED",assertThrows(ApiException.class,()->TemplatePublicationCopy.requireComplete(rows)).getCode());
    }
    @Test void requiresBothLanguagesAndSpecificNames() {
        assertThrows(ApiException.class,()->TemplatePublicationCopy.requireComplete(Collections.emptyList()));
        String desc="A spinning record sits above soft rippling reflections.";
        assertDoesNotThrow(()->TemplatePublicationCopy.requireComplete(copy("Ripple Reflection Vinyl",desc)));
        for(String name:Arrays.asList("Music Template","0923 (1)","水面倒影黑胶","CapCut Vinyl"))
            assertThrows(ApiException.class,()->TemplatePublicationCopy.requireComplete(copy(name,desc)));
        assertThrows(ApiException.class,()->TemplatePublicationCopy.requireComplete(copy("Ripple Reflection Vinyl","")));
        assertThrows(ApiException.class,()->TemplatePublicationCopy.requireComplete(copy("Ripple Reflection Vinyl","唱片与水面倒影组合成柔和的音乐画面。")));
    }
}
