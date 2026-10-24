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
        return Arrays.asList(zh,en);
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
