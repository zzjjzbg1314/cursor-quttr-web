package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class BrowserNativeScriptDependencyContractTest {
    static Map<String,Object> row(Object... pairs) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;
    }
    Map<String,Object> descriptor() {
        Map<String,Object> d=BrowserNativeRuntimeContractTest.descriptor();
        d.put("schemaVersion","browser-native-scene-runtime-v4");d.put("videoAudioPolicy","external_music_only");d.put("scriptTemplatePolicy","original_text_v2");
        d.put("bindings",Arrays.asList(row("resourceId","parent","path","parent/","kind","script_template"),row("resourceId","sticker","path","sticker/","kind","sticker")));
        d.put("files",Arrays.asList("parent/config.json","parent/content.json","parent/js/main.js","parent/js/template/template.js","sticker/config.json","sticker/infoSticker.lua"));return d;
    }
    Map<String,Object> source() {return row("schemaVersion","native-script-template-source-v1","status","source_projected","buildMode","original_resource","runtimeResourceId","parent","segmentId","segment","resourceId","material",
            "resourceIds",Collections.singletonList("sticker"),"dependencies",Collections.singletonList(row("resourceId","sticker","type","default")),"attachments",Collections.singletonList(row("id","child")));}
    Map<String,Object> scene(Map<String,Object> source) {return row("textLayers",Collections.singletonList(row("segmentId","segment__child","sourceSegmentId","segment","templateResourceId","material","templateAttachmentId","child","nativeScriptTemplateSource",source)));}
    final Set<String> ids=new HashSet<>(Arrays.asList("parent","sticker"));
    @Test void originalScriptCanOwnDeliveredStickerWithoutClaimingIndependentStickerLayers() {
        Map<String,Object> descriptor=descriptor();
        Map<String,Object> result=BrowserNativeRuntimeContract.validate(descriptor,ids,scene(source()));
        assertEquals(descriptor,result);assertFalse(result.containsKey("stickerPolicy"));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(descriptor,ids));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(descriptor,Collections.singleton("parent"),scene(source())));
    }
    @Test void invalidParentOrUnreferencedStickerCannotUseScriptPolicyAsBlanketPermission() {
        for(String field:Arrays.asList("schemaVersion","status","buildMode","runtimeResourceId","segmentId","resourceId")) {
            Map<String,Object> s=source();s.put(field,"unknown");assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(descriptor(),ids,scene(s)));
        }
        for(String field:Arrays.asList("resourceIds","dependencies","attachments")) {
            Map<String,Object> s=source();s.put(field,Collections.emptyList());assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(descriptor(),ids,scene(s)));
        }
        Map<String,Object> s=source();s.put("dependencies",Collections.singletonList(row("resourceId","sticker","type","sticker")));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(descriptor(),ids,scene(s)));
    }
    @Test void missingEntrypointsAndDynamicScriptsRemainBlocked() {
        Map<String,Object> d=descriptor();d.put("files",Arrays.asList("parent/config.json","parent/content.json","parent/js/main.js","parent/js/template/template.js","sticker/config.json"));
        assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(d,ids,scene(source())));
        d.putAll(descriptor());d.put("scriptTemplatePolicy","dynamic_text_v1");assertThrows(ApiException.class,()->BrowserNativeRuntimeContract.validate(d,ids,scene(source())));
    }
}
