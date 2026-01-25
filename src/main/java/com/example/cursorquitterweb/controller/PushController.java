package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.service.PushService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 推送控制器
 * 提供推送相关的 API 接口
 */
@RestController
@RequestMapping("/api/push")
public class PushController {
    
    private static final Logger logger = LogUtil.getLogger(PushController.class);
    
    @Autowired
    private PushService pushService;
    
    /**
     * 安排定时推送
     * POST /api/push/schedule
     */
    @PostMapping("/schedule")
    public ApiResponse<Void> schedulePush(@Valid @RequestBody SchedulePushRequest request) {
        logger.info("收到定时推送请求 - deviceToken: {}, title: {}, notificationId: {}, platform: {}", 
                   request.getDeviceToken(), request.getTitle(), request.getNotificationId(), request.getPlatform());
        
        try {
            boolean success = pushService.schedulePush(
                request.getDeviceToken(),
                request.getTitle(),
                request.getContent(),
                request.getTriggerTime(),
                request.getNotificationId(),
                request.getPlatform()
            );
            
            if (success) {
                return ApiResponse.success("定时推送已安排", null);
            } else {
                return ApiResponse.error("定时推送安排失败");
            }
        } catch (IllegalArgumentException e) {
            logger.error("参数错误", e);
            return ApiResponse.error(e.getMessage());
        } catch (IllegalStateException e) {
            logger.error("配置错误", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("安排定时推送失败", e);
            return ApiResponse.error("安排定时推送失败: " + e.getMessage());
        }
    }
    
    /**
     * 取消推送
     * POST /api/push/cancel
     */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancelPush(@Valid @RequestBody CancelPushRequest request) {
        logger.info("收到取消推送请求 - notificationId: {}", request.getNotificationId());
        
        try {
            boolean success = pushService.cancelPush(request.getNotificationId());
            
            if (success) {
                return ApiResponse.success("推送已取消", null);
            } else {
                return ApiResponse.error("推送取消失败");
            }
        } catch (Exception e) {
            logger.error("取消推送失败", e);
            return ApiResponse.error("取消推送失败: " + e.getMessage());
        }
    }
    
    /**
     * 安排定时推送请求 DTO
     */
    public static class SchedulePushRequest {
        private String deviceToken;
        private String title;
        private String content;
        private String triggerTime;
        private Integer notificationId;
        private String platform;
        
        public String getDeviceToken() {
            return deviceToken;
        }
        
        public void setDeviceToken(String deviceToken) {
            this.deviceToken = deviceToken;
        }
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public String getTriggerTime() {
            return triggerTime;
        }
        
        public void setTriggerTime(String triggerTime) {
            this.triggerTime = triggerTime;
        }
        
        public Integer getNotificationId() {
            return notificationId;
        }
        
        public void setNotificationId(Integer notificationId) {
            this.notificationId = notificationId;
        }
        
        public String getPlatform() {
            return platform;
        }
        
        public void setPlatform(String platform) {
            this.platform = platform;
        }
    }
    
    /**
     * 取消推送请求 DTO
     */
    public static class CancelPushRequest {
        private Integer notificationId;
        
        public Integer getNotificationId() {
            return notificationId;
        }
        
        public void setNotificationId(Integer notificationId) {
            this.notificationId = notificationId;
        }
    }
}
