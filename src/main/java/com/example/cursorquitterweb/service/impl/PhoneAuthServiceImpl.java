package com.example.cursorquitterweb.service.impl;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.example.cursorquitterweb.config.AliyunDypnsapiConfig;
import com.example.cursorquitterweb.dto.OneClickLoginResponse;
import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.service.PhoneAuthService;
import com.example.cursorquitterweb.service.RecoverJourneyService;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.util.LogUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 手机号认证服务实现类
 */
@Service
public class PhoneAuthServiceImpl implements PhoneAuthService {
    
    private static final Logger logger = LogUtil.getLogger(PhoneAuthServiceImpl.class);
    
    @Autowired
    private IAcsClient dypnsapiClient;
    
    @Autowired
    private AliyunDypnsapiConfig dypnsapiConfig;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private RecoverJourneyService recoverJourneyService;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    @Transactional
    public OneClickLoginResponse oneClickLogin(String verifyToken) throws Exception {
        logger.info("开始一键登录流程（融合认证），verifyToken: {}", maskToken(verifyToken));
        
        // 1. 调用阿里云融合认证 API 换取手机号
        String phoneNumber = getPhoneNumberFromAliyun(verifyToken);
        logger.info("成功获取手机号: {}", maskPhoneNumber(phoneNumber));
        
        // 2. 根据手机号查询用户
        Optional<User> existingUser = userService.findByPhoneNumber(phoneNumber);
        
        if (existingUser.isPresent()) {
            // 用户已存在，直接返回
            logger.info("用户已存在，手机号: {}, 用户ID: {}", maskPhoneNumber(phoneNumber), existingUser.get().getId());
            return new OneClickLoginResponse(phoneNumber, existingUser.get(), false);
        } else {
            // 用户不存在，创建新用户
            logger.info("用户不存在，创建新用户，手机号: {}", maskPhoneNumber(phoneNumber));
            User newUser = createUserWithPhoneNumber(phoneNumber);
            return new OneClickLoginResponse(phoneNumber, newUser, true);
        }
    }
    
    /**
     * 调用阿里云 VerifyWithFusionAuthToken API 获取手机号（融合认证）
     * 参考文档：https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-verifywithfusionauthtoken
     */
    private String getPhoneNumberFromAliyun(String verifyToken) throws Exception {
        CommonRequest request = new CommonRequest();
        request.setSysMethod(MethodType.POST);
        request.setSysDomain(dypnsapiConfig.getEndpoint());
        request.setSysVersion("2017-05-25");
        request.setSysAction("VerifyWithFusionAuthToken");
        request.putQueryParameter("RegionId", dypnsapiConfig.getRegionId());
        request.putQueryParameter("VerifyToken", verifyToken);
        
        try {
            CommonResponse response = dypnsapiClient.getCommonResponse(request);
            logger.debug("阿里云融合认证 API 响应: {}", response.getData());
            
            if (!response.getHttpResponse().isSuccess()) {
                throw new Exception("阿里云 API 调用失败，HTTP状态码: " + response.getHttpResponse().getStatus());
            }
            
            // 解析响应 JSON
            JsonNode jsonNode = objectMapper.readTree(response.getData());
            
            // 检查 API 调用是否成功
            if (!jsonNode.has("Success") || !jsonNode.get("Success").asBoolean()) {
                String errorCode = jsonNode.has("Code") ? jsonNode.get("Code").asText() : "UNKNOWN";
                String errorMessage = jsonNode.has("Message") ? jsonNode.get("Message").asText() : "未知错误";
                throw new Exception("阿里云 API 调用失败，错误码: " + errorCode + ", 错误信息: " + errorMessage);
            }
            
            // 检查状态码
            if (!jsonNode.has("Code") || !"OK".equals(jsonNode.get("Code").asText())) {
                String errorCode = jsonNode.has("Code") ? jsonNode.get("Code").asText() : "UNKNOWN";
                String errorMessage = jsonNode.has("Message") ? jsonNode.get("Message").asText() : "未知错误";
                throw new Exception("阿里云 API 返回错误，错误码: " + errorCode + ", 错误信息: " + errorMessage);
            }
            
            // 获取 Model 对象
            if (!jsonNode.has("Model")) {
                throw new Exception("响应中未找到 Model 数据");
            }
            
            JsonNode modelNode = jsonNode.get("Model");
            
            // 检查认证结果
            if (!modelNode.has("VerifyResult")) {
                throw new Exception("响应中未找到认证结果");
            }
            
            String verifyResult = modelNode.get("VerifyResult").asText();
            if (!"PASS".equals(verifyResult)) {
                throw new Exception("认证失败，认证结果: " + verifyResult);
            }
            
            // 获取手机号
            if (!modelNode.has("PhoneNumber")) {
                throw new Exception("响应中未找到手机号信息");
            }
            
            String phoneNumber = modelNode.get("PhoneNumber").asText();
            
            // 可选：记录手机号评分（如果存在）
            if (modelNode.has("PhoneScore")) {
                long phoneScore = modelNode.get("PhoneScore").asLong();
                logger.debug("手机号评分: {}", phoneScore);
            }
            
            return phoneNumber;
            
        } catch (ClientException e) {
            logger.error("调用阿里云融合认证 API 异常: {}", e.getMessage(), e);
            throw new Exception("调用阿里云 API 失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建带手机号的新用户
     */
    private User createUserWithPhoneNumber(String phoneNumber) {
        // 生成默认昵称
        String defaultNickname = "用户" + phoneNumber.substring(phoneNumber.length() - 4);
        
        // 生成随机头像
        int randomNumber = (int) (Math.random() * 30) + 1;
        String avatarUrl = "https://image.kejiapi.cn/images/xiaohongshu/" + randomNumber + ".jpg";
        
        // 创建用户
        User user = User.initUser();
        user.setNickname(defaultNickname);
        user.setPhoneNumber(phoneNumber);
        user.setAvatarUrl(avatarUrl);
        user.setRestartCount(0);
        
        User savedUser = userService.save(user);
        logger.info("新用户创建成功，用户ID: {}, 手机号: {}", savedUser.getId(), maskPhoneNumber(phoneNumber));
        
        // 用户数据初始化完成后，创建一条康复记录
        try {
            recoverJourneyService.createRecoverJourney(savedUser.getId(), "今天注册了克己。");
            logger.info("为用户 {} 创建初始康复记录成功", savedUser.getId());
        } catch (Exception e) {
            logger.error("为用户 {} 创建初始康复记录失败: {}", savedUser.getId(), e.getMessage());
            // 不抛出异常，避免影响用户创建流程
        }
        
        return savedUser;
    }
    
    /**
     * 掩码处理 token（用于日志）
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
    
    /**
     * 掩码处理手机号（用于日志）
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() != 11) {
            return phoneNumber;
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(7);
    }
}

