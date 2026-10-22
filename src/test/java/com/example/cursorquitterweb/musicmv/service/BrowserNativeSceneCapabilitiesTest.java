package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrowserNativeSceneCapabilitiesTest {
    static Map<String, Object> fixture() {
        Map<String, Object> descriptor = BrowserNativeRuntimeContractTest.descriptor();
        descriptor.put("schemaVersion", "browser-native-scene-runtime-v2");
        Map<String, Object> diagnostic = row("feature", "post_effects", "segmentId", "fx", "resourceId", "effect", "blocksExport", true);
        Map<String, Object> summary = row("feature", "post_effects", "declaredCount", 2, "executableCount", 1);
        return row("runtimeDelivery", row("schemaVersion", "browser-runtime-delivery-v1", "nativeEngine", descriptor,
                "resources", Collections.singletonList(row("resourceId", "effect"))),
                "layers", Collections.singletonList(row("layerId", "photo", "type", "photo")),
                "timelineSegments", Collections.singletonList(row("segmentId", "photo", "effects", Collections.singletonList(
                        row("effectId", "fx", "resourceId", "effect", "fidelity", "unsupported")))),
                "capability", row("browserExportReady", false, "blockingFeatures", Collections.singletonList("post_effects"),
                        "resourceDiagnostics", Collections.singletonList(diagnostic), "executionCapabilities", Collections.singletonList(summary)));
    }
    @Test void acceptsOnlyPreciselyOwnedEffectDeficits() {
        assertTrue(BrowserNativeSceneCapabilities.ownsEffectBlockers(fixture()));
        for (String variant : Arrays.asList("unknownFeature", "noDiagnostics", "duplicate", "mismatch", "unbound", "wrongLayer", "unknownRuntime")) {
            Map<String, Object> scene = fixture();
            Map<String, Object> capability = (Map<String, Object>) scene.get("capability");
            Map<String, Object> diagnostic = (Map<String, Object>) ((List<?>)capability.get("resourceDiagnostics")).get(0);
            if (variant.equals("unknownFeature")) capability.put("blockingFeatures", Arrays.asList("post_effects", "future"));
            if (variant.equals("noDiagnostics")) capability.remove("resourceDiagnostics");
            if (variant.equals("duplicate")) capability.put("resourceDiagnostics", Arrays.asList(diagnostic, diagnostic));
            if (variant.equals("mismatch")) diagnostic.put("segmentId", "photo");
            if (variant.equals("unbound")) ((Map<String, Object>)scene.get("runtimeDelivery")).put("resources", Collections.emptyList());
            if (variant.equals("wrongLayer")) ((Map<String, Object>)((List<?>)scene.get("layers")).get(0)).put("type", "video");
            if (variant.equals("unknownRuntime")) ((Map<String, Object>)((Map<?, ?>)scene.get("runtimeDelivery")).get("nativeEngine")).put("schemaVersion", "future");
            assertFalse(BrowserNativeSceneCapabilities.ownsEffectBlockers(scene), variant);
        }
    }
    @Test void rejectsUnaccountedOrInvalidCounts() {
        for (double declared : new double[]{1, 3, Double.NaN, 2.5}) {
            Map<String, Object> scene = fixture();
            Map<String, Object> summary = (Map<String, Object>) ((List<?>)((Map<?, ?>)scene.get("capability")).get("executionCapabilities")).get(0);
            summary.put("declaredCount", declared);
            assertFalse(BrowserNativeSceneCapabilities.ownsEffectBlockers(scene));
        }
    }
    @Test @SuppressWarnings("unchecked") void acceptsVerifiedBlendAndEffectCombinationAndRejectsIncompleteBindings() {
        for (String variant : Arrays.asList("valid", "unbound", "wrongKind", "count", "duplicateSummary", "unknown")) {
            Map<String,Object> scene=fixture();
            Map<String,Object> capability=(Map<String,Object>)scene.get("capability");
            capability.put("blockingFeatures",Arrays.asList("post_effects","blend_modes"));
            capability.put("unsupportedBrowserBlendModeCount",1);
            Map<String,Object> delivery=(Map<String,Object>)scene.get("runtimeDelivery");
            Map<String,Object> descriptor=(Map<String,Object>)delivery.get("nativeEngine");
            descriptor.put("schemaVersion","browser-native-scene-runtime-v3");
            descriptor.put("files",Arrays.asList("effect/config.json","blend/config.json"));
            descriptor.put("bindings",Arrays.asList(((List<?>)descriptor.get("bindings")).get(0),
                    row("resourceId","blend","kind",variant.equals("wrongKind")?"filter":"blend","path","blend/")));
            delivery.put("resources",Arrays.asList(row("resourceId","effect"),row("resourceId","blend")));
            Map<String,Object> segment=(Map<String,Object>)((List<?>)scene.get("timelineSegments")).get(0);
            segment.put("nativeBlendResourceId",variant.equals("unbound")?"missing":"blend");
            Object effects=((List<?>)capability.get("executionCapabilities")).get(0);
            Map<String,Object> blend=row("feature","blend_modes","declaredCount",variant.equals("count")?2:1,"executableCount",0);
            capability.put("executionCapabilities",variant.equals("duplicateSummary")?Arrays.asList(effects,blend,blend):Arrays.asList(effects,blend));
            if(variant.equals("unknown"))capability.put("blockingFeatures",Arrays.asList("post_effects","blend_modes","future"));
            assertEquals(variant.equals("valid"),BrowserNativeSceneCapabilities.ownsEffectBlockers(scene),variant);
        }
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> textAnimationScene() {
        Map<String,Object> scene=fixture(),delivery=(Map<String,Object>)scene.get("runtimeDelivery");
        Map<String,Object> descriptor=(Map<String,Object>)delivery.get("nativeEngine");
        descriptor.put("files",Arrays.asList("effect/config.json","animation/config.json"));
        descriptor.put("bindings",Arrays.asList(((List<?>)descriptor.get("bindings")).get(0),row("resourceId","animation","kind","animation","path","animation/")));
        delivery.put("resources",Arrays.asList(row("resourceId","effect"),row("resourceId","animation")));
        Map<String,Object> animation=row("animationId","anim-1","resourceId","animation","category","out","preset","unsupported","fidelity","unsupported",
                "rawAnimationTiming",row("startUs","0","durationUs","1000000"),
                "nativeTextAnimationParameters",row("schemaVersion","native-text-animation-parameters-v1","status","captured","adjustmentDeclared",false,"direction","","animMode",""));
        Map<String,Object> text=row("segmentId","text","fidelity","exact","targetStartSeconds",0d,"targetDurationSeconds",4d,"animations",Collections.singletonList(animation),
                "nativeTextSource",row("schemaVersion","native-text-source-v1","status","material_projected","visible",true,"common_keyframes",Collections.emptyList(),
                        "originalTimeline",row("trackId","track","speed",1,"targetStartRaw","0","targetDurationRaw","4000000")));
        scene.put("textLayers",Collections.singletonList(text));
        scene.put("layers",Arrays.asList(((List<?>)scene.get("layers")).get(0),row("layerId","text","type","text","targetStartSeconds",0d,"targetDurationSeconds",4d)));
        Map<String,Object> capability=(Map<String,Object>)scene.get("capability");
        capability.put("blockingFeatures",Arrays.asList("post_effects","animations"));capability.put("unsupportedBrowserAnimationCount",1);
        capability.put("executionCapabilities",Arrays.asList(((List<?>)capability.get("executionCapabilities")).get(0),row("feature","animations","declaredCount",1,"executableCount",0)));
        return scene;
    }
    @Test @SuppressWarnings("unchecked") void validatesNativeTextAnimationOwnershipWithoutOpeningUnknownCapabilities() {
        for(String variant:Arrays.asList("valid","resource","parameters","duration","clock","duplicate","count","unknown","owner","script","adjustment","category")) {
            Map<String,Object> scene=textAnimationScene();
            Map<String,Object> text=(Map<String,Object>)((List<?>)scene.get("textLayers")).get(0);
            Map<String,Object> animation=(Map<String,Object>)((List<?>)text.get("animations")).get(0);
            if(variant.equals("resource"))animation.put("resourceId","missing");
            if(variant.equals("parameters"))animation.remove("nativeTextAnimationParameters");
            if(variant.equals("duration"))((Map<String,Object>)animation.get("rawAnimationTiming")).put("durationUs","0");
            if(variant.equals("clock"))text.put("targetDurationSeconds",5d);
            if(variant.equals("duplicate"))text.put("animations",Arrays.asList(animation,animation));
            if(variant.equals("count"))((Map<String,Object>)scene.get("capability")).put("unsupportedBrowserAnimationCount",2);
            if(variant.equals("unknown"))((Map<String,Object>)scene.get("capability")).put("blockingFeatures",Arrays.asList("animations","future"));
            if(variant.equals("owner"))text.put("segmentId","missing");
            if(variant.equals("script"))text.put("nativeScriptTemplateSource",Collections.emptyMap());
            if(variant.equals("adjustment"))((Map<String,Object>)animation.get("nativeTextAnimationParameters")).put("direction","left");
            if(variant.equals("category"))animation.put("category","caption");
            assertEquals(variant.equals("valid"),BrowserNativeSceneCapabilities.ownsEffectBlockers(scene),variant);
        }
    }
    @Test @SuppressWarnings("unchecked") void acceptsBoundStickerDeficitsButRejectsUnexplainedDiagnostics() {
        for (String variant : Arrays.asList("valid", "count", "resource", "duplicate", "policy", "source")) {
            Map<String,Object> scene = fixture(), delivery = (Map<String,Object>) scene.get("runtimeDelivery");
            Map<String,Object> descriptor = (Map<String,Object>) delivery.get("nativeEngine");
            descriptor.put("schemaVersion", "browser-native-scene-runtime-v4");
            descriptor.put("stickerPolicy", "original_resources_v1");
            descriptor.put("videoAudioPolicy", "external_music_only");
            descriptor.put("files", Arrays.asList("effect/config.json", "sticker/config.json", "sticker/infoSticker.lua"));
            descriptor.put("bindings", Arrays.asList(((List<?>)descriptor.get("bindings")).get(0),
                    row("resourceId", "sticker", "kind", "sticker", "path", "sticker/")));
            delivery.put("resources", Arrays.asList(row("resourceId", "effect"), row("resourceId", "sticker")));
            Map<String,Object> source = BrowserNativeStickerContractTest.source();
            Object layer = ((List<?>) BrowserNativeStickerContractTest.scene(source).get("layers")).get(0);
            scene.put("layers", Arrays.asList(((List<?>)scene.get("layers")).get(0), layer));
            Map<String,Object> capability = (Map<String,Object>) scene.get("capability");
            capability.put("blockingFeatures", Arrays.asList("post_effects", "sticker_layers"));
            capability.put("draftStickerLayerCount", 1); capability.put("browserStickerLayerCount", 0);
            capability.put("executionCapabilities", Arrays.asList(((List<?>)capability.get("executionCapabilities")).get(0),
                    row("feature", "sticker_layers", "declaredCount", variant.equals("count") ? 2 : 1, "executableCount", 0)));
            Object effect = ((List<?>)capability.get("resourceDiagnostics")).get(0);
            Object sticker = row("feature", "sticker_layers", "segmentId", "s", "resourceId", variant.equals("resource") ? "other" : "sticker", "blocksExport", true);
            capability.put("resourceDiagnostics", variant.equals("duplicate") ? Arrays.asList(effect, sticker, sticker) : Arrays.asList(effect, sticker));
            if (variant.equals("policy")) descriptor.remove("stickerPolicy");
            if (variant.equals("source")) source.remove("attachmentStatus");
            assertEquals(variant.equals("valid"), BrowserNativeSceneCapabilities.ownsEffectBlockers(scene), variant);
        }
    }

    @Test @SuppressWarnings("unchecked") void imageAnimationDeficitsRequireUniqueBoundResourcesAndCapturedTiming() {
        for (String variant : Arrays.asList("valid", "resource", "duration", "duplicate", "count", "parameters")) {
            Map<String,Object> scene = textAnimationScene();
            Map<String,Object> text = (Map<String,Object>) ((List<?>)scene.get("textLayers")).get(0);
            Map<String,Object> animation = (Map<String,Object>) ((List<?>)text.get("animations")).get(0);
            animation.put("durationSeconds", 1d);
            scene.put("textLayers", Collections.emptyList());
            Map<String,Object> segment = (Map<String,Object>) ((List<?>)scene.get("timelineSegments")).get(0);
            segment.put("animations", variant.equals("duplicate") ? Arrays.asList(animation, animation) : Collections.singletonList(animation));
            if (variant.equals("resource")) animation.put("resourceId", "missing");
            if (variant.equals("duration")) animation.put("durationSeconds", 2d);
            if (variant.equals("parameters")) animation.remove("nativeTextAnimationParameters");
            if (variant.equals("count")) ((Map<String,Object>)scene.get("capability")).put("unsupportedBrowserAnimationCount", 2);
            assertEquals(variant.equals("valid"), BrowserNativeSceneCapabilities.ownsEffectBlockers(scene), variant);
        }
    }

    @Test @SuppressWarnings("unchecked") void checksCapturedProductionSceneWhenProvided() throws Exception {
        String path=System.getProperty("native.animation.scene");
        org.junit.jupiter.api.Assumptions.assumeTrue(path!=null);
        Map<String,Object> scene=new com.fasterxml.jackson.databind.ObjectMapper().readValue(new java.io.File(path),Map.class);
        assertTrue(BrowserNativeSceneCapabilities.ownsEffectBlockers(scene));
    }
    static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i=0;i<values.length;i+=2) result.put((String)values[i],values[i+1]);
        return result;
    }
}
