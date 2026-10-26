package com.example.cursorquitterweb.musicmv.service;

import com.example.cursorquitterweb.musicmv.repository.MusicMvTemplateCatalogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** 使用权威翻译服务和数据库写入逻辑执行可恢复的说明回填。 */
public final class TemplateDescriptionBackfill {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final TypeReference<List<Map<String,Object>>> ROWS=new TypeReference<List<Map<String,Object>>>() {};
    private static final TypeReference<Map<String,Map<String,String>>> COPY=new TypeReference<Map<String,Map<String,String>>>() {};
    private static final String SELECT="SELECT t.template_id,t.status,t.current_version_id,t.revision,tr.locale,tr.name,tr.description,tr.seo_title,tr.seo_description "
        +"FROM templates t LEFT JOIN template_translations tr ON tr.template_id=t.template_id WHERE t.deleted_at IS NULL ORDER BY t.template_id,tr.locale";

    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("Usage: snapshot|prepare|apply|verify <evidence-directory>");
        // 禁止 HTTP 调试日志输出认证信息或服务商原始响应。
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);
        Path directory=Paths.get(args[1]);Files.createDirectories(directory);
        Path backup=directory.resolve("before.json"), prepared=directory.resolve("descriptions.json");
        if("snapshot".equals(args[0])) {
            if(Files.exists(backup)) throw new IllegalStateException("Backup already exists; choose a new directory");
            List<Map<String,Object>> rows=database().query(SELECT).getRows();
            JSON.writerWithDefaultPrettyPrinter().writeValue(backup.toFile(),rows);
            System.out.println("Snapshot templates="+group(rows).size()+" rows="+rows.size());return;
        }
        List<Map<String,Object>> before=JSON.readValue(backup.toFile(),ROWS);
        Map<String,List<Map<String,Object>>> templates=group(before);
        Map<String,Map<String,String>> output=Files.exists(prepared)?JSON.readValue(prepared.toFile(),COPY):new LinkedHashMap<>();
        if("prepare".equals(args[0])) {
            TemplateTitleLocalizationService translator=new TemplateTitleLocalizationService(
                env("MUSIC_MV_TEXT_AI_API_KEY",env("DEEPSEEK_API_KEY","")),
                env("MUSIC_MV_TEXT_AI_URL",env("DEEPSEEK_API_URL","https://api.deepseek.com/chat/completions")),
                env("MUSIC_MV_TEXT_AI_MODEL",env("DEEPSEEK_API_MODEL","deepseek-v4-flash")));
            for(Map.Entry<String,List<Map<String,Object>>> entry:templates.entrySet()) {
                Map<String,String> source=TemplateTitleLocalizationService.descriptions(entry.getValue());
                if(output.containsKey(entry.getKey())) {validate(source,output.get(entry.getKey()));continue;}
                Map<String,String> translated=translator.prepareDescriptions(source,source.get("zh-CN"),source.get("en"));
                validate(source,translated);output.put(entry.getKey(),translated);
                Path temp=directory.resolve("descriptions.tmp");JSON.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(),output);
                Files.move(temp,prepared,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                System.out.println("Prepared "+output.size()+"/"+templates.size()+" "+entry.getKey());
            }
            return;
        }
        if(!output.keySet().equals(templates.keySet())) throw new IllegalStateException("Prepared template set differs from backup");
        D1DatabaseClient db=database();MusicMvTemplateCatalogRepository repository=new MusicMvTemplateCatalogRepository(db);
        Map<String,List<Map<String,Object>>> current=group(db.query(SELECT).getRows());
        if(!current.keySet().equals(templates.keySet())) throw new IllegalStateException("Template set changed; take a new backup and review");
        if("apply".equals(args[0])) {
            for(Map.Entry<String,List<Map<String,Object>>> entry:templates.entrySet()) {
                String id=entry.getKey();Map<String,String> source=TemplateTitleLocalizationService.descriptions(entry.getValue());
                Map<String,String> translated=output.get(id);validate(source,translated);
                verifyUnchanged(entry.getValue(),current.get(id),translated,false);
                if(TemplateTitleLocalizationService.descriptions(current.get(id)).equals(translated)) {System.out.println("Already applied "+id);continue;}
                long revision=((Number)entry.getValue().get(0).get("revision")).longValue();
                if(!repository.updateDescriptions(id,revision,source.get("zh-CN"),source.get("en"),translated))
                    throw new IllegalStateException("Concurrent change: "+id);
                Files.write(directory.resolve("applied.log"),(id+" revision="+(revision+1)+"\n").getBytes("UTF-8"),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                System.out.println("Applied "+id);
            }
            current=group(db.query(SELECT).getRows());
        } else if(!"verify".equals(args[0])) throw new IllegalArgumentException("Unknown operation");
        for(String id:templates.keySet()) verifyUnchanged(templates.get(id),current.get(id),output.get(id),true);
        JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("after.json").toFile(),db.query(SELECT).getRows());
        System.out.println("Verified "+templates.size()+" templates / "+templates.size()*10+" descriptions; titles, source descriptions, SEO and published pointers unchanged");
    }
    private static void validate(Map<String,String> source,Map<String,String> translated) {
        if(!Objects.equals(source.get("zh-CN"),translated.get("zh-CN"))||!Objects.equals(source.get("en"),translated.get("en")))
            throw new IllegalStateException("Source description changed");
        Map<String,String> target=new LinkedHashMap<>(translated);target.remove("en");target.remove("zh-CN");
        TemplateTitleLocalizationService.validateSuppliedDescriptions(source,target);
    }
    private static void verifyUnchanged(List<Map<String,Object>> before,List<Map<String,Object>> after,Map<String,String> expected,boolean requireApplied) {
        if(after==null||before.size()!=after.size()) throw new IllegalStateException("Translation rows changed");
        for(int i=0;i<before.size();i++) {
            Map<String,Object> a=before.get(i),b=after.get(i);String locale=String.valueOf(a.get("locale"));
            for(String key:Arrays.asList("template_id","locale","name","seo_title","seo_description","current_version_id","status"))
                if(!Objects.equals(a.get(key),b.get(key))) throw new IllegalStateException("Protected field changed: "+key+" "+a.get("template_id"));
            Object actual=b.get("description");
            if(requireApplied?!Objects.equals(expected.get(locale),actual):!Objects.equals(a.get("description"),actual)&&!Objects.equals(expected.get(locale),actual))
                throw new IllegalStateException("Description differs: "+a.get("template_id")+" "+locale);
        }
    }
    private static Map<String,List<Map<String,Object>>> group(List<Map<String,Object>> rows) {
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        for(Map<String,Object> row:rows) result.computeIfAbsent(String.valueOf(row.get("template_id")),k->new ArrayList<>()).add(row);return result;
    }
    private static String env(String key,String fallback) {String value=System.getenv(key);return value==null||value.isEmpty()?fallback:value;}
    private static D1DatabaseClient database() throws Exception {
        D1DatabaseClient client=new D1DatabaseClient(JSON,10000,30000);
        Map<String,Object> settings=new LinkedHashMap<>();settings.put("enabled",true);settings.put("baseUrl",env("MUSIC_MV_D1_BASE_URL","https://api.cloudflare.com/client/v4"));
        settings.put("accountId",env("MUSIC_MV_CLOUDFLARE_ACCOUNT_ID",""));settings.put("databaseId",env("MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID",""));settings.put("apiToken",env("MUSIC_MV_CLOUDFLARE_API_TOKEN",""));
        for(Map.Entry<String,Object> entry:settings.entrySet()) {java.lang.reflect.Field field=D1DatabaseClient.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(client,entry.getValue());}
        if(!client.isConfigured()) throw new IllegalStateException("D1 environment is not configured");return client;
    }
}
