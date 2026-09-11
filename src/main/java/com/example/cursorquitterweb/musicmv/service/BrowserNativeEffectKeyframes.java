package com.example.cursorquitterweb.musicmv.service;
import java.io.IOException;
import java.util.*;

/** 独立滤镜的标量关键帧按资源声明绑定，时间不套用视频源裁剪和变速。 */
final class BrowserNativeEffectKeyframes {
    static void validate(Object raw, Set<String> parameters) throws IOException {
        Set<String> properties = new HashSet<>(parameters); properties.add("KFTypeFilter");
        Set<String> seen = new HashSet<>(), ids = new HashSet<>();
        for (Object item : list(raw)) {
            Map<?,?> group = map(item); String property = text(group.get("property_type"));
            if (!properties.contains(property) || !seen.add("KFTypeFilter".equals(property)?"intensity":property)
                    || !ids.add(text(group.get("id"))) || !empty(group.get("material_id"))) throw invalid();
            List<?> frames = list(group.get("keyframe_list")); if (frames.isEmpty()) throw invalid();
            long previous = -1;
            for (Object value : frames) {
                Map<?,?> frame = map(value); long time = micros(frame.get("time_offset"));
                if (time <= previous || !empty(frame.get("string_value"))) throw invalid(); previous = time;
                String curve = text(frame.get("curveType"));
                if (!Arrays.asList("Line","CurveIn","CurveOut","CurveInOut","FreeCurveIn","FreeCurveOut","FreeCurveInOut").contains(curve)) throw invalid();
                List<?> values = list(frame.get("values")); if (values.size()!=1 || !finite(values.get(0))) throw invalid();
                if (!"Line".equals(curve)) { control(frame.get("left_control")); control(frame.get("right_control")); }
                if (!empty(frame.get("graphID")) || !empty(frame.get("graph"))) {
                    Map<?,?> graph=map(frame.get("graph"));
                    if (!empty(frame.get("graphID")) && !frame.get("graphID").equals(graph.get("id"))) throw invalid();
                    List<?> points=list(graph.get("graph_points")); if(points.size()<4)throw invalid();
                    for(Object point:points) { Map<?,?> p=map(point); Object type=p.get("type");
                        if (!(type instanceof Number) || (((Number)type).doubleValue()!=0 && ((Number)type).doubleValue()!=1)) throw invalid();
                        control(p.get("point")); }
                }
            }
        }
    }
    private static void control(Object raw)throws IOException { Map<?,?> p=map(raw);
        if(!finite(p.get("x")) || Math.abs(((Number)p.get("x")).doubleValue())>9007199254740991d || !finite(p.get("y")))throw invalid(); }
    private static long micros(Object v)throws IOException {
        String s=v instanceof String||v instanceof Number?v.toString():"";
        if(!s.matches("[0-9]+"))throw invalid();try {long n=Long.parseLong(s);if(n>9007199254740991L)throw invalid();return n;}catch(NumberFormatException e){throw invalid();}
    }
    private static boolean finite(Object v){return v instanceof Number&&Double.isFinite(((Number)v).doubleValue());}
    private static boolean empty(Object v){return v==null||"".equals(v);}
    private static String text(Object v)throws IOException {if(!(v instanceof String)||((String)v).isEmpty())throw invalid();return (String)v;}
    private static Map<?,?> map(Object v)throws IOException {if(!(v instanceof Map))throw invalid();return (Map<?,?>)v;}
    private static List<?> list(Object v)throws IOException {if(!(v instanceof List))throw invalid();return (List<?>)v;}
    private static IOException invalid(){return new IOException("原生滤镜关键帧缺少有效的参数绑定、曲线或时间");}
    private BrowserNativeEffectKeyframes(){}
}
