package com.example.cursorquitterweb.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek API工具类
 * 用于调用DeepSeek API生成戒色主题的英文内容
 */
@Component
public class DeepSeekApiUtil {
    
    private static final Logger logger = LogUtil.getLogger(DeepSeekApiUtil.class);
    
    @Value("${deepseek.api.key:}")
    private String apiKey;
    
    @Value("${deepseek.api.url:https://api.deepseek.com/chat/completions}")
    private String apiUrl;
    
    @Value("${deepseek.api.model:deepseek-v4-flash}")
    private String model;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public DeepSeekApiUtil() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 生成戒色主题的英文帖子内容
     * 
     * @return 生成的帖子内容
     */
    public String generatePostContent() {
        String prompt = "Generate a motivational post in English about quitting pornography addiction and maintaining self-control. " +
                "The post should be encouraging, supportive, and focused on personal growth and recovery. " +
                "Keep it between 100-200 words. Make it natural and authentic, as if written by someone sharing their journey.";
        
        return generateContent(prompt);
    }
    
    /**
     * 生成戒色主题的英文评论内容
     * 
     * @param postContent 帖子内容，用于生成相关评论
     * @return 生成的评论内容
     */
    public String generateCommentContent(String postContent) {
        String prompt = "Generate a supportive comment in English responding to a post about quitting pornography addiction. " +
                "The comment should be encouraging, empathetic, and show understanding. " +
                "Keep it between 30-80 words. Make it natural and conversational.";
        
        return generateContent(prompt);
    }
    
    /**
     * 生成戒色主题的英文回复内容
     * 
     * @param commentContent 评论内容，用于生成相关回复
     * @return 生成的回复内容
     */
    public String generateReplyContent(String commentContent) {
        String prompt = "Generate a brief reply in English to a comment about quitting pornography addiction. " +
                "The reply should be supportive and encouraging. " +
                "Keep it between 20-50 words. Make it natural and conversational.";
        
        return generateContent(prompt);
    }

    /**
     * 将中文社区帖子内容翻译成适合海外社区的英文
     *
     * @param sourceText 中文原文
     * @return 英文翻译
     */
    public String translateCommunityPostToEnglish(String sourceText) {
        return translateCommunityTextToEnglish(sourceText, "post", null);
    }

    /**
     * 将中文社区评论/回复内容翻译成适合海外社区的英文
     *
     * @param sourceText 中文原文
     * @param relatedContext 上下文，可传入对应帖子内容
     * @return 英文翻译
     */
    public String translateCommunityCommentToEnglish(String sourceText, String relatedContext) {
        return translateCommunityTextToEnglish(sourceText, "comment", relatedContext);
    }

    public String translateCommunityText(String sourceText, String contentType, String targetLanguage, String relatedContext) {
        return translateCommunityText(sourceText, contentType, targetLanguage, relatedContext, false);
    }
    
    /**
     * 调用DeepSeek API生成内容
     * 
     * @param prompt 提示词
     * @return 生成的内容
     */
    private String generateContent(String prompt) {
        if (apiKey == null || apiKey.isEmpty()) {
            LogUtil.logWarn(logger, "DeepSeek API密钥未配置，使用默认内容");
            return getDefaultContent(prompt);
        }
        
        try {
            // 构建请求URL（DeepSeek使用OpenAI兼容格式）
            // apiUrl 已经是完整的URL，不需要再拼接路径
            String url = apiUrl;
            
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            // 构建消息列表
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a helpful assistant that generates supportive and motivational content about personal growth and recovery.");
            messages.add(systemMessage);
            
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 500);
            requestBody.put("stream", false);
            
            // 发送请求
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // 解析响应
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode choices = jsonNode.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).get("message");
                    if (messageNode != null) {
                        JsonNode content = messageNode.get("content");
                        if (content != null) {
                            String generatedContent = content.asText().trim();
                            LogUtil.logInfo(logger, "DeepSeek API成功生成内容，长度: {}", generatedContent.length());
                            return generatedContent;
                        }
                    }
                }
            }
            
            LogUtil.logWarn(logger, "DeepSeek API响应解析失败，使用默认内容。响应状态: {}, 响应体: {}", 
                response.getStatusCode(), response.getBody());
            return getDefaultContent(prompt);
            
        } catch (Exception e) {
            LogUtil.logError(logger, "调用DeepSeek API失败", e);
            return getDefaultContent(prompt);
        }
    }

    /**
     * 严格模式翻译社区内容。该方法用于一次性数据同步，不允许静默降级成无关默认文案。
     */
    private String translateCommunityTextToEnglish(String sourceText, String contentType, String relatedContext) {
        return translateCommunityText(sourceText, contentType, "en", relatedContext, true);
    }

    private String translateCommunityText(String sourceText, String contentType, String targetLanguage, String relatedContext, boolean skipNonChinese) {
        if (sourceText == null) {
            return null;
        }

        String trimmedSource = sourceText.trim();
        if (trimmedSource.isEmpty() || (skipNonChinese && !containsChineseCharacters(trimmedSource))) {
            return trimmedSource;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("DeepSeek API密钥未配置，无法执行社区内容翻译");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content",
                "You are translating user-generated community content into natural " + languageName(targetLanguage) + " for a recovery app. " +
                "The topic is quitting pornography, recovery, relapse, self-control, and mutual support. " +
                "Preserve the original meaning, emotional tone, and humility. " +
                "Use safe, non-explicit language suitable for an app store community. " +
                "Do not add facts, do not moralize, do not turn it into marketing copy, and return only the translation text.");
            messages.add(systemMessage);

            StringBuilder prompt = new StringBuilder();
            prompt.append("Translate the following ").append(contentType)
                .append(" into fluent, concise ").append(languageName(targetLanguage)).append(".\n");
            if (relatedContext != null && !relatedContext.trim().isEmpty()) {
                prompt.append("Context for tone only:\n").append(relatedContext.trim()).append("\n\n");
            }
            prompt.append("Source text:\n").append(trimmedSource).append("\n\n")
                .append("Return only the translated text.");

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt.toString());
            messages.add(userMessage);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.2);
            requestBody.put("max_tokens", 900);
            requestBody.put("stream", false);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new IllegalStateException("DeepSeek翻译响应异常: " + response.getStatusCode());
            }

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            JsonNode choices = jsonNode.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                throw new IllegalStateException("DeepSeek翻译结果为空");
            }

            JsonNode content = choices.get(0).path("message").path("content");
            String translated = normalizeTranslatedContent(content.asText());
            if (translated.isEmpty()) {
                throw new IllegalStateException("DeepSeek翻译内容为空");
            }

            LogUtil.logInfo(logger, "DeepSeek翻译成功，类型: {}, 原文长度: {}, 译文长度: {}",
                contentType, trimmedSource.length(), translated.length());
            return translated;
        } catch (Exception e) {
            LogUtil.logError(logger, "DeepSeek社区内容翻译失败", e);
            throw new RuntimeException("社区内容翻译失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取默认内容（当API调用失败时使用）
     * 
     * @param prompt 提示词
     * @return 默认内容
     */
    private String getDefaultContent(String prompt) {
        if (prompt.contains("post")) {
            return "Today marks another day of my journey towards self-improvement. " +
                    "Every moment of resistance makes me stronger. " +
                    "I'm grateful for this community that supports each other in our shared goal of living a better life. " +
                    "Stay strong, everyone!";
        } else if (prompt.contains("comment")) {
            return "Thank you for sharing your journey. Your strength inspires me to keep going. " +
                    "We're all in this together, and every day is a victory.";
        } else {
            return "I completely agree. Keep up the great work!";
        }
    }

    private boolean containsChineseCharacters(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTranslatedContent(String content) {
        if (content == null) {
            return "";
        }

        String normalized = content.trim();
        if (normalized.startsWith("Translation:")) {
            normalized = normalized.substring("Translation:".length()).trim();
        }
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
            || (normalized.startsWith("“") && normalized.endsWith("”"))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private String languageName(String languageCode) {
        if (languageCode == null) {
            return "English";
        }
        switch (languageCode.toLowerCase()) {
            case "zh":
                return "Simplified Chinese";
            case "en":
                return "English";
            case "ja":
                return "Japanese";
            case "ko":
                return "Korean";
            case "de":
                return "German";
            case "fr":
                return "French";
            case "pt":
                return "Portuguese";
            case "es":
                return "Spanish";
            default:
                return "English";
        }
    }
}
