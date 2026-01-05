package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.config.AgoraConfig;
import com.example.cursorquitterweb.util.LogUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 声网 RTM2 消息发送服务（使用RESTful API）
 * 用于机器人账户发送系统消息
 * 参考文档: https://doc.shengwang.cn/api-ref/rtm2/restful/toc-message/publish
 */
@Service
public class AgoraRtmMessageService {
    
    private static final Logger logger = LogUtil.getLogger(AgoraRtmMessageService.class);
    
    // 机器人用户ID
    private static final String ROBOT_USER_ID = "27bd792d-df66-459a-b52f-dda93656ebdf";
    
    // RTM2 RESTful API 基础URL
    private static final String RTM_API_BASE_URL = "https://api.sd-rtn.com/rtm/v2/publish";
    
    @Autowired
    private AgoraConfig agoraConfig;
    
    @Autowired
    private AgoraRtmTokenService agoraRtmTokenService;
    
    @Autowired
    private RestTemplate restTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 机器人Token缓存，12小时过期
    private Cache<String, String> robotTokenCache;
    
    /**
     * 初始化Token缓存
     */
    @PostConstruct
    public void init() {
        robotTokenCache = Caffeine.newBuilder()
            .maximumSize(1) // 只缓存一个token（机器人token）
            .expireAfterWrite(12, TimeUnit.HOURS) // 12小时后过期
            .build();
        
        logger.info("机器人Token缓存已初始化，过期时间：12小时");
    }
    
    /**
     * 获取机器人Token（带缓存）
     * 
     * @return Token字符串
     * @throws Exception 生成Token失败时抛出异常
     */
    private String getRobotToken() throws Exception {
        // 从缓存获取
        String cachedToken = robotTokenCache.getIfPresent(ROBOT_USER_ID);
        if (cachedToken != null) {
            logger.debug("从缓存获取机器人Token");
            return cachedToken;
        }
        
        // 缓存未命中，生成新Token
        logger.info("缓存未命中，生成新的机器人Token");
        String token = agoraRtmTokenService.generateToken(ROBOT_USER_ID);
        
        // 存入缓存
        robotTokenCache.put(ROBOT_USER_ID, token);
        logger.info("机器人Token已缓存，将在12小时后过期");
        
        return token;
    }
    
    /**
     * 发送系统消息到频道
     * 参考文档: https://doc.shengwang.cn/api-ref/rtm2/restful/toc-message/publish
     * 
     * @param channelId 频道ID
     * @param message 消息内容
     * @param senderName 发送者名称
     * @param senderAvatarUrl 发送者头像URL
     * @param senderPlanetName 发送者星球名称（可选）
     * @throws Exception 发送失败时抛出异常
     */
    public void sendSystemMessage(Integer channelId, String message, String senderName, 
                                  String senderAvatarUrl, String senderPlanetName) throws Exception {
        if (agoraConfig.getAppId() == null || agoraConfig.getAppId().isEmpty()) {
            throw new IllegalStateException("声网 App ID 未配置");
        }
        
        // 获取Token（带缓存，12小时过期）
        String token = getRobotToken();
        
        // 后端代码应该改为：
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("content", message);  // ← 改为 'content'，不是 'message'
        messageData.put("senderName", senderName);
        messageData.put("senderAvatarUrl", senderAvatarUrl);
        if (senderPlanetName != null) {
            messageData.put("senderPlanetName", senderPlanetName);
        }
        
        String messageJson = objectMapper.writeValueAsString(messageData);
        
        // 构建请求URL
        // POST https://api.sd-rtn.com/rtm/v2/publish/{appId}/userId/{userId}/channelType/{channelType}/channel/{channelName}?storeInHistory=true&customType={customType}
        String url = String.format("%s/%s/userId/%s/channelType/message/channel/%s?storeInHistory=true&customType=SystemMessage",
            RTM_API_BASE_URL,
            agoraConfig.getAppId(),
            ROBOT_USER_ID,
            channelId
        );
        
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "agora token=" + token);
        
        // 创建请求实体
        HttpEntity<String> requestEntity = new HttpEntity<>(messageJson, headers);
        
        try {
            // 记录请求详情
            logger.info("发送RTM消息请求 - URL: {}, 频道ID: {}, 消息内容: {}", url, channelId, message);
            logger.debug("请求体: {}", messageJson);
            
            // 发送POST请求
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            // 记录完整响应
            logger.info("RTM API响应 - HTTP状态码: {}, 响应体: {}", response.getStatusCode(), response.getBody());
            
            // 检查响应
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody == null) {
                    logger.error("RTM API返回空响应体");
                    throw new RuntimeException("发送RTM消息失败，响应体为空");
                }
                
                // 记录完整响应体以便调试
                logger.debug("RTM API完整响应: {}", objectMapper.writeValueAsString(responseBody));
                
                // 检查错误码（优先检查errorCode）
                Object errorCodeObj = responseBody.get("errorCode");
                if (errorCodeObj != null) {
                    int errorCode = ((Number) errorCodeObj).intValue();
                    logger.info("RTM API返回错误码: {}", errorCode);
                    if (errorCode == 200) {
                        logger.info("系统消息发送成功，频道ID: {}, 消息: {}", channelId, message);
                        return;
                    } else {
                        String reason = (String) responseBody.get("reason");
                        logger.error("RTM API返回错误，错误码: {}, 原因: {}", errorCode, reason);
                        throw new RuntimeException(String.format("发送RTM消息失败，错误码: %d, 原因: %s", errorCode, reason));
                    }
                }
                
                // 如果没有errorCode字段，检查error字段
                Object errorObj = responseBody.get("error");
                if (errorObj != null) {
                    boolean isError = Boolean.TRUE.equals(errorObj);
                    logger.info("RTM API返回error字段: {}", isError);
                    if (isError) {
                        String reason = (String) responseBody.get("reason");
                        logger.error("RTM API返回错误，原因: {}", reason);
                        throw new RuntimeException("发送RTM消息失败: " + (reason != null ? reason : "未知错误"));
                    }
                }
                
                // 如果既没有errorCode也没有error字段，记录警告并抛出异常
                if (errorCodeObj == null && errorObj == null) {
                    logger.error("RTM API响应格式异常：既没有errorCode也没有error字段，响应体: {}", 
                        objectMapper.writeValueAsString(responseBody));
                    throw new RuntimeException("RTM API响应格式异常，无法判断是否成功。响应体: " + 
                        objectMapper.writeValueAsString(responseBody));
                }
                
                logger.info("系统消息发送成功，频道ID: {}, 消息: {}", channelId, message);
            } else {
                logger.error("RTM API返回非2xx状态码: {}", response.getStatusCode());
                throw new RuntimeException("发送RTM消息失败，HTTP状态码: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            logger.error("发送RTM消息时发生网络错误，频道ID: {}, 消息: {}, 错误详情: {}", 
                channelId, message, e.getMessage(), e);
            throw new RuntimeException("发送RTM消息失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("发送RTM消息时发生未知错误，频道ID: {}, 消息: {}", channelId, message, e);
            throw e;
        }
    }
}
