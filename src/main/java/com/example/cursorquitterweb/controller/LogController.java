package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志管理控制器
 */
@RestController
@RequestMapping("/api/logs")
public class LogController {
    
    private static final Logger logger = LogUtil.getLogger(LogController.class);
    
    @Autowired
    private LoggingSystem loggingSystem;
    
    /**
     * 获取当前日志级别
     */
    @GetMapping("/level")
    public ApiResponse<Map<String, String>> getLogLevel() {
        LogUtil.logDebug(logger, "获取当前日志级别");
        
        Map<String, String> levels = new HashMap<>();
        levels.put("root", loggingSystem.getLoggerConfiguration("ROOT").getEffectiveLevel().name());
        levels.put("com.example.cursorquitterweb", 
            loggingSystem.getLoggerConfiguration("com.example.cursorquitterweb").getEffectiveLevel().name());
        levels.put("org.springframework.web", 
            loggingSystem.getLoggerConfiguration("org.springframework.web").getEffectiveLevel().name());
        
        return ApiResponse.success(levels);
    }
    
    /**
     * 设置日志级别
     */
    @PostMapping("/level")
    public ApiResponse<String> setLogLevel(@RequestParam String loggerName, @RequestParam String level) {
        try {
            LogLevel logLevel = LogLevel.valueOf(level.toUpperCase());
            loggingSystem.setLogLevel(loggerName, logLevel);
            
            LogUtil.logInfo(logger, "设置日志级别成功: {} -> {}", loggerName, level);
            return ApiResponse.success("设置日志级别成功");
            
        } catch (IllegalArgumentException e) {
            LogUtil.logError(logger, "设置日志级别失败: 无效的日志级别 {}", level);
            return ApiResponse.error("无效的日志级别: " + level);
        } catch (Exception e) {
            LogUtil.logError(logger, "设置日志级别失败", e);
            return ApiResponse.error("设置日志级别失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取日志文件信息
     */
    @GetMapping("/files")
    public ApiResponse<Map<String, Object>> getLogFiles() {
        LogUtil.logDebug(logger, "获取日志文件信息");
        
        Map<String, Object> logFiles = new HashMap<>();
        logFiles.put("logHome", "logs/");
        logFiles.put("files", new String[]{
            "system.log",
            "error.log", 
            "wechat.log",
            "sql.log"
        });
        
        return ApiResponse.success(logFiles);
    }
} 