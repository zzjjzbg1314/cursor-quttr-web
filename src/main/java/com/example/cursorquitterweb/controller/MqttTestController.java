package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.util.MqttStatusMonitor;
import com.example.cursorquitterweb.util.MqttConnectionManager;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MQTT连接测试控制器
 * 提供REST API来测试和监控MQTT连接状态
 */
@RestController
@RequestMapping("/api/mqtt")
public class MqttTestController {
    
    @Autowired
    private MqttClient mqttClient;
    
    @Autowired
    private MqttStatusMonitor mqttStatusMonitor;
    
    @Autowired
    private MqttConnectionManager mqttConnectionManager;
    
    /**
     * 获取MQTT连接状态
     */
    @GetMapping("/status")
    public Map<String, Object> getMqttStatus() {
        System.out.println("=== MQTT状态查询API调用 ===");
        System.out.println("查询时间: " + LocalDateTime.now());
        
        Map<String, Object> status = new HashMap<>();
        
        try {
            if (mqttClient == null) {
                System.out.println("MQTT客户端为空");
                status.put("error", "MQTT客户端未初始化");
                status.put("connected", false);
                return status;
            }
            
            // 获取基本连接信息
            String clientId = mqttClient.getClientId();
            String serverUri = mqttClient.getServerURI();
            boolean isConnected = mqttClient.isConnected();
            
            System.out.println("MQTT状态查询结果:");
            System.out.println("  客户端ID: " + clientId);
            System.out.println("  服务器URI: " + serverUri);
            System.out.println("  连接状态: " + (isConnected ? "已连接" : "未连接"));
            
            status.put("clientId", clientId);
            status.put("serverUri", serverUri);
            status.put("connected", isConnected);
            status.put("timestamp", LocalDateTime.now().toString());
            
            if (isConnected) {
                status.put("status", "CONNECTED");
                status.put("message", "MQTT连接正常");
            } else {
                status.put("status", "DISCONNECTED");
                status.put("message", "MQTT连接已断开");
            }
            
        } catch (Exception e) {
            System.out.println("MQTT状态查询过程中发生错误:");
            System.out.println("  错误类型: " + e.getClass().getSimpleName());
            System.out.println("  错误消息: " + e.getMessage());
            System.out.println("  堆栈跟踪:");
            e.printStackTrace();
            
            status.put("error", "状态查询失败: " + e.getMessage());
            status.put("connected", false);
            status.put("status", "ERROR");
        }
        
        System.out.println("=== MQTT状态查询API调用完成 ===");
        return status;
    }
    
    /**
     * 手动触发MQTT状态检查
     */
    @PostMapping("/check")
    public Map<String, Object> manualStatusCheck() {
        System.out.println("=== 手动MQTT状态检查API调用 ===");
        System.out.println("检查时间: " + LocalDateTime.now());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            mqttStatusMonitor.manualStatusCheck();
            result.put("success", true);
            result.put("message", "MQTT状态检查已触发");
            result.put("timestamp", LocalDateTime.now().toString());
            
            System.out.println("手动状态检查触发成功");
            
        } catch (Exception e) {
            System.out.println("手动状态检查过程中发生错误:");
            System.out.println("  错误类型: " + e.getClass().getSimpleName());
            System.out.println("  错误消息: " + e.getMessage());
            System.out.println("  堆栈跟踪:");
            e.printStackTrace();
            
            result.put("success", false);
            result.put("error", "状态检查失败: " + e.getMessage());
            result.put("timestamp", LocalDateTime.now().toString());
        }
        
        System.out.println("=== 手动MQTT状态检查API调用完成 ===");
        return result;
    }
    
    /**
     * 尝试重新连接MQTT
     */
    @PostMapping("/reconnect")
    public Map<String, Object> tryReconnect() {
        System.out.println("=== MQTT重连API调用 ===");
        System.out.println("重连时间: " + LocalDateTime.now());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean reconnected = mqttStatusMonitor.tryReconnect();
            result.put("success", reconnected);
            result.put("reconnected", reconnected);
            result.put("message", reconnected ? "MQTT重连成功" : "MQTT重连失败");
            result.put("timestamp", LocalDateTime.now().toString());
            
            System.out.println("重连尝试结果: " + (reconnected ? "成功" : "失败"));
            
        } catch (Exception e) {
            System.out.println("MQTT重连过程中发生错误:");
            System.out.println("  错误类型: " + e.getClass().getSimpleName());
            System.out.println("  错误消息: " + e.getMessage());
            System.out.println("  堆栈跟踪:");
            e.printStackTrace();
            
            result.put("success", false);
            result.put("reconnected", false);
            result.put("error", "重连失败: " + e.getMessage());
            result.put("timestamp", LocalDateTime.now().toString());
        }
        
        System.out.println("=== MQTT重连API调用完成 ===");
        return result;
    }
    
    /**
     * 获取MQTT连接摘要信息
     */
    @GetMapping("/summary")
    public Map<String, Object> getConnectionSummary() {
        System.out.println("=== MQTT连接摘要查询API调用 ===");
        System.out.println("查询时间: " + LocalDateTime.now());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String summary = mqttStatusMonitor.getConnectionStatusSummary();
            result.put("summary", summary);
            result.put("timestamp", LocalDateTime.now().toString());
            
            System.out.println("连接摘要: " + summary);
            
        } catch (Exception e) {
            System.out.println("连接摘要查询过程中发生错误:");
            System.out.println("  错误类型: " + e.getClass().getSimpleName());
            System.out.println("  错误消息: " + e.getMessage());
            System.out.println("  堆栈跟踪:");
            e.printStackTrace();
            
            result.put("error", "摘要查询失败: " + e.getMessage());
            result.put("timestamp", LocalDateTime.now().toString());
        }
        
        System.out.println("=== MQTT连接摘要查询API调用完成 ===");
        return result;
    }
    
    /**
     * 测试MQTT消息发布（仅用于测试连接）
     */
    @PostMapping("/test-publish")
    public Map<String, Object> testPublish(@RequestParam(defaultValue = "test") String message) {
        System.out.println("=== MQTT测试消息发布API调用 ===");
        System.out.println("测试时间: " + LocalDateTime.now());
        System.out.println("测试消息: " + message);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                result.put("success", false);
                result.put("error", "MQTT客户端未连接");
                result.put("timestamp", LocalDateTime.now().toString());
                return result;
            }
            
            // 发布测试消息到测试主题
            String testTopic = "test/connection";
            org.eclipse.paho.client.mqttv3.MqttMessage mqttMessage = 
                new org.eclipse.paho.client.mqttv3.MqttMessage(message.getBytes());
            mqttMessage.setQos(0); // 最多一次传递，用于测试
            
            System.out.println("正在发布测试消息到主题: " + testTopic);
            long startTime = System.currentTimeMillis();
            
            mqttClient.publish(testTopic, mqttMessage);
            
            long endTime = System.currentTimeMillis();
            System.out.println("测试消息发布成功! 耗时: " + (endTime - startTime) + " ms");
            
            result.put("success", true);
            result.put("message", "测试消息发布成功");
            result.put("topic", testTopic);
            result.put("payload", message);
            result.put("publishTime", (endTime - startTime) + " ms");
            result.put("timestamp", LocalDateTime.now().toString());
            
        } catch (MqttException e) {
            System.out.println("测试消息发布失败:");
            System.out.println("  MQTT错误代码: " + e.getReasonCode());
            System.out.println("  MQTT错误消息: " + e.getMessage());
            System.out.println("  堆栈跟踪:");
            e.printStackTrace();
            
            result.put("success", false);
            result.put("error", "测试消息发布失败: " + e.getMessage());
            result.put("mqttErrorCode", e.getReasonCode());
            result.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            System.out.println("测试消息发布过程中发生未知错误:");
            System.out.println("  错误类型: " + e.getClass().getSimpleName());
            System.out.println("  错误消息: " + e.getMessage());
            System.out.println("  堆栈跟踪:");
            e.printStackTrace();
            
            result.put("success", false);
            result.put("error", "测试消息发布失败: " + e.getMessage());
            result.put("timestamp", LocalDateTime.now().toString());
        }
        
        System.out.println("=== MQTT测试消息发布API调用完成 ===");
        return result;
    }
    
    /**
     * 使用新的连接管理器进行重连
     */
    @PostMapping("/reconnect-v2")
    public Map<String, Object> reconnectWithManager() {
        System.out.println("=== MQTT重连管理器API调用 ===");
        System.out.println("重连时间: " + LocalDateTime.now());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean reconnected = mqttConnectionManager.manualReconnect();
            result.put("success", reconnected);
            result.put("reconnected", reconnected);
            result.put("message", reconnected ? "MQTT重连成功" : "MQTT重连失败");
            result.put("timestamp", LocalDateTime.now().toString());
            
            System.out.println("重连管理器结果: " + (reconnected ? "成功" : "失败"));
            
        } catch (Exception e) {
            System.out.println("MQTT重连管理器过程中发生错误:");
            System.out.println("  错误类型: " + e.getClass().getSimpleName());
            System.out.println("  错误消息: " + e.getMessage());
            System.out.println("  堆栈跟踪:");
            e.printStackTrace();
            
            result.put("success", false);
            result.put("reconnected", false);
            result.put("error", "重连失败: " + e.getMessage());
            result.put("timestamp", LocalDateTime.now().toString());
        }
        
        System.out.println("=== MQTT重连管理器API调用完成 ===");
        return result;
    }
    
    /**
     * 获取连接管理器状态
     */
    @GetMapping("/manager-status")
    public Map<String, Object> getManagerStatus() {
        System.out.println("=== MQTT连接管理器状态查询API调用 ===");
        System.out.println("查询时间: " + LocalDateTime.now());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String status = mqttConnectionManager.getConnectionStatus();
            String stats = mqttConnectionManager.getReconnectStats();
            
            result.put("success", true);
            result.put("status", status);
            result.put("stats", stats);
            result.put("timestamp", LocalDateTime.now().toString());
            
            System.out.println("连接管理器状态: " + status);
            System.out.println("重连统计: " + stats);
            
        } catch (Exception e) {
            System.out.println("MQTT连接管理器状态查询过程中发生错误:");
            System.out.println("  错误类型: " + e.getClass().getSimpleName());
            System.out.println("  错误消息: " + e.getMessage());
            System.out.println("  堆栈跟踪:");
            e.printStackTrace();
            
            result.put("success", false);
            result.put("error", "状态查询失败: " + e.getMessage());
            result.put("timestamp", LocalDateTime.now().toString());
        }
        
        System.out.println("=== MQTT连接管理器状态查询API调用完成 ===");
        return result;
    }
}
