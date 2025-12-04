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
    
    @Value("${deepseek.api.url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;
    
    @Value("${deepseek.api.model:deepseek-chat}")
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
}

