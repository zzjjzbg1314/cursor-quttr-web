package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.http.HttpStatus;

/** 校验原生照片及场景执行契约；SDK 从客户网站同源加载，效果只引用已交付最小包。 */
final class BrowserNativeRuntimeContract {
    @SuppressWarnings("unchecked")
    static Map<String,Object> validate(Object raw, Set<String> deliveredIds) {
        if (!(raw instanceof Map)) throw invalid();
        Map<String,Object> source=(Map<String,Object>)raw;
        if (!Arrays.asList("browser-native-photo-runtime-v1", "browser-native-scene-runtime-v2", "browser-native-scene-runtime-v3", "browser-native-scene-runtime-v4").contains(source.get("schemaVersion"))
                || !(source.get("layerMode") instanceof Number)
                || (((Number)source.get("layerMode")).doubleValue()!=0 && ((Number)source.get("layerMode")).doubleValue()!=1)
                || !(source.get("assets") instanceof Map) || !(source.get("files") instanceof List)
                || !(source.get("bindings") instanceof List)) throw invalid();
        boolean ownsVideo="browser-native-scene-runtime-v4".equals(source.get("schemaVersion"));
        if(ownsVideo&&!"external_music_only".equals(source.get("videoAudioPolicy")))throw invalid();
        boolean ownsTemplates="original_attachments_v1".equals(source.get("textTemplatePolicy"));
        if(source.containsKey("textTemplatePolicy")&&(!ownsTemplates||!ownsVideo))throw invalid();
        boolean ownsScriptTemplates="dynamic_text_v1".equals(source.get("scriptTemplatePolicy"));
        if(source.containsKey("scriptTemplatePolicy")&&(!ownsScriptTemplates||!ownsVideo))throw invalid();
        boolean ownsStickers="original_resources_v1".equals(source.get("stickerPolicy"));
        if(source.containsKey("stickerPolicy")&&(!ownsStickers||!ownsVideo))throw invalid();
        boolean ownsGlobals="original_tracks_v1".equals(source.get("globalEffectPolicy"));
        if(source.containsKey("globalEffectPolicy")&&(!ownsGlobals||!ownsVideo))throw invalid();
        Map<String,Object> assets=(Map<String,Object>)source.get("assets");
        Map<String,Object> safeAssets=new LinkedHashMap<>(); String root=null;
        String[][] roles={{"loaderUrl","loader.js"},{"mainWasmUrl","main.wasm"},{"mediaWasmUrl","media.wasm"},
                {"workerUrl","worker.mjs"},{"workletUrl","worklet.js"}};
        for(String[] role:roles) {
            Object value=assets.get(role[0]); if(!(value instanceof String))throw invalid();
            String path=(String)value;
            if(!path.matches("/native-runtime/[a-f0-9]{64}/"+role[1].replace(".","\\.")))throw invalid();
            String prefix=path.substring(0,path.lastIndexOf('/')+1);
            if(root!=null&&!root.equals(prefix))throw invalid(); root=prefix;safeAssets.put(role[0],path);
        }
        List<String> files=new ArrayList<>(); Set<String> uniqueFiles=new HashSet<>();
        for(Object value:(List<?>)source.get("files")) {
            if(!(value instanceof String))throw invalid();String file=(String)value;
            if(!file.matches("[A-Za-z0-9_./ -]+") || file.startsWith("/") || file.endsWith("/"))throw invalid();
            String[] parts=file.split("/",-1);
            if(parts.length<2 || !deliveredIds.contains(parts[0]) || !uniqueFiles.add(file))throw invalid();
            for(String part:parts)if(part.isEmpty()||part.equals(".")||part.equals(".."))throw invalid();
            files.add(file);
        }
        List<Map<String,Object>> bindings=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(Object value:(List<?>)source.get("bindings")) {
            if(!(value instanceof Map))throw invalid();Map<String,Object> item=(Map<String,Object>)value;
            String id=String.valueOf(item.get("resourceId"));
            if(!deliveredIds.contains(id)||!ids.add(id)||!(id+"/").equals(item.get("path"))
                    || !(Arrays.asList("filter","video_effect","adjustment","animation","transition","blend").contains(item.get("kind"))
                        ||(ownsTemplates&&"text_template".equals(item.get("kind")))
                        ||(ownsScriptTemplates&&"script_template".equals(item.get("kind")))
                        ||(ownsStickers&&"sticker".equals(item.get("kind"))))
                    || files.stream().noneMatch(file->file.startsWith(id+"/")))throw invalid();
            if("sticker".equals(item.get("kind"))&&(!uniqueFiles.contains(id+"/config.json")||!uniqueFiles.contains(id+"/infoSticker.lua")))throw invalid();
            if("text_template".equals(item.get("kind"))&&(!uniqueFiles.contains(id+"/config.json")||!uniqueFiles.contains(id+"/content.json")))throw invalid();
            if("script_template".equals(item.get("kind"))&&(!uniqueFiles.contains(id+"/config.json")
                    ||!uniqueFiles.contains(id+"/js/main.js")||!uniqueFiles.contains(id+"/js/template/template.js")
                    ||files.stream().noneMatch(file->file.matches(java.util.regex.Pattern.quote(id)+"/templates/[A-Za-z0-9_-]{1,160}/content\\.json"))))throw invalid();
            Map<String,Object> binding=new LinkedHashMap<>();binding.put("resourceId",id);binding.put("path",id+"/");binding.put("kind",item.get("kind"));
            if(item.containsKey("models")) {
                if(!(item.get("models") instanceof Map))throw invalid();
                Map<String,String> models=new LinkedHashMap<>();
                for(Map.Entry<?,?> entry:((Map<?,?>)item.get("models")).entrySet()) {
                    if(!(entry.getKey() instanceof String)||!(entry.getValue() instanceof String)
                            || !((String)entry.getKey()).matches("[A-Za-z0-9_-]+")
                            || !uniqueFiles.contains(entry.getValue())||!((String)entry.getValue()).endsWith(".model"))throw invalid();
                    models.put((String)entry.getKey(),(String)entry.getValue());
                }
                binding.put("models",models);
            }
            if(item.containsKey("adjustmentParameters")) {
                if(!"adjustment".equals(item.get("kind"))||!(item.get("adjustmentParameters") instanceof Map))throw invalid();
                Map<String,String> parameters=new LinkedHashMap<>();
                for(Map.Entry<?,?> entry:((Map<?,?>)item.get("adjustmentParameters")).entrySet()) {
                    if(!(entry.getKey() instanceof String)||!(entry.getValue() instanceof String)
                            || !((String)entry.getKey()).matches("[A-Za-z][A-Za-z0-9_]{0,127}")
                            || !((String)entry.getValue()).matches("[A-Za-z][A-Za-z0-9_]{0,127}"))throw invalid();
                    parameters.put((String)entry.getKey(),(String)entry.getValue());
                }
                binding.put("adjustmentParameters",parameters);
            }
            bindings.add(binding);
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("schemaVersion",source.get("schemaVersion"));
        if(ownsVideo)result.put("videoAudioPolicy","external_music_only");
        if(ownsStickers)result.put("stickerPolicy","original_resources_v1");
        if(ownsGlobals)result.put("globalEffectPolicy","original_tracks_v1");
        if(ownsScriptTemplates)result.put("scriptTemplatePolicy","dynamic_text_v1");
        if(ownsTemplates)result.put("textTemplatePolicy","original_attachments_v1");
        result.put("layerMode",((Number)source.get("layerMode")).intValue());result.put("assets",safeAssets);
        result.put("files",files);result.put("bindings",bindings);return result;
    }
    private static ApiException invalid(){return new ApiException(HttpStatus.CONFLICT,"NATIVE_RUNTIME_CONTRACT_INVALID","原生运行契约、同源 SDK 或最小依赖绑定无效");}
    private BrowserNativeRuntimeContract(){}
}
