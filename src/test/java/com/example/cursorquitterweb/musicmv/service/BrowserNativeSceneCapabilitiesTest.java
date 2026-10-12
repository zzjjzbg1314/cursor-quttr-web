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
    static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i=0;i<values.length;i+=2) result.put((String)values[i],values[i+1]);
        return result;
    }
}
