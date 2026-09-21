package com.example.cursorquitterweb.musicmv.service;

import java.util.*;

/** 正式任务沿用前端原生能力归属检查；实际执行仍由原生场景规划器逐项校验。 */
final class BrowserNativeSceneCapabilities {
    private BrowserNativeSceneCapabilities() {}

    static boolean ownsEffectBlockers(Map<String, Object> scene) {
        try {
            Map<?, ?> capability = map(scene.get("capability"));
            List<?> blockers = list(capability.get("blockingFeatures"));
            if (blockers.size() != 1 || !"post_effects".equals(blockers.get(0))) return false;
            Map<?, ?> delivery = map(scene.get("runtimeDelivery"));
            if (!"browser-runtime-delivery-v1".equals(delivery.get("schemaVersion"))) return false;
            Set<String> delivered = new HashSet<>();
            for (Object value : list(delivery.get("resources"))) {
                String id = string(map(value).get("resourceId"));
                if (!delivered.add(id)) return false;
            }
            Map<String, Object> descriptor = BrowserNativeRuntimeContract.validate(map(delivery.get("nativeEngine")), delivered, scene);
            Set<String> globalEffects=BrowserNativeGlobalEffectContract.validate(scene,descriptor);
            BrowserNativeStickerContract.validate(scene,descriptor);
            Set<String> bindings = new HashSet<>();
            for (Object value : list(descriptor.get("bindings"))) bindings.add(string(map(value).get("resourceId")));
            String schema = string(descriptor.get("schemaVersion"));
            boolean ownsVideo = "browser-native-scene-runtime-v4".equals(schema);
            boolean ownsFixed = ownsVideo || "browser-native-scene-runtime-v3".equals(schema);
            Map<String, Map<?, ?>> layers = new HashMap<>();
            for (Object value : list(scene.get("layers"))) {
                Map<?, ?> layer = map(value);
                if (layers.put(string(layer.get("layerId")), layer) != null) return false;
            }
            List<Map<?, ?>> segments = new ArrayList<>();
            for (Object value : list(scene.get("timelineSegments"))) {
                Map<?, ?> segment = map(value);
                Map<?, ?> layer = layers.get(string(segment.get("segmentId")));
                if (layer == null || !"photo".equals(layer.get("type"))) return false;
                segments.add(segment);
            }
            if (ownsFixed) for (Map<?, ?> layer : layers.values()) {
                if ("static_image".equals(layer.get("type")) || (ownsVideo && "video".equals(layer.get("type")))) segments.add(layer);
            }
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (Object value : list(capability.get("resourceDiagnostics"))) {
                Map<?, ?> diagnostic = map(value);
                if (!Boolean.TRUE.equals(diagnostic.get("blocksExport"))) continue;
                if (!"post_effects".equals(diagnostic.get("feature"))) return false;
                String effectId = string(diagnostic.get("segmentId")), resourceId = string(diagnostic.get("resourceId"));
                if (!bindings.contains(resourceId) || !seen.add(effectId + "\u0000" + resourceId)) return false;
                int matches = 0;
                for (Map<?, ?> segment : segments) for (Object raw : list(segment.get("effects"))) {
                    Map<?, ?> effect = map(raw);
                    if (effectId.equals(effect.get("effectId")) && resourceId.equals(effect.get("resourceId"))
                            && "unsupported".equals(effect.get("fidelity"))) matches++;
                }
                if(!globalEffects.isEmpty())for(Object raw:list(scene.get("postEffects"))) {
                    Map<?,?> effect=map(raw);
                    if(globalEffects.contains(effectId)&&effectId.equals(effect.get("effectId"))
                            &&resourceId.equals(effect.get("resourceId")))matches++;
                }
                if (matches != 1) return false;
                count++;
            }
            int summaries = 0;
            for (Object value : list(capability.get("executionCapabilities"))) {
                Map<?, ?> item = map(value);
                if (!"post_effects".equals(item.get("feature"))) continue;
                summaries++;
                if (!(item.get("declaredCount") instanceof Number) || !(item.get("executableCount") instanceof Number)) return false;
                double declared = ((Number)item.get("declaredCount")).doubleValue();
                double executable = ((Number)item.get("executableCount")).doubleValue();
                if (!Double.isFinite(declared) || !Double.isFinite(executable) || executable < 0 || declared < executable
                        || declared != Math.rint(declared) || executable != Math.rint(executable) || declared - executable != count) return false;
            }
            return count > 0 && summaries == 1;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static Map<?, ?> map(Object value) {
        if (!(value instanceof Map)) throw new IllegalArgumentException("Expected map");
        return (Map<?, ?>)value;
    }
    private static List<?> list(Object value) {
        if (!(value instanceof List)) throw new IllegalArgumentException("Expected list");
        return (List<?>)value;
    }
    private static String string(Object value) {
        if (!(value instanceof String) || ((String)value).isEmpty() || ((String)value).indexOf('\u0000') >= 0)
            throw new IllegalArgumentException("Expected identity");
        return (String)value;
    }
}
