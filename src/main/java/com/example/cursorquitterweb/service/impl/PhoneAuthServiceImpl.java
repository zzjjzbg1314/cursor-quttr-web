package com.example.cursorquitterweb.service.impl;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.teautil.models.RuntimeOptions;
import com.aliyun.tea.TeaException;
import com.example.cursorquitterweb.config.AliyunDypnsapiConfig;
import com.example.cursorquitterweb.dto.OneClickLoginResponse;
import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.service.PhoneAuthService;
import com.example.cursorquitterweb.service.RecoverJourneyService;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.util.LogUtil;
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
    private Client dypnsapiClient;
    
    @Autowired
    private AliyunDypnsapiConfig dypnsapiConfig;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private RecoverJourneyService recoverJourneyService;
    
    @Override
    @Transactional
    public OneClickLoginResponse oneClickLogin(String accessToken) throws Exception {
        logger.info("开始一键登录流程（号码认证），accessToken: {}", maskToken(accessToken));
        
        // 1. 调用阿里云号码认证 API 换取手机号
        String phoneNumber = getPhoneNumberFromAliyun(accessToken);
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
     * 调用阿里云 GetMobile API 获取手机号（号码认证）
     * 参考文档：https://next.api.aliyun.com/api/Dypnsapi/2017-05-25/GetMobile
     * 使用 Tea OpenAPI SDK
     */
    private String getPhoneNumberFromAliyun(String accessToken) throws Exception {
        com.aliyun.dypnsapi20170525.models.GetMobileRequest request = new com.aliyun.dypnsapi20170525.models.GetMobileRequest();
        request.setAccessToken(accessToken);
        
        RuntimeOptions runtime = new RuntimeOptions();
        
        try {
            com.aliyun.dypnsapi20170525.models.GetMobileResponse response = dypnsapiClient.getMobileWithOptions(request, runtime);
            
            // 检查响应
            if (response == null) {
                throw new Exception("阿里云 API 返回空响应");
            }

            com.aliyun.dypnsapi20170525.models.GetMobileResponseBody body = response.getBody();
            if (body == null) {
                throw new Exception("阿里云 API 响应体为空");
            }
            

            // 检查状态码
            if (body.getCode() == null || !"OK".equals(body.getCode())) {
                String errorCode = body.getCode() != null ? body.getCode() : "UNKNOWN";
                String errorMessage = body.getMessage() != null ? body.getMessage() : "未知错误";
                throw new Exception("阿里云 API 返回错误，错误码: " + errorCode + ", 错误信息: " + errorMessage);
            }
            
            // 获取 GetMobileResultDTO 对象
            com.aliyun.dypnsapi20170525.models.GetMobileResponseBody.GetMobileResponseBodyGetMobileResultDTO resultDTO = body.getGetMobileResultDTO();
            if (resultDTO == null) {
                throw new Exception("响应中未找到 GetMobileResultDTO 数据");
            }
            
            // 获取手机号
            String phoneNumber = resultDTO.getMobile();
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                throw new Exception("响应中未找到手机号信息");
            }
            
            logger.debug("阿里云号码认证 API 调用成功，手机号: {}", maskPhoneNumber(phoneNumber));
            
            return phoneNumber;
            
        } catch (TeaException e) {
            logger.error("调用阿里云号码认证 API 异常: {}", e.getMessage(), e);
            String errorMsg = e.getMessage();
            if (e.getData() != null && e.getData().containsKey("Recommend")) {
                errorMsg += ", 诊断地址: " + e.getData().get("Recommend");
            }
            throw new Exception("调用阿里云 API 失败: " + errorMsg, e);
        } catch (Exception e) {
            logger.error("调用阿里云号码认证 API 异常: {}", e.getMessage(), e);
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

