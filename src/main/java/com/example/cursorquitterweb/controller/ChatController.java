package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.ChatMessageRequest;
import com.example.cursorquitterweb.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 聊天控制器
 */
@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    @Autowired
    private ChatService chatService;
    
    /**
     * 发送聊天消息
     * @param request 聊天消息请求
     * @param httpRequest HTTP请求对象
     * @return API响应
     */
    @PostMapping("/sendMsg")
    public ApiResponse<String> sendMessage(@Valid @RequestBody ChatMessageRequest request, 
                                         HttpServletRequest httpRequest) {
        try {
            // 获取客户端IP地址
            String ipAddress = getClientIpAddress(httpRequest);
            logger.info("收到聊天消息请求: 用户={}, 阶段={}, 类型={}, 内容={}, IP={}", 
                       request.getNickName(), request.getUserStage(), 
                       request.getMsgType(), request.getContent(), ipAddress);
            
            // 发送消息
            boolean success = chatService.sendMessage(request, ipAddress);
            
            if (success) {
                return ApiResponse.success("消息发送成功");
            } else {
                return ApiResponse.error("消息发送失败");
            }
            
        } catch (Exception e) {
            logger.error("发送聊天消息时发生错误", e);
            return ApiResponse.error("发送消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * 获取聊天功能信息
     */
    @GetMapping("/info")
    public ApiResponse<Object> getChatInfo() {
        return ApiResponse.success("群组聊天功能已启用，支持实时消息广播");
    }
}
