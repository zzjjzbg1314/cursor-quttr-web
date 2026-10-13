package com.example.cursorquitterweb.musicmv.service;

import java.util.*;

/** 仅由已绑定的原生脚本文字父模板接管其装饰节点缺口。 */
final class BrowserNativeDecorationCapabilities {
    static boolean owns(Map<String,Object> scene,Map<String,Object> descriptor,Map<String,Map<?,?>> layers,Map<String,String> bindings) {
        if(!"original_text_v2".equals(descriptor.get("scriptTemplatePolicy")))return false;
        Map<String,Map<?,?>> parents=new HashMap<>();
        for(Object raw:list(scene.get("textLayers"))) {
            Map<?,?> text=map(raw);Object value=text.get("nativeScriptTemplateSource");
            if(!(value instanceof Map))continue;
            Map<?,?> source=map(value);List<?> decorations=list(source.getOrDefault("decorations",null));
            if(decorations.isEmpty())continue;
            String parent=id(source.get("segmentId")),child=id(text.get("templateAttachmentId"));
            Map<?,?> layer=layers.get(id(text.get("segmentId")));
            if(layer==null||!"text".equals(layer.get("type"))||!source.equals(layer.get("nativeScriptTemplateSource"))
                    ||!"native-script-template-source-v1".equals(source.get("schemaVersion"))||!"source_projected".equals(source.get("status"))
                    ||!"original_resource".equals(source.get("buildMode"))||!"script_template".equals(bindings.get(id(source.get("runtimeResourceId"))))
                    ||!parent.equals(text.get("sourceSegmentId"))||!(parent+"__"+child).equals(text.get("segmentId"))
                    ||!id(source.get("resourceId")).equals(text.get("templateResourceId")))return false;
            micros(source.get("start"));if(micros(source.get("duration"))<=0)return false;
            if(list(source.get("attachments")).stream().noneMatch(a->child.equals(map(a).get("id"))))return false;
            Map<?,?> prior=parents.putIfAbsent(parent,source);if(prior!=null&&!prior.equals(source))return false;
            Set<String> names=new HashSet<>(),animations=new HashSet<>();
            for(Object dep:list(source.get("dependencies"))) {
                Map<?,?> d=map(dep);if("sticker".equals(d.get("type"))&&list(source.get("resourceIds")).contains(d.get("resourceId")))animations.add(id(d.get("resourceId")));
            }
            for(Object item:decorations) {
                Map<?,?> d=map(item);if(!names.add(id(d.get("name")))||!"sticker".equals(d.get("type")))return false;
                micros(d.get("start"));if(micros(d.get("duration"))<=0||!transform(map(d.get("clip"))))return false;
                List<?> size=list(d.get("originalSize"));if(size.size()!=2||!positive(size.get(0))||!positive(size.get(1)))return false;
                Set<String> types=new HashSet<>();
                for(Object a:list(d.get("animations"))) {
                    Map<?,?> animation=map(a);String type=id(animation.get("type")),resource=id(animation.get("resourceId"));
                    if(!Arrays.asList("in","out","loop").contains(type)||!types.add(type)||micros(animation.get("duration"))<=0
                            ||!animations.contains(resource)||!"animation".equals(bindings.get(resource)))return false;
                }
            }
        }
        int count=parents.values().stream().mapToInt(p->list(p.get("decorations")).size()).sum(),summaries=0;
        for(Object raw:list(map(scene.get("capability")).get("executionCapabilities"))) {
            Map<?,?> c=map(raw);if(!"text_template_decorations".equals(c.get("feature")))continue;summaries++;
            if(!integer(c.get("declaredCount"))||!integer(c.get("executableCount"))
                    ||((Number)c.get("declaredCount")).doubleValue()-((Number)c.get("executableCount")).doubleValue()!=count)return false;
        }
        return count>0&&summaries==1;
    }
    private static boolean transform(Map<?,?> clip) {
        if(!finite(clip.get("alpha"))||!finite(clip.get("rotation")))return false;
        for(String field:Arrays.asList("scale","transform")) {Map<?,?> p=map(clip.get(field));if(!finite(p.get("x"))||!finite(p.get("y")))return false;}
        Map<?,?> flip=map(clip.get("flip"));return flip.get("horizontal") instanceof Boolean&&flip.get("vertical") instanceof Boolean;
    }
    private static boolean finite(Object n){return n instanceof Number&&Double.isFinite(((Number)n).doubleValue());}
    private static boolean positive(Object n){return finite(n)&&((Number)n).doubleValue()>0;}
    private static boolean integer(Object n){return finite(n)&&((Number)n).doubleValue()>=0&&((Number)n).doubleValue()==Math.rint(((Number)n).doubleValue());}
    private static long micros(Object n){if(!(n instanceof String)||!((String)n).matches("0|[1-9][0-9]*"))throw new IllegalArgumentException();long v=Long.parseLong((String)n);if(v>9007199254740991L)throw new IllegalArgumentException();return v;}
    private static String id(Object n){if(!(n instanceof String)||((String)n).isEmpty())throw new IllegalArgumentException();return (String)n;}
    private static Map<?,?> map(Object n){if(!(n instanceof Map))throw new IllegalArgumentException();return (Map<?,?>)n;}
    private static List<?> list(Object n){if(n==null)return Collections.emptyList();if(!(n instanceof List))throw new IllegalArgumentException();return (List<?>)n;}
    private BrowserNativeDecorationCapabilities(){}
}
