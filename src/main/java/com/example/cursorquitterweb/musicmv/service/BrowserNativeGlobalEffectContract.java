package com.example.cursorquitterweb.musicmv.service;
import java.util.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.http.HttpStatus;

/** 原生全局接管必须逐项对应原始源和已交付依赖，不能仅凭策略字段放行。 */
final class BrowserNativeGlobalEffectContract {
    static Set<String> validate(Map<?,?> scene,Map<?,?> descriptor) {
        Set<String> owned=new HashSet<>();
        if(!descriptor.containsKey("globalEffectPolicy"))return owned;
        if(!"original_tracks_v1".equals(descriptor.get("globalEffectPolicy"))
                ||!"browser-native-scene-runtime-v4".equals(descriptor.get("schemaVersion")))throw invalid();
        Map<String,Map<?,?>> bindings=new HashMap<>();
        for(Object raw:list(descriptor.get("bindings"))) {
            Map<?,?> binding=map(raw);if(bindings.put(id(binding.get("resourceId")),binding)!=null)throw invalid();
        }
        Object effects=scene.get("postEffects");
        if(effects==null)return owned;
        for(Object raw:list(effects)) {
            Map<?,?> effect=map(raw),source=map(effect.get("nativeGlobalSource"));
            String effectId=id(effect.get("effectId"));Map<?,?> binding=bindings.get(id(effect.get("resourceId")));
            if(!owned.add(effectId)||!effectId.equals(source.get("segmentId"))
                    ||!"native-global-effect-source-v1".equals(source.get("schemaVersion"))
                    ||!Arrays.asList("filter","video_effect").contains(source.get("type"))
                    ||binding==null||!source.get("type").equals(binding.get("kind")))throw invalid();
            id(source.get("materialId"));id(binding.get("path"));
            long start=integer(source.get("targetStartRaw")),duration=integer(source.get("targetDurationRaw"));
            long offset=integer(source.get("offsetRaw"));
            if(start<0||duration<=0||!seconds(effect.get("targetStartSeconds"),start)
                    ||!seconds(effect.get("targetDurationSeconds"),duration))throw invalid();
            long sourceStart=start,sourceDuration=duration;
            if(source.containsKey("sourceStartRaw")!=source.containsKey("sourceDurationRaw"))throw invalid();
            if(source.containsKey("sourceStartRaw")) {
                long a=integer(source.get("sourceStartRaw")),b=integer(source.get("sourceDurationRaw"));
                if(b>0){sourceStart=a;sourceDuration=b;}
            }
            safe(start+duration);safe(offset+sourceStart);safe(offset+sourceStart+sourceDuration);
            long index=index(source.get("renderIndex")),track=index(source.get("trackRenderIndex"));
            if(((Number)descriptor.get("layerMode")).intValue()==1)safe(track*100);
            if(!(source.get("visible") instanceof Boolean)||!finite(source.get("value")))throw invalid();
            Set<String> params=new HashSet<>();
            for(Object value:list(source.get("adjustParams"))) {
                Map<?,?> param=map(value);String name=id(param.get("name"));
                if(!params.add(name)||Arrays.asList("__proto__","constructor","prototype").contains(name)
                        ||!finite(param.get("value")))throw invalid();
            }
            try { BrowserNativeEffectKeyframes.validate(source.get("commonKeyframes"),params); }
            catch(java.io.IOException error) { throw invalid(); }
        }
        return owned;
    }
    private static long index(Object value){if(!finite(value))throw invalid();double n=((Number)value).doubleValue();if(n<0||n!=Math.rint(n))throw invalid();return safe(((Number)value).longValue());}
    private static boolean seconds(Object value,long raw){return finite(value)&&((Number)value).doubleValue()==raw/1e6;}
    private static boolean finite(Object value){return value instanceof Number&&Double.isFinite(((Number)value).doubleValue());}
    private static long integer(Object value){if(!(value instanceof String)||!((String)value).matches("-?(0|[1-9][0-9]*)"))throw invalid();try{return safe(Long.parseLong((String)value));}catch(NumberFormatException error){throw invalid();}}
    private static long safe(long value){if(value < -9007199254740991L||value>9007199254740991L)throw invalid();return value;}
    private static String id(Object value){if(!(value instanceof String)||((String)value).trim().isEmpty()||((String)value).indexOf(0)>=0)throw invalid();return (String)value;}
    private static Map<?,?> map(Object value){if(!(value instanceof Map))throw invalid();return (Map<?,?>)value;}
    private static List<?> list(Object value){if(!(value instanceof List))throw invalid();return (List<?>)value;}
    private static ApiException invalid(){return new ApiException(HttpStatus.CONFLICT,"NATIVE_GLOBAL_EFFECT_INVALID","原生全局效果的原始时钟、参数或依赖绑定缺失或尚未支持");}
    private BrowserNativeGlobalEffectContract(){}
}
