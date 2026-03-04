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
            String accessToken = oauthInfo.get("access_token");
            
            LogUtil.logInfo(logger, "获取到openid: {}, unionid: {}", openId, unionid);
            
            // 2. 尝试获取微信用户资料；若 scope 不足或接口失败，回退到系统默认值
            WechatUserInfo userInfo = new WechatUserInfo();
            userInfo.setOpenId(openId);
            userInfo.setUnionid(unionid);

            WechatUserInfo fetchedUserInfo = getWechatUserInfo(accessToken, openId);
            if (fetchedUserInfo != null) {
                userInfo.setNickname(fetchedUserInfo.getNickname());
                userInfo.setHeadimgurl(fetchedUserInfo.getHeadimgurl());
                if (fetchedUserInfo.getUnionid() != null && !fetchedUserInfo.getUnionid().trim().isEmpty()) {
                    userInfo.setUnionid(fetchedUserInfo.getUnionid());
                }
            } else {
                userInfo.setNickname(generateNickname());
                userInfo.setHeadimgurl(generateDefaultAvatarUrl());
            }
            
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
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String responseBody = response.getBody();
            
            LogUtil.logDebug(logger, "微信 OAuth 响应: {}", responseBody);
            
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
     * 使用 access_token + openid 拉取微信用户资料
     */
    private WechatUserInfo getWechatUserInfo(String accessToken, String openId) {
        try {
            if (accessToken == null || accessToken.trim().isEmpty() || openId == null || openId.trim().isEmpty()) {
                return null;
            }

            String url = String.format("%s?access_token=%s&openid=%s",
                    wechatConfig.getUserInfoUrl(),
                    accessToken,
                    openId);

            LogUtil.logDebug(logger, "请求微信用户信息 URL: {}", url);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String responseBody = response.getBody();

            LogUtil.logDebug(logger, "微信用户信息响应: {}", responseBody);

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            if (jsonNode.has("errcode") && jsonNode.get("errcode").asInt() != 0) {
                String errorMsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "未知错误";
                LogUtil.logWarn(logger, "获取微信用户信息失败，将回退默认资料: {}", errorMsg);
                return null;
            }

            WechatUserInfo userInfo = new WechatUserInfo();
            userInfo.setOpenId(jsonNode.has("openid") ? jsonNode.get("openid").asText() : openId);
            userInfo.setNickname(jsonNode.has("nickname") ? jsonNode.get("nickname").asText() : generateNickname());
            userInfo.setHeadimgurl(jsonNode.has("headimgurl") ? jsonNode.get("headimgurl").asText() : generateDefaultAvatarUrl());
            if (jsonNode.has("unionid")) {
                userInfo.setUnionid(jsonNode.get("unionid").asText());
            }
            return userInfo;
        } catch (Exception e) {
            LogUtil.logWarn(logger, "获取微信用户信息异常，将回退默认资料", e);
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
