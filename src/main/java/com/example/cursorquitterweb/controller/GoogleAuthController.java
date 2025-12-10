package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.GoogleLoginRequest;
import com.example.cursorquitterweb.dto.GoogleLoginResponse;
import com.example.cursorquitterweb.service.GoogleAuthService;
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
 * Google 登录认证控制器
 */
@RestController
@RequestMapping("/api/auth/google")
public class GoogleAuthController {
    
    private static final Logger logger = LogUtil.getLogger(GoogleAuthController.class);
    
    @Autowired
    private GoogleAuthService googleAuthService;
    
    /**
     * Google 登录接口
     * POST /api/auth/google/login
     * 
     * @param request Google 登录请求
     * @return 用户信息
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody GoogleLoginRequest request) {
        
        logger.info("收到 Google 登录请求，google_user_id: {}", request.getGoogleUserId());
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 处理 Google 登录
            GoogleLoginResponse loginResponse = googleAuthService.login(request);
            
            response.put("success", true);
            response.put("message", "登录成功");
            response.put("data", loginResponse);
            
            logger.info("Google 登录成功，user_id: {}, is_new_user: {}", 
                       loginResponse.getId(), loginResponse.getIsNewUser());
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Google 登录失败", e);
            
            response.put("success", false);
            response.put("message", "登录失败: " + e.getMessage());
            
            // 根据异常类型返回不同的状态码
            if (e.getMessage().contains("验证失败") || e.getMessage().contains("不匹配")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            logger.error("Google 登录异常", e);
            
            response.put("success", false);
            response.put("message", "登录异常，请稍后重试");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 验证 Google ID Token（调试用）
     * POST /api/auth/google/verify
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyToken(@RequestBody Map<String, String> request) {
        
        logger.info("收到 Token 验证请求");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String idToken = request.get("id_token");
            
            if (idToken == null || idToken.isEmpty()) {
                response.put("success", false);
                response.put("message", "id_token 不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 验证 token
            String googleUserId = googleAuthService.verifyIdToken(idToken);
            
            if (googleUserId != null) {
                response.put("success", true);
                response.put("message", "Token 验证成功");
                response.put("google_user_id", googleUserId);
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
     * GET /api/auth/google/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Google Auth Service is running");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}

