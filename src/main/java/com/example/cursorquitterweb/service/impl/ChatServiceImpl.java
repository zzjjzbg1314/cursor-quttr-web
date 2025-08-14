package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.ChatMessageRequest;
import com.example.cursorquitterweb.service.ChatService;
import com.example.cursorquitterweb.entity.elasticsearch.ChatMessageDocument;
import com.example.cursorquitterweb.service.ChatMessageSearchService;
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
 * 聊天服务实现类 - 完全按照阿里云MQ4IoT官方示例实现
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private MqttClient mqttClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatMessageSearchService chatMessageSearchService;

    // MQTT主题配置 - 从配置文件读取
    @Value("${mqtt.parent.topic:CHAT_OFFICIAL_GROUP}")
    private String parentTopic;

    @Value("${mqtt.qos.level:0}")
    private int qosLevel;

    @Value("${mqtt.group.id:GID_QUITTR}")
    private String groupId;

    @Value("${mqtt.device.id:${random.uuid}}")
    private String deviceId;

    @Override
    public boolean sendMessage(ChatMessageRequest request, String ipAddress) {
        System.out.println("=== 聊天消息发送开始（阿里云MQ4IoT官方方式） ===");
        System.out.println("发送时间: " + LocalDateTime.now());
        System.out.println("发送者IP: " + ipAddress);
        System.out.println("消息内容: " + request.getContent());
        System.out.println("发送者昵称: " + request.getNickName());
        System.out.println("用户阶段: " + request.getUserStage());
        System.out.println("消息类型: " + request.getMsgType());
        System.out.println("Parent Topic: " + parentTopic);
        System.out.println("QoS Level: " + qosLevel);
        System.out.println("Group ID: " + groupId);
        System.out.println("Device ID: " + deviceId);

        try {
            // 检查MQTT客户端状态
            if (mqttClient == null) {
                System.out.println("错误: MQTT客户端为空");
                return false;
            }

            System.out.println("MQTT客户端状态检查:");
            System.out.println("  客户端ID: " + mqttClient.getClientId());
            System.out.println("  服务器URI: " + mqttClient.getServerURI());
            System.out.println("  连接状态: " + (mqttClient.isConnected() ? "已连接" : "未连接"));

            if (!mqttClient.isConnected()) {
                System.out.println("警告: MQTT客户端未连接，无法发送消息");
                return false;
            }

            // 通过MQTT广播消息
            broadcastMessage(request, ipAddress);
            System.out.println("聊天消息已通过MQTT广播: " + request.getContent());
            System.out.println("=== 聊天消息发送成功 ===");
            return true;

        } catch (Exception e) {
            System.out.println("=== 聊天消息发送失败 ===");
            System.out.println("错误类型: " + e.getClass().getSimpleName());
            System.out.println("错误消息: " + e.getMessage());
            System.out.println("堆栈跟踪:");
            e.printStackTrace();
            logger.error("发送聊天消息失败", e);
            return false;
        }
    }

    /**
     * 通过MQTT广播消息
     * 完全按照阿里云MQ4IoT官方示例实现
     */
    private void broadcastMessage(ChatMessageRequest request, String ipAddress) {
        System.out.println("=== MQTT消息广播开始（阿里云MQ4IoT官方方式） ===");
        System.out.println("广播时间: " + LocalDateTime.now());

        try {
            // 构造完整的topic：parentTopic/子主题
//            String topic = parentTopic + "/chat";
            String topic = parentTopic;
            System.out.println("MQTT主题: " + topic);
            System.out.println("Parent Topic: " + parentTopic);

            // 创建简化的消息内容，只包含必要信息
            System.out.println("正在创建消息DTO...");
            ChatMessageDto messageDto = new ChatMessageDto();
            messageDto.setNickName(request.getNickName());
            messageDto.setUserStage(request.getUserStage());
            messageDto.setContent(request.getContent());
            messageDto.setMsgType(request.getMsgType());
            messageDto.setTimestamp(LocalDateTime.now());
            messageDto.setIpAddress(ipAddress);

            System.out.println("消息DTO创建完成:");
            System.out.println("  昵称: " + messageDto.getNickName());
            System.out.println("  用户阶段: " + messageDto.getUserStage());
            System.out.println("  内容: " + messageDto.getContent());
            System.out.println("  消息类型: " + messageDto.getMsgType());
            System.out.println("  时间戳: " + messageDto.getTimestamp());
            System.out.println("  IP地址: " + messageDto.getIpAddress());

            // 转换为JSON
            System.out.println("正在序列化消息为JSON...");
            String payload = objectMapper.writeValueAsString(messageDto);
            System.out.println("JSON序列化完成，长度: " + payload.length() + " 字符");
            System.out.println("JSON内容: " + payload);

            // 创建MQTT消息
            System.out.println("正在创建MQTT消息...");
            MqttMessage mqttMessage = new MqttMessage(payload.getBytes());
            mqttMessage.setQos(qosLevel); // 使用配置的QoS级别
            System.out.println("MQTT消息创建完成，QoS: " + mqttMessage.getQos());
            System.out.println("消息大小: " + mqttMessage.getPayload().length + " 字节");

            // 发布消息
            System.out.println("正在发布MQTT消息到主题: " + topic);
            long publishStartTime = System.currentTimeMillis();

            mqttClient.publish(topic, mqttMessage);

            long publishEndTime = System.currentTimeMillis();
            System.out.println("MQTT消息发布成功! 耗时: " + (publishEndTime - publishStartTime) + " ms");
            System.out.println("消息已通过MQTT广播到主题: " + topic);
            System.out.println("消息内容: " + request.getContent());

            // 发送点对点消息示例（可选）
//            sendP2PMessage(request, ipAddress);

            logger.info("消息已通过MQTT广播到主题: {}, 内容: {}", topic, request.getContent());
            System.out.println("=== MQTT消息广播完成 ===");

        } catch (JsonProcessingException e) {
            System.out.println("=== JSON序列化失败 ===");
            System.out.println("序列化错误: " + e.getMessage());
            System.out.println("堆栈跟踪:");
            e.printStackTrace();
            logger.error("序列化聊天消息失败", e);
            throw new RuntimeException("序列化聊天消息失败", e);
        } catch (MqttException e) {
            System.out.println("=== MQTT广播消息失败 ===");
            System.out.println("MQTT错误代码: " + e.getReasonCode());
            System.out.println("MQTT错误消息: " + e.getMessage());
            System.out.println("MQTT错误原因: " + e.getCause());
            System.out.println("堆栈跟踪:");
            e.printStackTrace();
            logger.error("MQTT广播消息失败", e);
            throw new RuntimeException("MQTT广播消息失败", e);
        } catch (Exception e) {
            System.out.println("=== MQTT广播过程中发生未知错误 ===");
            System.out.println("错误类型: " + e.getClass().getSimpleName());
            System.out.println("错误消息: " + e.getMessage());
            System.out.println("堆栈跟踪:");
            e.printStackTrace();
            throw new RuntimeException("MQTT广播过程中发生未知错误", e);
        }
    }

    /**
     * 发送点对点消息
     * 按照阿里云MQ4IoT官方示例实现
     */
    private void sendP2PMessage(ChatMessageRequest request, String ipAddress) {
        try {
            // 点对点消息的topic格式：parentTopic/p2p/targetClientId
            String targetClientId = groupId + "@@@" + deviceId;
            String p2pTopic = parentTopic + "/p2p/" + targetClientId;

            System.out.println("正在发送点对点消息...");
            System.out.println("点对点主题: " + p2pTopic);
            System.out.println("目标客户端ID: " + targetClientId);

            // 创建点对点消息内容
            ChatMessageDto p2pMessage = new ChatMessageDto();
            p2pMessage.setNickName(request.getNickName());
            p2pMessage.setUserStage(request.getUserStage());
            p2pMessage.setContent("P2P: " + request.getContent());
            p2pMessage.setMsgType("p2p");
            p2pMessage.setTimestamp(LocalDateTime.now());
            p2pMessage.setIpAddress(ipAddress);

            String p2pPayload = objectMapper.writeValueAsString(p2pMessage);
            MqttMessage p2pMqttMessage = new MqttMessage(p2pPayload.getBytes());
            p2pMqttMessage.setQos(qosLevel);

            mqttClient.publish(p2pTopic, p2pMqttMessage);
            System.out.println("点对点消息发送成功");

        } catch (Exception e) {
            System.out.println("点对点消息发送失败: " + e.getMessage());
            // 点对点消息失败不影响主要功能
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
        private String ipAddress;

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

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }
    }

    @Override
    public boolean sendChatMessage(ChatMessageRequest request) {
        System.out.println("=== 聊天消息发送到Elasticsearch索引开始 ===");
        System.out.println("发送时间: " + LocalDateTime.now());
        System.out.println("消息内容: " + request.getContent());
        System.out.println("发送者昵称: " + request.getNickName());
        System.out.println("用户阶段: " + request.getUserStage());
        System.out.println("消息类型: " + request.getMsgType());

        try {
            // 将ChatMessageRequest转换为ChatMessageDocument
            ChatMessageDocument document = new ChatMessageDocument(
                    request.getNickName(),
                    request.getUserStage(),
                    request.getContent(),
                    request.getMsgType()
            );

            System.out.println("正在创建Elasticsearch文档...");
            System.out.println("文档内容:");
            System.out.println("  昵称: " + document.getNickName());
            System.out.println("  用户阶段: " + document.getUserStage());
            System.out.println("  内容: " + document.getContent());
            System.out.println("  消息类型: " + document.getMsgType());
            System.out.println("  创建时间: " + document.getCreateAt());

            // 存储到Elasticsearch索引
            System.out.println("正在存储到Elasticsearch索引...");
            return chatMessageSearchService.saveMessage(document);
        } catch (Exception e) {
            System.out.println("=== 聊天消息发送到Elasticsearch索引失败 ===");
            System.out.println("错误类型: " + e.getClass().getSimpleName());
            System.out.println("错误消息: " + e.getMessage());
            System.out.println("堆栈跟踪:");
            e.printStackTrace();
            logger.error("发送聊天消息到Elasticsearch索引失败", e);
            return false;
        }
    }
}
