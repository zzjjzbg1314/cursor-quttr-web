package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.service.CommunityContentTranslationService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import com.example.cursorquitterweb.util.DeepSeekApiUtil;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CommunityContentTranslationServiceImpl implements CommunityContentTranslationService {

    private static final Logger logger = LogUtil.getLogger(CommunityContentTranslationServiceImpl.class);
    private static final String[] SUPPORTED_LANGUAGES = {"zh", "en", "ja", "ko", "de", "fr", "pt", "es"};

    @Autowired
    private CloudflareD1Util d1Util;

    @Autowired
    private DeepSeekApiUtil deepSeekApiUtil;

    @Override
    public String normalizeOriginalLanguage(String originalLanguage, String content) {
        if (originalLanguage != null && !originalLanguage.trim().isEmpty()) {
            String lang = originalLanguage.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            int separatorIndex = lang.indexOf('-');
            if (separatorIndex > 0) {
                lang = lang.substring(0, separatorIndex);
            }
            for (String supportedLanguage : SUPPORTED_LANGUAGES) {
                if (supportedLanguage.equals(lang)) {
                    return lang;
                }
            }
        }
        if (containsScript(content, Character.UnicodeScript.HAN)) {
            return "zh";
        }
        if (containsScript(content, Character.UnicodeScript.HIRAGANA)
            || containsScript(content, Character.UnicodeScript.KATAKANA)) {
            return "ja";
        }
        if (containsScript(content, Character.UnicodeScript.HANGUL)) {
            return "ko";
        }
        return "en";
    }

    @Async("communityTranslationExecutor")
    @Override
    public void translatePostAsync(UUID postId, String content, String originalLanguage) {
        translateAsync("posts", "post_id", postId, content, originalLanguage, "post");
    }

    @Async("communityTranslationExecutor")
    @Override
    public void translateCommentAsync(UUID commentId, String content, String originalLanguage) {
        translateAsync("comments", "comment_id", commentId, content, originalLanguage, "comment");
    }

    private void translateAsync(String tableName, String idColumn, UUID id, String content, String originalLanguage, String contentType) {
        if (id == null || content == null || content.trim().isEmpty()) {
            return;
        }

        String normalizedLanguage = normalizeOriginalLanguage(originalLanguage, content);
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("translation_status", "processing");
            data.put("updated_at", EntityMapper.offsetDateTimeToString(OffsetDateTime.now()));
            d1Util.updateById(tableName, data, idColumn, EntityMapper.uuidToString(id));

            Map<String, Object> translations = new HashMap<>();
            for (String targetLanguage : SUPPORTED_LANGUAGES) {
                String translatedContent = normalizedLanguage.equals(targetLanguage)
                    ? content
                    : deepSeekApiUtil.translateCommunityText(content, contentType, targetLanguage, null);
                translations.put("content_" + targetLanguage, translatedContent);
            }
            translations.put("translation_status", "completed");
            translations.put("translated_at", EntityMapper.offsetDateTimeToString(OffsetDateTime.now()));
            translations.put("updated_at", EntityMapper.offsetDateTimeToString(OffsetDateTime.now()));
            d1Util.updateById(tableName, translations, idColumn, EntityMapper.uuidToString(id));
        } catch (Exception e) {
            logger.error("社区内容翻译失败，table: {}, id: {}", tableName, id, e);
            Map<String, Object> failed = new HashMap<>();
            failed.put("translation_status", "failed");
            failed.put("updated_at", EntityMapper.offsetDateTimeToString(OffsetDateTime.now()));
            d1Util.updateById(tableName, failed, idColumn, EntityMapper.uuidToString(id));
        }
    }

    private boolean containsScript(String text, Character.UnicodeScript script) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == script) {
                return true;
            }
        }
        return false;
    }
}
