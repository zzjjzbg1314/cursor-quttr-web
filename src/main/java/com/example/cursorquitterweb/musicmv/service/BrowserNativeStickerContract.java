package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.http.HttpStatus;

/** 贴纸源、附属动画与最小包须一致，策略声明本身不能证明可执行。 */
final class BrowserNativeStickerContract {
    static Set<String> validate(Map<?,?> scene,Map<?,?> descriptor) {
        Set<String> owned=new HashSet<>();
        if(!descriptor.containsKey("stickerPolicy"))return owned;
        if(!"original_resources_v1".equals(descriptor.get("stickerPolicy"))
                ||!"browser-native-scene-runtime-v4".equals(descriptor.get("schemaVersion")))throw invalid();
        Map<String,Map<?,?>> bindings=new HashMap<>();
        for(Object raw:list(descriptor.get("bindings"))) {
            Map<?,?> binding=map(raw);if(bindings.put(id(binding.get("resourceId")),binding)!=null)throw invalid();
        }
        for(Object raw:list(scene.get("layers"))) {
            Map<?,?> layer=map(raw);if(!"sticker".equals(layer.get("type")))continue;
            String segment=id(layer.get("layerId"));Map<?,?> source=map(layer.get("nativeStickerSource"));
            if(!owned.add(segment)||!segment.equals(layer.get("segmentId"))||!segment.equals(source.get("segmentId"))
                    ||!"native-sticker-source-v1".equals(source.get("schemaVersion"))||!"source_projected".equals(source.get("status"))
                    ||!"bound".equals(source.get("attachmentStatus"))||!"sticker".equals(source.get("materialType"))
                    ||!"".equals(source.get("unicode"))||!(source.get("visible") instanceof Boolean)
                    ||!list(layer.get("effects")).isEmpty()
                    ||layer.containsKey("mask")||layer.containsKey("transitionIn")
                    ||(layer.containsKey("blendMode")&&!"source-over".equals(layer.get("blendMode"))))throw invalid();
            id(source.get("materialId"));id(source.get("trackId"));bind(bindings,id(source.get("resourceId")),"sticker");
            long start=integer(source.get("startRaw")),duration=integer(source.get("durationRaw")),offset=integer(source.get("offsetRaw"));
            if(start<0||duration<=0||safe(start+offset)<0||!seconds(layer.get("targetStartSeconds"),start)
                    ||!seconds(layer.get("targetDurationSeconds"),duration))throw invalid();
            safe(start+offset+duration);
            for(String key:Arrays.asList("renderIndex","trackRenderIndex"))if(index(source.get(key))!=index(layer.get(key)))throw invalid();
            if(((Number)descriptor.get("layerMode")).intValue()==1)safe(index(source.get("trackRenderIndex"))*100);
            transform(map(source.get("clip")));
            keyframes(source.get("commonKeyframes"));
            Set<String> materialIds=new HashSet<>();
            for(Object value:list(source.get("animationMaterialIds")))if(!materialIds.add(id(value)))throw invalid();
            if(materialIds.size()>1)throw invalid();
            Set<String> categories=new HashSet<>();
            for(Object value:list(source.get("animations"))) {
                Map<?,?> animation=map(value),timing=map(animation.get("rawAnimationTiming")),params=map(animation.get("nativeTextAnimationParameters"));
                String category=id(animation.get("category"));
                if(materialIds.isEmpty()||!Arrays.asList("in","out","loop").contains(category)||!categories.add(category)
                        ||!"exact".equals(animation.get("fidelity"))||!"native_sticker_animation".equals(animation.get("preset"))
                        ||integer(timing.get("startUs"))!=0||integer(timing.get("durationUs"))<=0
                        ||!seconds(animation.get("durationSeconds"),integer(timing.get("durationUs")))
                        ||!"native-text-animation-parameters-v1".equals(params.get("schemaVersion"))||!"captured".equals(params.get("status"))
                        ||!(params.get("adjustmentDeclared") instanceof Boolean)||!(params.get("direction") instanceof String)
                        ||!(params.get("animMode") instanceof String))throw invalid();
                if(Boolean.FALSE.equals(params.get("adjustmentDeclared"))&&(!"".equals(params.get("direction"))||!"".equals(params.get("animMode"))))throw invalid();
                bind(bindings,id(animation.get("resourceId")),"animation");
            }
        }
        return owned;
    }
    // 发布契约与浏览器共同限定普通资源贴纸的线性、局部时钟变换。
    private static void keyframes(Object raw) {
        Set<String> properties=new HashSet<>(),ids=new HashSet<>();
        for(Object value:list(raw)) {
            Map<?,?> group=map(value);String property=id(group.get("property_type"));
            if(!Arrays.asList("KFTypePositionX","KFTypePositionY","KFTypeScaleX","KFTypeScaleY","KFTypeRotation").contains(property)
                    ||!properties.add(property)||!ids.add(id(group.get("id")))||!empty(group.get("material_id")))throw invalid();
            List<?> points=list(group.get("keyframe_list"));if(points.isEmpty())throw invalid();
            long previous=-1;
            for(Object item:points) {
                Map<?,?> point=map(item);List<?> values=list(point.get("values"));
                if(!"Line".equals(point.get("curveType"))||!empty(point.get("graphID"))||!empty(point.get("graph"))
                        ||!empty(point.get("string_value"))||values.size()!=1||!finite(values.get(0)))throw invalid();
                Object time=point.get("time_offset");
                long current=time instanceof String?integer(time):index(time);
                if(current<0||current<=previous)throw invalid();previous=current;
            }
        }
    }
    private static boolean empty(Object value){return value==null||"".equals(value);}
    private static void transform(Map<?,?> clip) {
        if(!Arrays.asList("alpha","rotation","scale","transform","flip").containsAll(clip.keySet())
                ||!finite(clip.get("alpha"))||((Number)clip.get("alpha")).doubleValue()!=1||!finite(clip.get("rotation")))throw invalid();
        for(String key:Arrays.asList("scale","transform")) {Map<?,?> value=map(clip.get(key));if(!finite(value.get("x"))||!finite(value.get("y")))throw invalid();}
        Map<?,?> flip=map(clip.get("flip"));if(!(flip.get("horizontal") instanceof Boolean)||!(flip.get("vertical") instanceof Boolean))throw invalid();
    }
    private static void bind(Map<String,Map<?,?>> bindings,String id,String kind) {Map<?,?> value=bindings.get(id);if(value==null||!kind.equals(value.get("kind"))||!(id+"/").equals(value.get("path")))throw invalid();}
    private static long index(Object value){if(!finite(value))throw invalid();double n=((Number)value).doubleValue();if(n<0||n!=Math.rint(n))throw invalid();return safe(((Number)value).longValue());}
    private static boolean seconds(Object value,long raw){return finite(value)&&((Number)value).doubleValue()==raw/1e6;}
    private static boolean finite(Object value){return value instanceof Number&&Double.isFinite(((Number)value).doubleValue());}
    private static long integer(Object value){if(!(value instanceof String)||!((String)value).matches("-?(0|[1-9][0-9]*)"))throw invalid();try{return safe(Long.parseLong((String)value));}catch(NumberFormatException error){throw invalid();}}
    private static long safe(long value){if(value < -9007199254740991L||value>9007199254740991L)throw invalid();return value;}
    private static String id(Object value){if(!(value instanceof String)||((String)value).trim().isEmpty()||((String)value).indexOf(0)>=0)throw invalid();return (String)value;}
    private static Map<?,?> map(Object value){if(!(value instanceof Map))throw invalid();return (Map<?,?>)value;}
    private static List<?> list(Object value){if(!(value instanceof List))throw invalid();return (List<?>)value;}
    private static ApiException invalid(){return new ApiException(HttpStatus.CONFLICT,"NATIVE_STICKER_INVALID","原生贴纸源、时钟或依赖绑定缺失或尚未支持");}
    private BrowserNativeStickerContract(){}
}
