package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.AppleLoginRequest;
import com.example.cursorquitterweb.dto.AppleLoginResponse;
import com.example.cursorquitterweb.service.AppleAuthService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * Apple 登录认证控制器
 */
@RestController
@RequestMapping("/api/auth/apple")
public class AppleAuthController {
    
    private static final Logger logger = LogUtil.getLogger(AppleAuthController.class);
    
    @Autowired
    private AppleAuthService appleAuthService;
    
    /**
     * Apple 登录接口
     * POST /api/auth/apple/login
     * 
     * @param request Apple 登录请求
     * @return 用户信息
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody AppleLoginRequest request) {
        
        logger.info("收到 Apple 登录请求，apple_user_id: {}", request.getAppleUserId());
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 处理 Apple 登录
            AppleLoginResponse loginResponse = appleAuthService.login(request);
            
            response.put("success", true);
            response.put("message", "登录成功");
            response.put("data", loginResponse);
            
            logger.info("Apple 登录成功，user_id: {}, is_new_user: {}", 
                       loginResponse.getId(), loginResponse.getIsNewUser());
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Apple 登录失败", e);
            
            response.put("success", false);
            response.put("message", "登录失败: " + e.getMessage());
            
            // 根据异常类型返回不同的状态码
            if (e.getMessage().contains("验证失败") || e.getMessage().contains("不匹配")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            logger.error("Apple 登录异常", e);
            
            response.put("success", false);
            response.put("message", "登录异常，请稍后重试");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 验证 Apple Identity Token（调试用）
     * POST /api/auth/apple/verify
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyToken(@RequestBody Map<String, String> request) {
        
        logger.info("收到 Token 验证请求");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String identityToken = request.get("identity_token");
            
            if (identityToken == null || identityToken.isEmpty()) {
                response.put("success", false);
                response.put("message", "identity_token 不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 验证 token
            String appleUserId = appleAuthService.verifyIdentityToken(identityToken);
            
            if (appleUserId != null) {
                response.put("success", true);
                response.put("message", "Token 验证成功");
                response.put("apple_user_id", appleUserId);
            } else {
                response.put("success", false);
                response.put("message", "Token 验证失败");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Token 验证异常", e);
            
            response.put("success", false);
            response.put("message", "验证异常: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 健康检查接口
     * GET /api/auth/apple/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Apple Auth Service is running");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}

