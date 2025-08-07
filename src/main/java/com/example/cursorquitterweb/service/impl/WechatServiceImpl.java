package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.config.WechatConfig;
import com.example.cursorquitterweb.dto.WechatUserInfo;
import com.example.cursorquitterweb.service.WechatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信服务实现类
 */
@Service
public class WechatServiceImpl implements WechatService {
    
    private static final Logger logger = LoggerFactory.getLogger(WechatServiceImpl.class);
    
    @Autowired
    private WechatConfig wechatConfig;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public WechatUserInfo login(String code) {
        try {
            logger.info("开始微信登录，授权码: {}", code);
            
            // 1. 使用授权码获取openid和session_key
            Map<String, String> sessionInfo = getSessionInfo(code);
            if (sessionInfo == null) {
                throw new RuntimeException("获取微信session失败");
            }
            
            String openId = sessionInfo.get("openid");
            String unionid = sessionInfo.get("unionid");
            
            logger.info("获取到openid: {}, unionid: {}", openId, unionid);
            
            // 2. 创建用户信息（微信小程序中，用户信息需要前端传递）
            WechatUserInfo userInfo = new WechatUserInfo();
            userInfo.setOpenId(openId);
            userInfo.setUnionid(unionid);
            userInfo.setNickname("微信用户");
            userInfo.setHeadimgurl("");
            
            logger.info("微信登录成功，用户信息: {}", userInfo);
            return userInfo;
            
        } catch (Exception e) {
            logger.error("微信登录失败", e);
            throw new RuntimeException("微信登录失败: " + e.getMessage());
        }
    }
    
    /**
     * 使用授权码获取session信息
     */
    private Map<String, String> getSessionInfo(String code) {
        try {
            String url = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    wechatConfig.getCode2SessionUrl(),
                    wechatConfig.getAppId(),
                    wechatConfig.getAppSecret(),
                    code);
            
            logger.debug("请求微信session URL: {}", url);
            
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String responseBody = response.getBody();
            
            logger.debug("微信session响应: {}", responseBody);
            
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            if (jsonNode.has("errcode") && jsonNode.get("errcode").asInt() != 0) {
                String errorMsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "未知错误";
                logger.error("微信session获取失败: {}", errorMsg);
                return null;
            }
            
            Map<String, String> sessionInfo = new HashMap<>();
            sessionInfo.put("openid", jsonNode.get("openid").asText());
            sessionInfo.put("session_key", jsonNode.get("session_key").asText());
            
            // unionid可能不存在
            if (jsonNode.has("unionid")) {
                sessionInfo.put("unionid", jsonNode.get("unionid").asText());
            }
            
            return sessionInfo;
            
        } catch (Exception e) {
            logger.error("获取微信session失败", e);
            return null;
        }
    }
} 