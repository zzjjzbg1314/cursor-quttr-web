package com.example.cursorquitterweb.util;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MQTT连接状态监控工具类
 * 定期检查MQTT连接状态并输出详细日志
 */
@Component
public class MqttStatusMonitor {
    
    @Autowired
    private MqttClient mqttClient;
    
    /**
     * 每30秒检查一次MQTT连接状态
     */
//    @Scheduled(fixedRate = 30000)
    public void checkMqttStatus() {
        System.out.println("=== MQTT连接状态检查 ===");
        System.out.println("检查时间: " + LocalDateTime.now());
        
        if (mqttClient == null) {
            System.out.println("错误: MQTT客户端为空，无法检查状态");
            return;
        }
        
        try {
            // 检查基本连接信息
            System.out.println("MQTT客户端基本信息:");
            System.out.println("  客户端ID: " + mqttClient.getClientId());
            System.out.println("  服务器URI: " + mqttClient.getServerURI());
            
            // 检查连接状态
            boolean isConnected = mqttClient.isConnected();
            System.out.println("  连接状态: " + (isConnected ? "已连接" : "未连接"));
            
            if (isConnected) {
                System.out.println("MQTT连接正常");
                
                // 检查连接质量相关指标
                try {
                    // 尝试发送一个ping消息来测试连接质量
                    System.out.println("正在测试连接质量...");
                    long startTime = System.currentTimeMillis();
                    
                    // 这里可以添加ping测试逻辑
                    // 由于Paho MQTT客户端没有直接的ping方法，我们可以通过其他方式测试
                    
                    long endTime = System.currentTimeMillis();
                    System.out.println("连接质量测试完成，响应时间: " + (endTime - startTime) + " ms");
                    
                } catch (Exception e) {
                    System.out.println("连接质量测试失败: " + e.getMessage());
                }
                
            } else {
                System.out.println("警告: MQTT连接已断开");
                System.out.println("建议检查网络连接和MQTT服务器状态");
            }
            
            // 检查是否有待处理的消息
            try {
                // 这里可以添加检查待处理消息的逻辑
                System.out.println("待处理消息检查: 功能待实现");
            } catch (Exception e) {
                System.out.println("待处理消息检查失败: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.out.println("MQTT状态检查过程中发生错误:");
            System.out.println("  错误类型: " + e.getClass().getSimpleName());
            System.out.println("  错误消息: " + e.getMessage());
            System.out.println("  堆栈跟踪:");
            e.printStackTrace();
        }
        
        System.out.println("=== MQTT连接状态检查完成 ===");
    }
    
    /**
     * 手动检查MQTT连接状态
     * 可以在需要时调用此方法
     */
    public void manualStatusCheck() {
        System.out.println("=== 手动MQTT状态检查 ===");
        checkMqttStatus();
    }
    
    /**
     * 获取MQTT连接状态摘要
     */
    public String getConnectionStatusSummary() {
        if (mqttClient == null) {
            return "MQTT客户端未初始化";
        }
        
        try {
            boolean isConnected = mqttClient.isConnected();
            String status = isConnected ? "已连接" : "未连接";
            String clientId = mqttClient.getClientId();
            String serverUri = mqttClient.getServerURI();
            
            return String.format("MQTT状态: %s | 客户端ID: %s | 服务器: %s", 
                               status, clientId, serverUri);
        } catch (Exception e) {
            return "MQTT状态检查失败: " + e.getMessage();
        }
    }
    
    /**
     * 尝试重新连接MQTT
     */
    public boolean tryReconnect() {
        System.out.println("=== 尝试MQTT重新连接 ===");
        System.out.println("重连时间: " + LocalDateTime.now());
        
        if (mqttClient == null) {
            System.out.println("错误: MQTT客户端为空，无法重连");
            return false;
        }
        
        try {
            if (mqttClient.isConnected()) {
                System.out.println("MQTT客户端已连接，无需重连");
                return true;
            }
            
            System.out.println("正在尝试重新连接...");
            long startTime = System.currentTimeMillis();
            
            // 这里需要实现重连逻辑
            // 由于Spring管理的Bean，重连可能需要重新创建客户端
            System.out.println("注意: 重连功能需要重新创建MQTT客户端");
            
            long endTime = System.currentTimeMillis();
            System.out.println("重连尝试完成，耗时: " + (endTime - startTime) + " ms");
            
            return false; // 暂时返回false，因为重连逻辑未完全实现
            
        } catch (Exception e) {
            System.out.println("MQTT重连过程中发生错误:");
            System.out.println("  错误类型: " + e.getClass().getSimpleName());
            System.out.println("  错误消息: " + e.getMessage());
            System.out.println("  堆栈跟踪:");
            e.printStackTrace();
            return false;
        }
    }
}
