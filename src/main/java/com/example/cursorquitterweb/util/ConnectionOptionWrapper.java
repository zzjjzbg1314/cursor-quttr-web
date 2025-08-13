package com.example.cursorquitterweb.util;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 阿里云MQ4IoT连接选项包装器
 * 完全按照阿里云官方示例实现签名鉴权模式
 */
public class ConnectionOptionWrapper {
    
    private final String instanceId;
    private final String accessKey;
    private final String secretKey;
    private final String clientId;
    private final MqttConnectOptions mqttConnectOptions;
    
    private static final int MQTT_VERSION_3_1_1 = 4;
    
    public ConnectionOptionWrapper(String instanceId, String accessKey, String secretKey, String clientId,
                                 int connectionTimeout, int keepAliveInterval, boolean autoReconnect) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        System.out.println("=== 创建ConnectionOptionWrapper ===");
        System.out.println("Instance ID: " + instanceId);
        System.out.println("Access Key: " + accessKey.substring(0, Math.min(accessKey.length(), 8)) + "...");
        System.out.println("Secret Key: " + secretKey.substring(0, Math.min(secretKey.length(), 8)) + "...");
        System.out.println("Client ID: " + clientId);
        
        this.instanceId = instanceId;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.clientId = clientId;
        
        // 创建MQTT连接选项
        mqttConnectOptions = new MqttConnectOptions();
        
        // 设置用户名：Signature|AccessKey|InstanceId
        String username = "Signature|" + accessKey + "|" + instanceId;
        mqttConnectOptions.setUserName(username);
        System.out.println("设置用户名: " + username);
        
        // 设置密码：使用macSignature方法
        String password = Tools.macSignature(clientId, secretKey);
        mqttConnectOptions.setPassword(password.toCharArray());
        System.out.println("设置密码长度: " + password.length());
        
        // 设置其他连接选项
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setKeepAliveInterval(keepAliveInterval);
        mqttConnectOptions.setAutomaticReconnect(autoReconnect);
        mqttConnectOptions.setMqttVersion(MQTT_VERSION_3_1_1);
        mqttConnectOptions.setConnectionTimeout(connectionTimeout * 1000); // 转换为毫秒
        
        System.out.println("MQTT连接选项配置完成");
        System.out.println("Clean Session: false");
        System.out.println("Keep Alive Interval: " + keepAliveInterval);
        System.out.println("Automatic Reconnect: " + autoReconnect);
        System.out.println("MQTT Version: 3.1.1");
        System.out.println("Connection Timeout: " + connectionTimeout + "s");
    }
    
    /**
     * 获取MQTT连接选项
     */
    public MqttConnectOptions getMqttConnectOptions() {
        return mqttConnectOptions;
    }
    
    /**
     * Tools类 - 提供macSignature方法
     */
    public static class Tools {
        
        /**
         * 生成MAC签名
         * @param clientId 客户端ID
         * @param secretKey 密钥
         * @return 签名结果
         */
        public static String macSignature(String clientId, String secretKey) {
            try {
                System.out.println("正在生成MAC签名...");
                System.out.println("Client ID: " + clientId);
                System.out.println("Secret Key长度: " + secretKey.length());
                
                // 使用HMAC-SHA1算法签名
                Mac mac = Mac.getInstance("HmacSHA1");
                SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
                mac.init(secretKeySpec);
                
                // 对clientId进行签名
                byte[] hash = mac.doFinal(clientId.getBytes(StandardCharsets.UTF_8));
                
                // Base64编码
                String signature = java.util.Base64.getEncoder().encodeToString(hash);
                
                System.out.println("MAC签名生成完成，长度: " + signature.length());
                System.out.println("签名结果: " + signature);
                
                return signature;
                
            } catch (Exception e) {
                System.out.println("MAC签名生成失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("MAC签名生成失败", e);
            }
        }
    }
}
