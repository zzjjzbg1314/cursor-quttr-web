package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.config.PushConfig;
import com.example.cursorquitterweb.service.PushService;
import com.example.cursorquitterweb.util.LogUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 推送服务实现类
 * 调用 U-Push（友盟推送）API
 */
@Service
public class PushServiceImpl implements PushService {
    
    private static final Logger logger = LogUtil.getLogger(PushServiceImpl.class);
    
    @Autowired
    private PushConfig pushConfig;
    
    @Autowired
    private RestTemplate restTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 生成友盟推送 API 签名
     * 签名算法：
     * 1. 将所有请求参数（除了 sign 本身）按照 key 的字典序排序
     * 2. 拼接成 key1=value1key2=value2key3=value3 的格式（直接拼接，不用 & 分隔）
     * 3. 在末尾追加 masterSecret
     * 4. 对整个字符串进行 MD5 加密，得到 32 位小写的签名值
     */
    private String generateSign(Map<String, Object> params, String masterSecret) {
        try {
            // 使用 TreeMap 自动按 key 排序
            TreeMap<String, Object> sortedParams = new TreeMap<>(params);
            
            // 拼接参数字符串
            StringBuilder signString = new StringBuilder();
            for (Map.Entry<String, Object> entry : sortedParams.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                // 跳过 sign 参数本身
                if ("sign".equals(key)) {
                    continue;
                }
                
                // 处理嵌套对象（如 payload, policy 等）
                if (value instanceof Map) {
                    // 对于嵌套的 Map，需要递归处理或转换为 JSON 字符串
                    String jsonValue = objectMapper.writeValueAsString(value);
                    signString.append(key).append("=").append(jsonValue);
                } else if (value instanceof List) {
                    // 对于 List，转换为 JSON 字符串
                    String jsonValue = objectMapper.writeValueAsString(value);
                    signString.append(key).append("=").append(jsonValue);
                } else {
                    // 对于基本类型，直接转换为字符串
                    signString.append(key).append("=").append(value != null ? value.toString() : "");
                }
            }
            
            // 在末尾追加 masterSecret
            signString.append(masterSecret);
            
            logger.debug("签名原始字符串: {}", signString.toString());
            
            // MD5 加密
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(signString.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String sign = sb.toString();
            logger.debug("生成的签名: {}", sign);
            return sign;
        } catch (Exception e) {
            logger.error("生成签名失败", e);
            throw new RuntimeException("生成签名失败", e);
        }
    }
    
    /**
     * 获取当前时间戳（秒）
     */
    private String getTimestamp() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }
    
    /**
     * 将 ISO 8601 格式的时间转换为友盟推送需要的格式（yyyy-MM-dd HH:mm:ss）
     */
    private String convertTriggerTime(String isoTime) {
        try {
            // ISO 8601 格式: 2026-01-26T00:51:39.679341Z
            // 移除毫秒和 Z，转换为: 2026-01-26 00:51:39
            String timeStr = isoTime.replace("T", " ").replace("Z", "");
            if (timeStr.contains(".")) {
                timeStr = timeStr.substring(0, timeStr.indexOf("."));
            }
            return timeStr;
        } catch (Exception e) {
            logger.error("时间格式转换失败: {}", isoTime, e);
            throw new RuntimeException("时间格式转换失败", e);
        }
    }
    
    @Override
    public boolean schedulePush(String deviceToken, String title, String content, 
                               String triggerTime, Integer notificationId, String platform) throws Exception {
        logger.info("安排定时推送 - deviceToken: {}, title: {}, notificationId: {}, platform: {}", 
                   deviceToken, title, notificationId, platform);
        
        // 根据平台选择 AppKey 和 MasterSecret
        String appKey;
        String masterSecret;
        if ("ios".equalsIgnoreCase(platform)) {
            appKey = pushConfig.getIosAppKey();
            masterSecret = pushConfig.getIosMasterSecret();
        } else if ("android".equalsIgnoreCase(platform)) {
            appKey = pushConfig.getAndroidAppKey();
            masterSecret = pushConfig.getAndroidMasterSecret();
        } else {
            throw new IllegalArgumentException("不支持的平台: " + platform + "，仅支持 ios 或 android");
        }
        
        if (appKey == null || appKey.isEmpty()) {
            throw new IllegalStateException("平台 " + platform + " 的 AppKey 未配置");
        }
        
        // 构建请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("appkey", appKey);
        payload.put("timestamp", getTimestamp());
        
        // 构建推送消息体
        Map<String, Object> payloadBody = new HashMap<>();
        payloadBody.put("display_type", "notification"); // 通知类型
        
        // 构建通知内容（根据平台调整）
        Map<String, Object> body = new HashMap<>();
        if ("ios".equalsIgnoreCase(platform)) {
            // iOS 推送格式
            Map<String, Object> aps = new HashMap<>();
            Map<String, Object> alert = new HashMap<>();
            alert.put("title", title);
            alert.put("body", content);
            aps.put("alert", alert);
            aps.put("sound", "default");
            aps.put("badge", 1);
            body.put("aps", aps);
        } else {
            // Android 推送格式
            body.put("ticker", title);
            body.put("title", title);
            body.put("text", content);
            body.put("after_open", "go_app"); // 点击后打开应用
        }
        
        payloadBody.put("body", body);
        payload.put("payload", payloadBody);
        
        // 构建推送策略（定时推送）
        Map<String, Object> policy = new HashMap<>();
        policy.put("start_time", convertTriggerTime(triggerTime)); // 定时推送时间
        policy.put("expire_time", ""); // 过期时间，空表示不过期
        
        payload.put("policy", policy);
        
        // 构建设备Token列表（友盟推送需要数组格式）
        List<String> deviceTokens = new ArrayList<>();
        deviceTokens.add(deviceToken);
        payload.put("device_tokens", String.join(",", deviceTokens)); // 友盟推送使用逗号分隔的字符串
        
        // 构建描述信息
        payload.put("description", "定时推送 - " + title);
        
        // 设置推送类型为单播（unicast）
        payload.put("type", "unicast");
        
        // 生成签名（在转换为 JSON 之前，因为签名需要基于参数 Map）
        String sign = generateSign(payload, masterSecret);
        payload.put("sign", sign);
        
        // 转换为 JSON（包含签名）
        String postBody = objectMapper.writeValueAsString(payload);
        
        String url = "/api/send";
        
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/5.0");
        
        HttpEntity<String> requestEntity = new HttpEntity<>(postBody, headers);
        
        // 调用友盟推送 API
        String apiUrl = pushConfig.getBaseUrl() + url;
        logger.info("调用友盟推送 API: {}", apiUrl);
        logger.debug("请求体: {}", postBody);
        
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            logger.info("友盟推送 API 响应状态: {}", response.getStatusCode());
            logger.debug("响应体: {}", response.getBody());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null) {
                    String ret = (String) responseBody.get("ret");
                    if ("SUCCESS".equals(ret)) {
                        logger.info("定时推送安排成功 - notificationId: {}", notificationId);
                        return true;
                    } else {
                        String errorMsg = "友盟推送 API 返回错误: " + responseBody.get("data");
                        logger.error(errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                }
            }
            
            logger.error("友盟推送 API 调用失败，状态码: {}", response.getStatusCode());
            throw new RuntimeException("友盟推送 API 调用失败");
            
        } catch (RestClientException e) {
            logger.error("调用友盟推送 API 异常", e);
            throw new Exception("调用友盟推送 API 异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean cancelPush(Integer notificationId) throws Exception {
        logger.info("取消推送 - notificationId: {}", notificationId);
        
        // 注意：友盟推送的取消推送 API 需要根据实际的 API 文档来实现
        // 这里提供一个通用的实现方式
        
        // 由于友盟推送的取消推送可能需要任务ID而不是通知ID
        // 这里假设 notificationId 就是友盟推送返回的任务ID
        
        // 构建请求体
        Map<String, Object> payload = new HashMap<>();
        
        // 尝试使用 iOS 配置（如果 notificationId 对应的是 iOS 推送）
        // 实际实现中可能需要存储 platform 信息
        String appKey = pushConfig.getIosAppKey();
        String masterSecret = pushConfig.getIosMasterSecret();
        
        if (appKey == null || appKey.isEmpty()) {
            // 尝试使用 Android 配置
            appKey = pushConfig.getAndroidAppKey();
            masterSecret = pushConfig.getAndroidMasterSecret();
        }
        
        if (appKey == null || appKey.isEmpty()) {
            throw new IllegalStateException("AppKey 未配置");
        }
        
        payload.put("appkey", appKey);
        payload.put("timestamp", getTimestamp());
        payload.put("task_id", notificationId.toString()); // 假设 notificationId 是任务ID
        
        // 生成签名（在转换为 JSON 之前，因为签名需要基于参数 Map）
        String sign = generateSign(payload, masterSecret);
        payload.put("sign", sign);
        
        // 转换为 JSON（包含签名）
        String postBody = objectMapper.writeValueAsString(payload);
        
        String url = "/api/cancel";
        
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/5.0");
        
        HttpEntity<String> requestEntity = new HttpEntity<>(postBody, headers);
        
        // 调用友盟推送 API
        String apiUrl = pushConfig.getBaseUrl() + url;
        logger.info("调用友盟推送取消 API: {}", apiUrl);
        logger.debug("请求体: {}", postBody);
        
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            logger.info("友盟推送取消 API 响应状态: {}", response.getStatusCode());
            logger.debug("响应体: {}", response.getBody());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null) {
                    String ret = (String) responseBody.get("ret");
                    if ("SUCCESS".equals(ret)) {
                        logger.info("推送取消成功 - notificationId: {}", notificationId);
                        return true;
                    } else {
                        String errorMsg = "友盟推送取消 API 返回错误: " + responseBody.get("data");
                        logger.error(errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                }
            }
            
            logger.error("友盟推送取消 API 调用失败，状态码: {}", response.getStatusCode());
            throw new RuntimeException("友盟推送取消 API 调用失败");
            
        } catch (RestClientException e) {
            logger.error("调用友盟推送取消 API 异常", e);
            throw new Exception("调用友盟推送取消 API 异常: " + e.getMessage(), e);
        }
    }
}
