package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.WechatLoginRequest;
import com.example.cursorquitterweb.dto.WechatLoginResponse;
import com.example.cursorquitterweb.dto.WechatUserInfo;
import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.entity.UserIdentity;
import com.example.cursorquitterweb.service.UserIdentityService;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.service.WechatService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * 微信登录控制器
 */
@RestController
@RequestMapping("/api/wechat")
public class WechatController {
    
    private static final Logger logger = LogUtil.getLogger(WechatController.class);
    
    @Autowired
    private WechatService wechatService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private UserIdentityService userIdentityService;
    
    /**
     * 微信登录接口
     * @param request 登录请求
     * @return 用户信息
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<WechatLoginResponse>> login(@RequestBody WechatLoginRequest request) {
        LogUtil.logInfo(logger, "收到微信登录请求，授权码: {}", request.getCode());
        
        try {
            if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                LogUtil.logWarn(logger, "微信登录失败：授权码为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("授权码不能为空"));
            }
            
            WechatUserInfo wechatUserInfo = wechatService.login(request.getCode());
            
            // 使用微信 openid 作为唯一身份标识，避免按昵称匹配导致串号
            UUID existingUserId = userIdentityService.findUserIdByIdentity(
                    UserIdentity.IdentityType.WECHAT,
                    wechatUserInfo.getOpenId()
            );

            User user;
            boolean isNewUser = false;
            if (existingUserId != null) {
                Optional<User> existingUser = userService.findById(existingUserId);
                if (!existingUser.isPresent()) {
                    LogUtil.logWarn(logger, "微信身份已存在但用户不存在，userId: {}", existingUserId);
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("微信登录失败：用户数据异常"));
                }

                // 用户已存在，直接使用现有用户信息
                user = existingUser.get();
                LogUtil.logInfo(logger, "用户已存在，用户ID: {}", user.getId());
            } else {
                // 创建新用户
                isNewUser = true;
                user = userService.createUser(
                    wechatUserInfo.getNickname(),
                    wechatUserInfo.getHeadimgurl()
                );

                String identityData = wechatUserInfo.getUnionid() != null && !wechatUserInfo.getUnionid().trim().isEmpty()
                        ? "{\"unionid\":\"" + wechatUserInfo.getUnionid() + "\"}"
                        : null;

                userIdentityService.createIdentity(
                        user.getId(),
                        UserIdentity.IdentityType.WECHAT,
                        wechatUserInfo.getOpenId(),
                        identityData
                );

                LogUtil.logInfo(logger, "新用户创建成功，用户ID: {}", user.getId());
            }

            WechatLoginResponse response = new WechatLoginResponse(user, isNewUser);
            LogUtil.logInfo(logger, "微信登录成功，用户ID: {}, openId: {}, isNewUser: {}", user.getId(), wechatUserInfo.getOpenId(), isNewUser);
            
            return ResponseEntity.ok(ApiResponse.success("微信登录成功", response));
            
        } catch (Exception e) {
            LogUtil.logError(logger, "微信登录失败", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("微信登录失败: " + e.getMessage()));
        }
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        LogUtil.logDebug(logger, "微信服务健康检查");
        return ResponseEntity.ok(ApiResponse.success("微信服务正常"));
    }
} 
