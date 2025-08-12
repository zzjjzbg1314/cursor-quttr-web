package com.example.cursorquitterweb.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * MQTT配置类
 */
@Configuration
@PropertySource("classpath:application.yml")
public class MqttConfig {
    

    @Value("${mqtt.broker.url:ssl://your_instance_id_here.mqtt.aliyuncs.com:8883}")
    private String brokerUrl;
    
    @Value("${mqtt.client.id:GID_QUITTR_${random.uuid}}")
    private String clientId;
    
    @Value("${mqtt.access-key:}")
    private String accessKey;
    
    @Value("${mqtt.secret-key:}")
    private String secretKey;
    
    @Value("${mqtt.username:}")
    private String username;
    
    @Value("${mqtt.password:}")
    private String password;
    
    @Value("${mqtt.connection.timeout:30}")
    private int connectionTimeout;
    
    @Value("${mqtt.keep.alive.interval:60}")
    private int keepAliveInterval;
    
    @Value("${mqtt.clean.session:true}")
    private boolean cleanSession;
    
    @Value("${mqtt.auto.reconnect:true}")
    private boolean autoReconnect;
    
    /**
     * 创建MQTT客户端
     */
    @Bean
    public MqttClient mqttClient() throws MqttException {
        MqttClient mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(cleanSession);
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        options.setAutomaticReconnect(autoReconnect);
        
        // 优先使用阿里云AccessKey认证
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            // 阿里云MQTT使用AccessKey作为用户名，SecretKey作为密码
            options.setUserName(accessKey);
            options.setPassword(secretKey.toCharArray());
        } else if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            // 备用：传统用户名密码认证
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        } else {
        }
        
        mqttClient.connect(options);
        
        return mqttClient;
    }
    
    // Getter方法
    public String getBrokerUrl() {
        return brokerUrl;
    }
    
    public String getAccessKey() {
        return accessKey;
    }
    
    public String getSecretKey() {
        return secretKey;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }
    
    public boolean isCleanSession() {
        return cleanSession;
    }
    
    public boolean isAutoReconnect() {
        return autoReconnect;
    }
}
