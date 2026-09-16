package com.example.cursorquitterweb.musicmv.repository;

import com.example.cursorquitterweb.musicmv.service.TemplateTopics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;

/** 搜索与计数共享同一谓词；分类从当前关联读取，新模板无需另建索引。 */
final class TemplateSearch {
    private static final Map<String,String> TOPICS=new LinkedHashMap<>();
    static {
        for(Map<String,Object> item:TemplateTopics.items()) {
            String key=(String)item.get("key");
            for(String field:Arrays.asList("key","nameZh","nameEn"))TOPICS.put(normalize((String)item.get(field)),key);
        }
        TemplateTopics.aliases().forEach((alias,key)->TOPICS.put(normalize(alias),key));
        try(InputStream input=TemplateSearch.class.getResourceAsStream("/template-search-aliases.json")) {
            JsonNode aliases=new ObjectMapper().readTree(input);
            aliases.fields().forEachRemaining(entry->{
                if(!TOPICS.containsValue(entry.getKey()))throw new IllegalArgumentException("Unknown search topic");
                for(JsonNode alias:entry.getValue())TOPICS.put(normalize(alias.asText()),entry.getKey());
            });
        } catch(Exception error) {throw new ExceptionInInitializerError(error);}
    }
    static String normalize(String text) {
        if(text==null)return "";
        return Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+"," ").trim();
    }
    static String topic(String text) {return TOPICS.get(normalize(text));}
    static void append(StringBuilder sql,List<Object> params,String keyword) {
        String query=normalize(keyword);if(query.isEmpty())return;
        String like="%"+query.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        sql.append("AND (lower(t.slug) LIKE ? ESCAPE '!' ")
           .append("OR EXISTS (SELECT 1 FROM template_translations sx WHERE sx.template_id=t.template_id AND (")
           .append("lower(sx.name) LIKE ? ESCAPE '!' OR lower(sx.description) LIKE ? ESCAPE '!')) ")
           .append("OR EXISTS (SELECT 1 FROM json_each(COALESCE(t.tags_json,'[]')) sk WHERE lower(sk.value) LIKE ? ESCAPE '!') ")
           .append("OR EXISTS (SELECT 1 FROM template_source_metadata sm WHERE sm.template_id=t.template_id AND (")
           .append("lower(sm.source_title) LIKE ? ESCAPE '!' OR lower(sm.source_description) LIKE ? ESCAPE '!' ")
           .append("OR lower(sm.source_category) LIKE ? ESCAPE '!' OR lower(sm.source_search_keyword) LIKE ? ESCAPE '!' ")
           .append("OR lower(sm.source_hashtags_json) LIKE ? ESCAPE '!')) ");
        for(int i=0;i<9;i++)params.add(like);
        String topic=topic(query);
        if(topic!=null) {
            sql.append("OR EXISTS (SELECT 1 FROM template_categories sc WHERE sc.enabled=1 AND sc.is_selectable=1 ")
               .append("AND sc.category_key=? AND (sc.category_key=t.category_key OR EXISTS (")
               .append("SELECT 1 FROM template_category_items si WHERE si.template_id=t.template_id AND si.category_key=sc.category_key))) ");
            params.add(topic);
        }
        sql.append(") ");
    }
    private TemplateSearch(){}
}
