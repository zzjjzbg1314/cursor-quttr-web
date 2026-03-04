package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.config.WechatConfig;
import com.example.cursorquitterweb.dto.WechatUserInfo;
import com.example.cursorquitterweb.service.WechatService;
import com.example.cursorquitterweb.util.LogUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信服务实现类
 */
@Service
public class WechatServiceImpl implements WechatService {
    
    private static final Logger logger = LogUtil.getLogger(WechatServiceImpl.class);
    
    @Autowired
    private WechatConfig wechatConfig;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public WechatUserInfo login(String code) {
        LogUtil.logInfo(logger, "开始微信登录，授权码: {}", code);
        
        try {
            // 1. 使用开放平台 OAuth code 获取 access_token / openid
            Map<String, String> oauthInfo = getOauthAccessInfo(code);
            if (oauthInfo == null) {
                LogUtil.logError(logger, "获取微信 OAuth access_token 失败");
                throw new RuntimeException("获取微信授权信息失败");
            }
            
            String openId = oauthInfo.get("openid");
            String unionid = oauthInfo.get("unionid");
            LogUtil.logInfo(logger, "获取到openid: {}, unionid: {}", openId, unionid);
            
            // 2. 不使用微信返回的昵称和头像，统一使用系统默认生成值
            WechatUserInfo userInfo = new WechatUserInfo();
            userInfo.setOpenId(openId);
            userInfo.setUnionid(unionid);
            userInfo.setNickname(generateNickname());
            userInfo.setHeadimgurl(generateDefaultAvatarUrl());
            
            LogUtil.logInfo(logger, "微信登录成功，用户信息: {}", userInfo);
            return userInfo;
            
        } catch (Exception e) {
            LogUtil.logError(logger, "微信登录失败", e);
            throw new RuntimeException("微信登录失败: " + e.getMessage());
        }
    }
    
    /**
     * 使用微信开放平台 OAuth code 获取 access_token / openid
     */
    private Map<String, String> getOauthAccessInfo(String code) {
        try {
            String url = String.format("%s?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
                    wechatConfig.getOauthAccessTokenUrl(),
                    wechatConfig.getAppId(),
                    wechatConfig.getAppSecret(),
                    code);
            
            LogUtil.logDebug(logger, "请求微信 OAuth URL: {}", url);
            
            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
            byte[] responseBody = response.getBody();

            LogUtil.logDebug(logger, "微信 OAuth 响应字节长度: {}", responseBody != null ? responseBody.length : 0);

            if (responseBody == null || responseBody.length == 0) {
                LogUtil.logError(logger, "微信 OAuth 响应为空");
                return null;
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            if (jsonNode.has("errcode") && jsonNode.get("errcode").asInt() != 0) {
                String errorMsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "未知错误";
                LogUtil.logError(logger, "微信 OAuth 获取失败: {}", errorMsg);
                return null;
            }
            
            if (!jsonNode.has("openid") || !jsonNode.has("access_token")) {
                LogUtil.logError(logger, "微信 OAuth 响应缺少必要字段");
                return null;
            }

            Map<String, String> oauthInfo = new HashMap<>();
            oauthInfo.put("openid", jsonNode.get("openid").asText());
            oauthInfo.put("access_token", jsonNode.get("access_token").asText());
            
            // unionid可能不存在
            if (jsonNode.has("unionid")) {
                oauthInfo.put("unionid", jsonNode.get("unionid").asText());
            }
            
            return oauthInfo;
            
        } catch (Exception e) {
            LogUtil.logError(logger, "获取微信 OAuth 信息失败", e);
            return null;
        }
    }

    /**
     * 生成默认昵称（与其他登录方式保持一致）
     */
    private String generateNickname() {
        final String allowedCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        final int nicknameLength = 10;
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder nicknameBuilder = new StringBuilder(nicknameLength);
        for (int i = 0; i < nicknameLength; i++) {
            int randomIndex = secureRandom.nextInt(allowedCharacters.length());
            nicknameBuilder.append(allowedCharacters.charAt(randomIndex));
        }
        return nicknameBuilder.toString();
    }

    /**
     * 生成默认头像（与其他登录方式保持一致）
     */
    private String generateDefaultAvatarUrl() {
        int randomNumber = (int) (Math.random() * 30) + 1;
        return "https://image.kejiapi.cn/image/xiaohongshu/" + randomNumber + ".jpg";
    }
}
