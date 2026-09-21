package com.example.cursorquitterweb.musicmv.service;

import java.util.*;
import org.springframework.http.HttpStatus;
import com.example.cursorquitterweb.musicmv.support.ApiException;

/** 运营标签独立于来源 Hashtag；首次初始化仅补标一次旧生日模板。 */
public class TemplateTagService {
    private final D1DatabaseClient d1;
    private volatile boolean ready;
    public TemplateTagService(D1DatabaseClient d1) { this.d1 = d1; }
    private static final String[][] SEEDS = {
        {"valentines-day", "情人节", "Valentine's Day"}, {"mothers-day", "母亲节", "Mother's Day"},
        {"fathers-day", "父亲节", "Father's Day"}, {"birthday", "生日", "Birthday"},
        {"wedding", "婚礼", "Wedding"},
        {"graduation", "毕业", "Graduation"}, {"christmas", "圣诞节", "Christmas"}
    };
    private ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "TEMPLATE_TAG_INVALID", message); }
    private synchronized void ensure() {
        if (ready) return;
        List<D1Statement> sql = new ArrayList<>();
        sql.add(D1Statement.of("CREATE TABLE IF NOT EXISTS template_tags (tag_key TEXT PRIMARY KEY,name_zh TEXT NOT NULL UNIQUE,name_en TEXT NOT NULL COLLATE NOCASE UNIQUE,sort_order INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)"));
        sql.add(D1Statement.of("CREATE TABLE IF NOT EXISTS template_tag_items (template_id TEXT NOT NULL REFERENCES templates(template_id) ON DELETE CASCADE,tag_key TEXT NOT NULL REFERENCES template_tags(tag_key),PRIMARY KEY(template_id,tag_key))"));
        sql.add(D1Statement.of("CREATE INDEX IF NOT EXISTS idx_template_tag_items_tag ON template_tag_items(tag_key,template_id)"));
        sql.add(D1Statement.of("CREATE TABLE IF NOT EXISTS template_tag_migrations (migration_key TEXT PRIMARY KEY,applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)"));
        sql.add(D1Statement.of("CREATE TABLE IF NOT EXISTS template_tag_translations (tag_key TEXT NOT NULL REFERENCES template_tags(tag_key) ON DELETE CASCADE,locale TEXT NOT NULL,name TEXT NOT NULL,PRIMARY KEY(tag_key,locale),UNIQUE(locale,name COLLATE NOCASE))"));
        int order = 0;
        for (String[] seed : SEEDS) sql.add(D1Statement.of("INSERT OR IGNORE INTO template_tags(tag_key,name_zh,name_en,sort_order) VALUES (?,?,?,?)", seed[0],seed[1],seed[2],order++));
        // 保留旧列兼容已部署数据；展示名称统一从可扩展翻译表读取。
        sql.add(D1Statement.of("INSERT OR IGNORE INTO template_tag_translations(tag_key,locale,name) SELECT tag_key,'zh-CN',name_zh FROM template_tags"));
        sql.add(D1Statement.of("INSERT OR IGNORE INTO template_tag_translations(tag_key,locale,name) SELECT tag_key,'en',name_en FROM template_tags"));
        sql.add(D1Statement.of("INSERT OR IGNORE INTO template_tag_items(template_id,tag_key) SELECT t.template_id,'birthday' FROM templates t WHERE (t.category_key='birthday' OR EXISTS (SELECT 1 FROM template_category_items c WHERE c.template_id=t.template_id AND c.category_key='birthday')) AND NOT EXISTS (SELECT 1 FROM template_tag_migrations WHERE migration_key='birthday-v1')"));
        sql.add(D1Statement.of("INSERT OR IGNORE INTO template_tag_migrations(migration_key) VALUES ('birthday-v1')"));
        // 一次性合并纪念日标签，保留模板关联并处理已经同时勾选两个标签的情况。
        sql.add(D1Statement.of("INSERT OR IGNORE INTO template_tag_items(template_id,tag_key) SELECT template_id,'wedding' FROM template_tag_items WHERE tag_key='wedding-anniversary' AND NOT EXISTS (SELECT 1 FROM template_tag_migrations WHERE migration_key='merge-wedding-v1')"));
        sql.add(D1Statement.of("DELETE FROM template_tag_items WHERE tag_key='wedding-anniversary' AND NOT EXISTS (SELECT 1 FROM template_tag_migrations WHERE migration_key='merge-wedding-v1')"));
        sql.add(D1Statement.of("DELETE FROM template_tag_translations WHERE tag_key='wedding-anniversary' AND NOT EXISTS (SELECT 1 FROM template_tag_migrations WHERE migration_key='merge-wedding-v1')"));
        sql.add(D1Statement.of("DELETE FROM template_tags WHERE tag_key='wedding-anniversary' AND NOT EXISTS (SELECT 1 FROM template_tag_migrations WHERE migration_key='merge-wedding-v1')"));
        sql.add(D1Statement.of("INSERT OR IGNORE INTO template_tag_migrations(migration_key) VALUES ('merge-wedding-v1')"));
        d1.batch(sql);
        ready = true;
    }
    private List<Map<String,Object>> rows(String sql, Object... args) {
        D1QueryResult result = d1.query(sql, args);
        return result == null ? Collections.emptyList() : result.getRows();
    }
    public Map<String,Object> list() { return list("zh-CN"); }
    public Map<String,Object> list(String locale) {
        ensure();
        List<Map<String,Object>> items = rows("SELECT tag_key AS key,sort_order AS sortOrder FROM template_tags ORDER BY sort_order,tag_key");
        Map<String,Map<String,String>> translations = new LinkedHashMap<>();
        for (Map<String,Object> row : rows("SELECT tag_key,locale,name FROM template_tag_translations ORDER BY locale")) {
            translations.computeIfAbsent(String.valueOf(row.get("tag_key")), key -> new LinkedHashMap<>())
                    .put(String.valueOf(row.get("locale")), String.valueOf(row.get("name")));
        }
        List<Map<String,Object>> resultItems = new ArrayList<>();
        for (Map<String,Object> row : items) {
            Map<String,Object> item = new LinkedHashMap<>(row);
            Map<String,String> names = translations.getOrDefault(String.valueOf(row.get("key")), Collections.emptyMap());
            item.put("translations", names); item.put("nameZh", names.get("zh-CN")); item.put("nameEn", names.get("en"));
            item.put("name", localizedName(names, locale)); resultItems.add(item);
        }
        Map<String,Object> result = new LinkedHashMap<>(); result.put("items", resultItems); return result;
    }
    static String localizedName(Map<String,String> names, String locale) {
        String wanted = locale == null || locale.trim().isEmpty() ? "zh-CN" : Locale.forLanguageTag(locale).toLanguageTag();
        if (names.containsKey(wanted)) return names.get(wanted);
        String language = Locale.forLanguageTag(wanted).getLanguage();
        if (names.containsKey(language)) return names.get(language);
        return names.getOrDefault("en", names.getOrDefault("zh-CN", ""));
    }
    public Map<String,Object> add(Map<String,Object> request) {
        String key = text(request.get("key"));
        if (!key.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*") || key.length() > 64) throw invalid("标签标识须为 1–64 位小写英文、数字和连字符");
        Map<String,String> names = new LinkedHashMap<>();
        if (request.containsKey("translations")) {
            if (!(request.get("translations") instanceof Map)) throw invalid("标签翻译格式不正确");
            for (Map.Entry<?,?> entry : ((Map<?,?>)request.get("translations")).entrySet()) {
                String language = text(entry.getKey());
                if (!language.matches("[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*") || language.length() > 35) throw invalid("请使用有效语言标识");
                String normalized = Locale.forLanguageTag(language).toLanguageTag();
                if ("und".equals(normalized) || names.containsKey(normalized)) throw invalid("语言标识重复或无效");
                names.put(normalized, text(entry.getValue()));
            }
        }
        if (request.containsKey("nameZh")) names.put("zh-CN",text(request.get("nameZh")));
        if (request.containsKey("nameEn")) names.put("en",text(request.get("nameEn")));
        if (!names.containsKey("zh-CN") || !names.containsKey("en") || names.size() > 100) throw invalid("请填写中文和英文名称");
        for (String name : names.values()) if (name.isEmpty() || name.length() > 80) throw invalid("翻译名称须为 1–80 字");
        ensure();
        if (!rows("SELECT tag_key FROM template_tags WHERE tag_key=?",key).isEmpty()) throw duplicate();
        for (Map.Entry<String,String> entry : names.entrySet()) {
            if (!rows("SELECT tag_key FROM template_tag_translations WHERE locale=? AND name=? COLLATE NOCASE",entry.getKey(),entry.getValue()).isEmpty()) throw duplicate();
        }
        List<D1Statement> sql = new ArrayList<>();
        sql.add(D1Statement.of("INSERT INTO template_tags(tag_key,name_zh,name_en,sort_order) VALUES (?,?,?,(SELECT COALESCE(MAX(sort_order),0)+1 FROM template_tags))",key,names.get("zh-CN"),names.get("en")));
        for (Map.Entry<String,String> entry : names.entrySet()) sql.add(D1Statement.of("INSERT INTO template_tag_translations(tag_key,locale,name) VALUES (?,?,?)",key,entry.getKey(),entry.getValue()));
        // 单个事务保存标识与全部翻译，失败时不留下半个标签。
        d1.batch(sql);
        return list();
    }
    private ApiException duplicate() { return new ApiException(HttpStatus.CONFLICT,"TEMPLATE_TAG_EXISTS","标签标识或名称已存在"); }
    private String text(Object value) { return value instanceof String ? ((String)value).trim() : ""; }
    public List<String> validate(List<String> keys) {
        if (keys == null) return null;
        if (keys.size() > 50) throw invalid("每个模板最多关联 50 个标签");
        ensure();
        Set<String> known = new HashSet<>();
        for (Map<String,Object> row : rows("SELECT tag_key FROM template_tags")) known.add(String.valueOf(row.get("tag_key")));
        List<String> clean = new ArrayList<>();
        for (String key : keys) {
            if (key == null || !known.contains(key)) throw invalid("包含不存在的标签，请刷新后重新选择");
            if (!clean.contains(key)) clean.add(key);
        }
        return clean;
    }
    public List<String> keys(String templateId) {
        ensure();
        List<String> result = new ArrayList<>();
        for (Map<String,Object> row : rows("SELECT tag_key FROM template_tag_items WHERE template_id=? ORDER BY tag_key", templateId)) result.add(String.valueOf(row.get("tag_key")));
        return result;
    }
    public void replace(String templateId, List<String> keys) {
        if (keys == null) return;
        List<String> clean = validate(keys);
        List<D1Statement> sql = new ArrayList<>();
        sql.add(D1Statement.of("DELETE FROM template_tag_items WHERE template_id=?", templateId));
        for (String key : clean) sql.add(D1Statement.of("INSERT INTO template_tag_items(template_id,tag_key) VALUES (?,?)", templateId,key));
        d1.batch(sql);
    }
}
