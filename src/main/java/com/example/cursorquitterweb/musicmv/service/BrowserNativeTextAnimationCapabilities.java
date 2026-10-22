package com.example.cursorquitterweb.musicmv.service;

import java.util.*;

/** 与浏览器普通文字动画绑定契约对应；只解释已交付的原生文字动画缺口。 */
final class BrowserNativeTextAnimationCapabilities {
    static boolean owns(Map<String,Object> scene, Map<String,Object> descriptor,
                        Map<String,Map<?,?>> layers, Map<String,String> bindings) {
        if (!Arrays.asList("browser-native-scene-runtime-v2", "browser-native-scene-runtime-v3",
                "browser-native-scene-runtime-v4").contains(descriptor.get("schemaVersion"))) return false;
        Set<String> ids=new HashSet<>(); int missing=0;
        for(Object raw:list(scene.get("textLayers"))) {
            Map<?,?> text=map(raw); String id=identity(text.get("segmentId"));
            if(!ids.add(id)) return false;
            List<?> animations=list(text.get("animations"));
            boolean blocked=animations.stream().anyMatch(a->"unsupported".equals(map(a).get("fidelity")));
            if(!blocked) continue;
            Map<?,?> layer=layers.get(id),source=map(text.get("nativeTextSource"));
            if(layer==null||!"text".equals(layer.get("type"))||!"exact".equals(text.get("fidelity"))
                    ||text.get("templateResourceId")!=null||text.get("nativeScriptTemplateSource")!=null
                    ||!"native-text-source-v1".equals(source.get("schemaVersion"))
                    ||!"material_projected".equals(source.get("status"))) return false;
            Map<?,?> timing=map(source.get("originalTimeline")); identity(timing.get("trackId"));
            if(!(timing.get("speed") instanceof Number)||((Number)timing.get("speed")).doubleValue()!=1
                    ||!(source.get("visible") instanceof Boolean)||!(source.get("common_keyframes") instanceof List)) return false;
            long start=micros(timing.get("targetStartRaw")),duration=micros(timing.get("targetDurationRaw"));
            if(duration<=0||!clock(text.get("targetStartSeconds"),start)||!clock(text.get("targetDurationSeconds"),duration)
                    ||!clock(layer.get("targetStartSeconds"),start)||!clock(layer.get("targetDurationSeconds"),duration)) return false;
            Set<String> animationIds=new HashSet<>();
            for(Object value:animations) {
                Map<?,?> animation=map(value),parameters=map(animation.get("nativeTextAnimationParameters"));
                Map<?,?> time=map(animation.get("rawAnimationTiming"));
                long animationStart=micros(time.get("startUs")),animationDuration=micros(time.get("durationUs"));
                if(!animationIds.add(identity(animation.get("animationId")))
                        ||!"native-text-animation-parameters-v1".equals(parameters.get("schemaVersion"))
                        ||!"captured".equals(parameters.get("status"))
                        ||!(parameters.get("adjustmentDeclared") instanceof Boolean)
                        ||!(parameters.get("direction") instanceof String)||!(parameters.get("animMode") instanceof String)) return false;
                boolean adjusted=!"".equals(parameters.get("direction"))||!"".equals(parameters.get("animMode"));
                if(Boolean.FALSE.equals(parameters.get("adjustmentDeclared"))&&adjusted)return false;
                if(!Arrays.asList("in","out","loop","caption").contains(animation.get("category")))return false;
                if("noop".equals(animation.get("preset"))) {
                    if(!"exact".equals(animation.get("fidelity"))||animationStart!=0||animationDuration!=0
                            ||!clock(animation.get("durationSeconds"),0)||animation.get("resourceId")!=null||adjusted)return false;
                } else if(!Arrays.asList("in","out","loop").contains(animation.get("category"))
                        ||animationDuration<=0||!"animation".equals(bindings.get(identity(animation.get("resourceId")))))return false;
                if("unsupported".equals(animation.get("fidelity")))missing++;
            }
        }
        Map<?,?> capability=map(scene.get("capability"));int summaries=0;
        if(missing==0||!count(capability.get("unsupportedBrowserAnimationCount"),missing))return false;
        for(Object value:list(capability.get("executionCapabilities"))) {
            Map<?,?> summary=map(value);if(!"animations".equals(summary.get("feature")))continue;
            summaries++;
            Object declared=summary.get("declaredCount"),executable=summary.get("executableCount");
            if(!integer(declared)||!integer(executable)
                    ||((Number)declared).doubleValue()-((Number)executable).doubleValue()!=missing)return false;
        }
        return summaries==1;
    }
    private static boolean integer(Object value){return value instanceof Number&&Double.isFinite(((Number)value).doubleValue())
            &&((Number)value).doubleValue()>=0&&((Number)value).doubleValue()==Math.rint(((Number)value).doubleValue());}
    private static boolean count(Object value,int expected){return integer(value)&&((Number)value).doubleValue()==expected;}
    private static boolean clock(Object value,long micros){return value instanceof Number&&Double.isFinite(((Number)value).doubleValue())
            &&((Number)value).doubleValue()>=0&&Math.round(((Number)value).doubleValue()*1e6)==micros;}
    private static long micros(Object value){if(!(value instanceof String)||!((String)value).matches("0|[1-9][0-9]*"))throw new IllegalArgumentException();
        long n=Long.parseLong((String)value);if(n>9007199254740991L)throw new IllegalArgumentException();return n;}
    private static String identity(Object value){if(!(value instanceof String)||((String)value).isEmpty()||((String)value).indexOf('\u0000')>=0)throw new IllegalArgumentException();return (String)value;}
    private static Map<?,?> map(Object value){if(!(value instanceof Map))throw new IllegalArgumentException();return (Map<?,?>)value;}
    private static List<?> list(Object value){if(!(value instanceof List))throw new IllegalArgumentException();return (List<?>)value;}
    private BrowserNativeTextAnimationCapabilities(){}
}
