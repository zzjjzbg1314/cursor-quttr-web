package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.ChatMessageRequest;
import com.example.cursorquitterweb.service.ChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;

/**
 * 聊天服务实现类 - 仅MQTT广播，不存储数据
 */
@Service
public class ChatServiceImpl implements ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    
    @Autowired
    private MqttClient mqttClient;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // MQTT主题前缀 - 从配置文件读取
    @Value("${mqtt.topic.prefix:CHAT_OFFICIAL_GROUP}")
    private String mqttTopicPrefix;
    
    @Override
    public boolean sendMessage(ChatMessageRequest request, String ipAddress) {
        try {
            // 通过MQTT广播消息
            broadcastMessage(request, ipAddress);
            logger.info("聊天消息已通过MQTT广播: {}", request.getContent());
            return true;
            
        } catch (Exception e) {
            logger.error("发送聊天消息失败", e);
            return false;
        }
    }
    
    /**
     * 通过MQTT广播消息
     */
    private void broadcastMessage(ChatMessageRequest request, String ipAddress) {
        try {
            // 使用配置的主题
            String topic = mqttTopicPrefix;
            
            // 创建简化的消息内容，只包含必要信息
            ChatMessageDto messageDto = new ChatMessageDto();
            messageDto.setNickName(request.getNickName());
            messageDto.setUserStage(request.getUserStage());
            messageDto.setContent(request.getContent());
            messageDto.setMsgType(request.getMsgType());
            messageDto.setTimestamp(LocalDateTime.now());
            
            // 转换为JSON
            String payload = objectMapper.writeValueAsString(messageDto);
            
            // 创建MQTT消息
            MqttMessage mqttMessage = new MqttMessage(payload.getBytes());
            mqttMessage.setQos(1); // 至少一次传递
            
            // 发布消息
            mqttClient.publish(topic, mqttMessage);
            
            logger.info("消息已通过MQTT广播到主题: {}, 内容: {}", topic, request.getContent());
            
        } catch (JsonProcessingException e) {
            logger.error("序列化聊天消息失败", e);
            throw new RuntimeException("序列化聊天消息失败", e);
        } catch (MqttException e) {
            logger.error("MQTT广播消息失败", e);
            throw new RuntimeException("MQTT广播消息失败", e);
        }
    }
    
    /**
     * 聊天消息DTO，用于MQTT传输（不存储到数据库）
     */
    public static class ChatMessageDto {
        private String nickName;
        private String userStage;
        private String content;
        private String msgType;
        private LocalDateTime timestamp;
        
        // Getter和Setter方法
        public String getNickName() {
            return nickName;
        }
        
        public void setNickName(String nickName) {
            this.nickName = nickName;
        }
        
        public String getUserStage() {
            return userStage;
        }
        
        public void setUserStage(String userStage) {
            this.userStage = userStage;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public String getMsgType() {
            return msgType;
        }
        
        public void setMsgType(String msgType) {
            this.msgType = msgType;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }
}
