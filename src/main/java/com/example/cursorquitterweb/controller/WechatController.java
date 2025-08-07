package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.WechatLoginRequest;
import com.example.cursorquitterweb.dto.WechatUserInfo;
import com.example.cursorquitterweb.service.WechatService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 微信登录控制器
 */
@RestController
@RequestMapping("/api/wechat")
public class WechatController {
    
    private static final Logger logger = LogUtil.getLogger(WechatController.class);
    
    @Autowired
    private WechatService wechatService;
    
    /**
     * 微信登录接口
     * @param request 登录请求
     * @return 用户信息
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<WechatUserInfo>> login(@RequestBody WechatLoginRequest request) {
        LogUtil.logInfo(logger, "收到微信登录请求，授权码: {}", request.getCode());
        
        try {
            if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                LogUtil.logWarn(logger, "微信登录失败：授权码为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("授权码不能为空"));
            }
            
            WechatUserInfo userInfo = wechatService.login(request.getCode());
            
            LogUtil.logInfo(logger, "微信登录成功，用户信息: {}", userInfo);
            
            return ResponseEntity.ok(ApiResponse.success(userInfo));
            
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