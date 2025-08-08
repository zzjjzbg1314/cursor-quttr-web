package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 首页控制器
 */
@RestController
public class HomeController {

    /**
     * 根路径处理器
     */
    @GetMapping("/")
    public ApiResponse<Map<String, Object>> home() {
        Map<String, Object> appInfo = new HashMap<>();
        appInfo.put("name", "Cursor Quitter Web");
        appInfo.put("version", "1.0.0");
        appInfo.put("description", "基于Spring Boot 2.7.18和Java 8的Web应用程序");
        appInfo.put("features", new String[]{
            "微信登录功能",
            "完整的日志中心",
            "HTTPS安全访问",
            "SSL证书管理"
        });
        appInfo.put("endpoints", new String[]{
            "/api/hello - 欢迎信息",
            "/api/health - 健康检查",
            "/api/wechat/login - 微信登录",
            "/api/wechat/health - 微信服务健康检查",
            "/api/ssl/info - SSL证书信息",
            "/api/ssl/status - SSL状态",
            "/api/logs/level - 日志级别",
            "/api/logs/files - 日志文件信息"
        });
        
        return ApiResponse.success(appInfo);
    }
}
