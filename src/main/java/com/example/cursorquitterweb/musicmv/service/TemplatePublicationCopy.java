package com.example.cursorquitterweb.musicmv.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.http.HttpStatus;

final class TemplatePublicationCopy {
    private static final Pattern HAN = Pattern.compile("[\\p{IsHan}]");
    private TemplatePublicationCopy() { }

    static void requireComplete(List<Map<String, Object>> translations) {
        requireSources(translations);
        for(String locale:TemplateTitleLocalizationService.LOCALES) {
            if(!TemplateTitleLocalizationService.validLocalizedTitle(locale,text(find(translations,locale),"name")))
                throw new ApiException(HttpStatus.CONFLICT,"TEMPLATE_MULTILINGUAL_TITLES_REQUIRED","发布前需要补齐十种语言的模板标题。");
        }
    }

    static void requireSources(List<Map<String, Object>> translations) {
        Map<String, Object> zh = find(translations, "zh-CN"), en = find(translations, "en");
        String zn = text(zh, "name"), zd = text(zh, "description");
        String ename = text(en, "name"), ed = text(en, "description");
        if (!HAN.matcher(zn).find() || !HAN.matcher(zd).find()
                || !ename.matches(".*[A-Za-z].*") || !ed.matches("(?s).*[A-Za-z].*")
                || HAN.matcher(ename + ed).find() || zd.length() < 12 || ed.length() < 30
                || generic(zn) || generic(ename) || branded(zn + zd + ename + ed)) {
            throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_BILINGUAL_COPY_REQUIRED",
                    "发布前请根据预览补齐具体的中文、英文名称和设计描述；不要使用草稿日期、泛称或复制同一语言。");
        }
    }

    private static boolean generic(String value) {
        return value.matches("(?i)(music template|music video|music player|wallpaper music player|single photo|音乐模板|音乐播放器|背景墙播放器[！!]?|一图|高级氛围感)");
    }
    private static boolean branded(String value) { return value.toLowerCase(java.util.Locale.ROOT).contains("capcut") || value.contains("剪映"); }
    private static Map<String, Object> find(List<Map<String, Object>> rows, String locale) {
        if (rows != null) for (Map<String, Object> row : rows) if (locale.equals(row.get("locale"))) return row;
        return java.util.Collections.emptyMap();
    }
    private static String text(Map<String, Object> row, String key) { Object v = row.get(key); return v == null ? "" : v.toString().trim(); }
}
