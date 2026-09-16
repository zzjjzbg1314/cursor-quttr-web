package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrowserNativeStickerContractTest {
    static Map<String,Object> map(Object... entries) {Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<entries.length;i+=2)out.put((String)entries[i],entries[i+1]);return out;}
    static Map<String,Object> source() {
        return map("schemaVersion","native-sticker-source-v1","status","source_projected","attachmentStatus","bound",
            "segmentId","s","materialId","m","resourceId","sticker","materialType","sticker","unicode","","trackId","track",
            "startRaw","0","durationRaw","1000000","offsetRaw","0","renderIndex",14000,"trackRenderIndex",0,"visible",true,
            "commonKeyframes",Collections.emptyList(),"animationMaterialIds",Collections.singletonList("animation-material"),"animations",Collections.emptyList(),
            "clip",map("alpha",1,"rotation",0,"scale",map("x",.2,"y",.2),"transform",map("x",0,"y",0),"flip",map("horizontal",false,"vertical",false)));
    }
    static Map<String,Object> scene(Map<String,Object> source) {return map("layers",Collections.singletonList(map("type","sticker","layerId","s","segmentId","s",
        "renderIndex",14000,"trackRenderIndex",0,"targetStartSeconds",0,"targetDurationSeconds",1,"effects",Collections.emptyList(),"blendMode","source-over","nativeStickerSource",source)));}
    static Map<String,Object> descriptor(){return map("schemaVersion","browser-native-scene-runtime-v4","stickerPolicy","original_resources_v1","layerMode",0,
        "bindings",Arrays.asList(map("resourceId","sticker","path","sticker/","kind","sticker"),map("resourceId","animation","path","animation/","kind","animation")));}
    @Test void preservesSourceAndChecksEveryBoundSticker() {
        Map<String,Object> source=source();String before=source.toString();
        assertEquals(Collections.singleton("s"),BrowserNativeStickerContract.validate(scene(source),descriptor()));assertEquals(before,source.toString());
        Map<String,Object> descriptor=descriptor();descriptor.remove("stickerPolicy");
        assertTrue(BrowserNativeStickerContract.validate(Collections.emptyMap(),descriptor).isEmpty());
    }
    @Test void unknownSourceTimingResourcesAndKeyframesRemainBlocked() {
        List<Consumer<Map<String,Object>>> mutations=Arrays.asList(
            s->s.remove("attachmentStatus"),s->s.put("status","unsupported"),s->s.put("unicode","emoji"),
            s->s.put("resourceId","missing"),s->s.put("durationRaw","2000000"),s->s.put("offsetRaw","-1"),
            s->s.put("commonKeyframes",Collections.singletonList(map("type","unknown"))),s->s.put("renderIndex",4),
            s->s.put("animationMaterialIds",Arrays.asList("a","a")));
        for(Consumer<Map<String,Object>> mutation:mutations){Map<String,Object> source=source();mutation.accept(source);assertThrows(RuntimeException.class,()->BrowserNativeStickerContract.validate(scene(source),descriptor()));}
    }
    @Test void animationNeedsDeliveredResourceAndCapturedParameters() {
        Map<String,Object> source=source(),animation=map("category","loop","resourceId","animation","fidelity","exact","preset","native_sticker_animation",
            "durationSeconds",.5,"rawAnimationTiming",map("startUs","0","durationUs","500000"),
            "nativeTextAnimationParameters",map("schemaVersion","native-text-animation-parameters-v1","status","captured","adjustmentDeclared",false,"direction","","animMode",""));
        source.put("animations",Collections.singletonList(animation));
        assertEquals(Collections.singleton("s"),BrowserNativeStickerContract.validate(scene(source),descriptor()));
        animation.put("resourceId","missing");assertThrows(RuntimeException.class,()->BrowserNativeStickerContract.validate(scene(source),descriptor()));
        animation.put("resourceId","animation");animation.remove("nativeTextAnimationParameters");
        assertThrows(RuntimeException.class,()->BrowserNativeStickerContract.validate(scene(source),descriptor()));
    }
    static Map<String,Object> group() {return map("id","g","property_type","KFTypePositionY","material_id","",
        "keyframe_list",Arrays.asList(map("curveType","Line","time_offset",0,"values",Collections.singletonList(.7)),
        map("curveType","Line","time_offset","1000000","values",Collections.singletonList(.66))));}
    @Test void linearStickerTransformRetainsSourceAndRejectsUnknownSemantics() {
        Map<String,Object> source=source(),group=group();source.put("commonKeyframes",Collections.singletonList(group));
        String before=source.toString();assertEquals(Collections.singleton("s"),BrowserNativeStickerContract.validate(scene(source),descriptor()));
        assertEquals(before,source.toString());
        for(String property:Arrays.asList("KFTypePositionX","KFTypePositionY","KFTypeScaleX","KFTypeScaleY","KFTypeRotation")) {
            group.put("property_type",property);assertEquals(Collections.singleton("s"),BrowserNativeStickerContract.validate(scene(source),descriptor()));
        }
        for(String property:Arrays.asList("KFTypeAlpha","KFTypeMaskSizeY","KFTypeScale")) {
            group.put("property_type",property);assertThrows(RuntimeException.class,()->BrowserNativeStickerContract.validate(scene(source),descriptor()));
        }
        group.put("property_type","KFTypePositionY");group.put("material_id","unexpected");
        assertThrows(RuntimeException.class,()->BrowserNativeStickerContract.validate(scene(source),descriptor()));
        group.put("material_id","");source.put("commonKeyframes",Arrays.asList(group,group));
        assertThrows(RuntimeException.class,()->BrowserNativeStickerContract.validate(scene(source),descriptor()));
        source.put("commonKeyframes",Collections.singletonList(group));
        Map<String,Object> point=(Map<String,Object>)((List<?>)group.get("keyframe_list")).get(1);
        point.put("curveType","Cubic");assertThrows(RuntimeException.class,()->BrowserNativeStickerContract.validate(scene(source),descriptor()));
        point.put("curveType","Line");point.put("time_offset",0);assertThrows(RuntimeException.class,()->BrowserNativeStickerContract.validate(scene(source),descriptor()));
        point.put("time_offset",1);point.put("values",Collections.singletonList(Double.NaN));
        assertThrows(RuntimeException.class,()->BrowserNativeStickerContract.validate(scene(source),descriptor()));
    }
}
