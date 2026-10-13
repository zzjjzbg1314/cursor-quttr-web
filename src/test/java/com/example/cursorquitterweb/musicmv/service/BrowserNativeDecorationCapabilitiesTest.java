package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.cursorquitterweb.musicmv.service.BrowserNativeSceneCapabilitiesTest.row;

@SuppressWarnings("unchecked")
class BrowserNativeDecorationCapabilitiesTest {
    static Map<String,Object> fixture() {
        Map<String,Object> scene=BrowserNativeSceneCapabilitiesTest.fixture();
        Map<String,Object> delivery=(Map<String,Object>)scene.get("runtimeDelivery"),descriptor=(Map<String,Object>)delivery.get("nativeEngine");
        descriptor.put("schemaVersion","browser-native-scene-runtime-v4");
        descriptor.put("videoAudioPolicy","external_music_only");descriptor.put("scriptTemplatePolicy","original_text_v2");
        descriptor.put("files",Arrays.asList("effect/config.json","script/config.json","script/content.json","script/js/main.js","script/js/template/template.js","anim/config.json"));
        descriptor.put("bindings",Arrays.asList(((List<?>)descriptor.get("bindings")).get(0),row("resourceId","script","kind","script_template","path","script/"),row("resourceId","anim","kind","animation","path","anim/")));
        delivery.put("resources",Arrays.asList(row("resourceId","effect"),row("resourceId","script"),row("resourceId","anim")));
        Map<String,Object> decoration=row("name","decoration","type","sticker","start","0","duration","4000000","originalSize",Arrays.asList(100,100),
                "clip",row("alpha",1,"rotation",0,"scale",row("x",1,"y",1),"transform",row("x",0,"y",0),"flip",row("horizontal",false,"vertical",false)),
                "animations",Collections.singletonList(row("type","loop","resourceId","anim","duration","1000000")));
        Map<String,Object> source=row("schemaVersion","native-script-template-source-v1","status","source_projected","buildMode","original_resource",
                "segmentId","parent","resourceId","original","runtimeResourceId","script","start","0","duration","4000000",
                "attachments",Collections.singletonList(row("id","child")),"decorations",Collections.singletonList(decoration),
                "resourceIds",Collections.singletonList("anim"),"dependencies",Collections.singletonList(row("type","sticker","resourceId","anim")));
        scene.put("textLayers",Collections.singletonList(row("segmentId","parent__child","sourceSegmentId","parent","templateAttachmentId","child","templateResourceId","original","nativeScriptTemplateSource",source)));
        scene.put("layers",Arrays.asList(((List<?>)scene.get("layers")).get(0),row("layerId","parent__child","type","text","nativeScriptTemplateSource",source)));
        Map<String,Object> capability=(Map<String,Object>)scene.get("capability");
        capability.put("blockingFeatures",Arrays.asList("post_effects","text_template_decorations"));
        capability.put("executionCapabilities",Arrays.asList(((List<?>)capability.get("executionCapabilities")).get(0),row("feature","text_template_decorations","declaredCount",1,"executableCount",0)));
        return scene;
    }
    @Test void acceptsBoundOriginalDecorationsWithoutMutatingScene() throws Exception {
        Map<String,Object> scene=fixture();String before=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(scene);
        assertTrue(BrowserNativeSceneCapabilities.ownsEffectBlockers(scene));
        assertEquals(before,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(scene));
    }
    @Test void rejectsIncompleteOwnershipAndUnknownDecorations() {
        for(String variant:Arrays.asList("policy","binding","parent","child","source","count","duplicateSummary","duration","start","clip","size","type","animation","dependency","duplicateAnimation","unknownBlocker")) {
            Map<String,Object> scene=fixture(),delivery=(Map<String,Object>)scene.get("runtimeDelivery"),descriptor=(Map<String,Object>)delivery.get("nativeEngine");
            Map<String,Object> text=(Map<String,Object>)((List<?>)scene.get("textLayers")).get(0),source=(Map<String,Object>)text.get("nativeScriptTemplateSource");
            Map<String,Object> decoration=(Map<String,Object>)((List<?>)source.get("decorations")).get(0),animation=(Map<String,Object>)((List<?>)decoration.get("animations")).get(0);
            Map<String,Object> capability=(Map<String,Object>)scene.get("capability"),summary=(Map<String,Object>)((List<?>)capability.get("executionCapabilities")).get(1);
            if(variant.equals("policy"))descriptor.remove("scriptTemplatePolicy");
            if(variant.equals("binding"))source.put("runtimeResourceId","missing");
            if(variant.equals("parent"))text.put("sourceSegmentId","other");
            if(variant.equals("child"))text.put("templateAttachmentId","other");
            if(variant.equals("source"))((Map<String,Object>)((List<?>)scene.get("layers")).get(1)).put("nativeScriptTemplateSource",Collections.emptyMap());
            if(variant.equals("count"))summary.put("declaredCount",2);
            if(variant.equals("duplicateSummary"))capability.put("executionCapabilities",Arrays.asList(summary,summary));
            if(variant.equals("duration"))decoration.put("duration","0");
            if(variant.equals("start"))decoration.put("start","-1");
            if(variant.equals("clip"))((Map<String,Object>)decoration.get("clip")).put("alpha",Double.NaN);
            if(variant.equals("size"))decoration.put("originalSize",Arrays.asList(0,100));
            if(variant.equals("type"))decoration.put("type","future");
            if(variant.equals("animation"))animation.put("resourceId","missing");
            if(variant.equals("dependency"))source.put("dependencies",Collections.emptyList());
            if(variant.equals("duplicateAnimation"))decoration.put("animations",Arrays.asList(animation,animation));
            if(variant.equals("unknownBlocker"))capability.put("blockingFeatures",Arrays.asList("text_template_decorations","future"));
            assertFalse(BrowserNativeSceneCapabilities.ownsEffectBlockers(scene),variant);
        }
    }
}
