package com.example.cursorquitterweb.musicmv.service;

import java.util.*;

/** 文字模板内部的贴纸依赖不取得独立贴纸图层的执行权限。 */
final class BrowserNativeScriptDependencyContract {
    static Set<String> stickers(Map<String,Object> scene, Map<String,Object> descriptor) {
        Set<String> result=new HashSet<>();
        if(!"original_text_v2".equals(descriptor.get("scriptTemplatePolicy"))
                ||!(scene.get("textLayers") instanceof List))return result;
        Set<Object> parents=new HashSet<>();
        for(Object raw:(List<?>)descriptor.get("bindings"))if(raw instanceof Map) {
            Map<?,?> binding=(Map<?,?>)raw;
            if("script_template".equals(binding.get("kind")))parents.add(binding.get("resourceId"));
        }
        for(Object raw:(List<?>)scene.get("textLayers")) {
            if(!(raw instanceof Map))continue;Map<?,?> layer=(Map<?,?>)raw;
            if(!(layer.get("nativeScriptTemplateSource") instanceof Map))continue;
            Map<?,?> source=(Map<?,?>)layer.get("nativeScriptTemplateSource");
            if(!"native-script-template-source-v1".equals(source.get("schemaVersion"))
                    ||!"source_projected".equals(source.get("status"))
                    ||!"original_resource".equals(source.get("buildMode"))
                    ||!parents.contains(source.get("runtimeResourceId"))
                    ||!(source.get("segmentId") instanceof String)
                    ||!source.get("segmentId").equals(layer.get("sourceSegmentId"))
                    ||!(source.get("resourceId") instanceof String)
                    ||!source.get("resourceId").equals(layer.get("templateResourceId"))
                    ||!(layer.get("templateAttachmentId") instanceof String)
                    ||!(source.get("segmentId")+"__"+layer.get("templateAttachmentId")).equals(layer.get("segmentId"))
                    ||!(source.get("attachments") instanceof List)
                    ||!(source.get("resourceIds") instanceof List)
                    ||!(source.get("dependencies") instanceof List))continue;
            boolean attachment=false;
            for(Object item:(List<?>)source.get("attachments"))if(item instanceof Map
                    &&layer.get("templateAttachmentId").equals(((Map<?,?>)item).get("id")))attachment=true;
            if(!attachment)continue;
            for(Object item:(List<?>)source.get("dependencies"))if(item instanceof Map) {
                Map<?,?> dependency=(Map<?,?>)item;Object id=dependency.get("resourceId");
                // default 是贴纸本体；sticker 面板是动画，不能作为贴纸本体放行。
                if("default".equals(dependency.get("type"))&&id instanceof String
                        &&((List<?>)source.get("resourceIds")).contains(id))result.add((String)id);
            }
        }
        return result;
    }
    private BrowserNativeScriptDependencyContract(){}
}
