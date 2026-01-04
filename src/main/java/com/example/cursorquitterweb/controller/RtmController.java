package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.service.AgoraRtmTokenService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 声网 RTM Token 控制器
 */
@RestController
@RequestMapping("/api/rtm")
public class RtmController {
    
    private static final Logger logger = LogUtil.getLogger(RtmController.class);
    
    @Autowired
    private AgoraRtmTokenService agoraRtmTokenService;
    
    /**
     * 获取 RTM Token
     * 
     * @param userId 用户 ID
     * @return RTM Token 响应
     */
    @GetMapping("/token")
    public ApiResponse<Map<String, String>> getToken(@RequestParam String userId) {
        logger.info("获取 RTM Token，userId: {}", userId);
        
        try {
            if (userId == null || userId.trim().isEmpty()) {
                return ApiResponse.error("userId 参数不能为空");
            }
            
            String token = agoraRtmTokenService.generateToken(userId);
            
            Map<String, String> data = new HashMap<>();
            data.put("token", token);
            
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            logger.error("获取 RTM Token 失败，参数错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (IllegalStateException e) {
            logger.error("获取 RTM Token 失败，配置错误: {}", e.getMessage());
            return ApiResponse.error("声网配置未正确设置");
        } catch (Exception e) {
            logger.error("获取 RTM Token 失败，userId: {}", userId, e);
            return ApiResponse.error("生成 Token 失败: " + e.getMessage());
        }
    }
}

