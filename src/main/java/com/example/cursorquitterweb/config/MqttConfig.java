package com.example.cursorquitterweb.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

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
    
    @Value("${mqtt.max.inflight:1000}")
    private int maxInflight;
    
    @Value("${mqtt.connection.retry.count:3}")
    private int connectionRetryCount;
    
    @Value("${mqtt.connection.retry.delay:5000}")
    private long connectionRetryDelay;
    
    /**
     * 创建MQTT客户端
     */
    @Bean
    public MqttClient mqttClient() throws MqttException {
        System.out.println("=== MQTT客户端创建开始 ===");
        System.out.println("MQTT Broker URL: " + brokerUrl);
        System.out.println("MQTT Client ID: " + clientId);
        System.out.println("MQTT Connection Timeout: " + connectionTimeout + " seconds");
        System.out.println("MQTT Keep Alive Interval: " + keepAliveInterval + " seconds");
        System.out.println("MQTT Clean Session: " + cleanSession);
        System.out.println("MQTT Auto Reconnect: " + autoReconnect);
        System.out.println("MQTT Max Inflight: " + maxInflight);
        System.out.println("MQTT Connection Retry Count: " + connectionRetryCount);
        System.out.println("MQTT Connection Retry Delay: " + connectionRetryDelay + " ms");
        
        MqttClient mqttClient = null;
        MqttException lastException = null;
        
        for (int attempt = 1; attempt <= connectionRetryCount; attempt++) {
            try {
                System.out.println("=== 第 " + attempt + " 次连接尝试 ===");
                
                System.out.println("正在创建MQTT客户端...");
                mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
                System.out.println("MQTT客户端创建成功: " + mqttClient.getClientId());
                
                System.out.println("正在配置MQTT连接选项...");
                MqttConnectOptions options = createConnectOptions();
                
                System.out.println("正在连接到MQTT Broker: " + brokerUrl);
                System.out.println("连接超时时间: " + connectionTimeout + " 秒");
                
                long startTime = System.currentTimeMillis();
                mqttClient.connect(options);
                long endTime = System.currentTimeMillis();
                
                System.out.println("MQTT连接成功! 耗时: " + (endTime - startTime) + " ms");
                System.out.println("客户端ID: " + mqttClient.getClientId());
                System.out.println("服务器URI: " + mqttClient.getServerURI());
                System.out.println("连接状态: " + (mqttClient.isConnected() ? "已连接" : "未连接"));
                
                // 设置连接丢失回调
                setupMqttCallbacks(mqttClient);
                
                System.out.println("MQTT回调设置完成");
                System.out.println("=== MQTT客户端创建完成 ===");
                
                return mqttClient;
                
            } catch (MqttException e) {
                lastException = e;
                System.out.println("=== 第 " + attempt + " 次连接尝试失败 ===");
                System.out.println("错误代码: " + e.getReasonCode());
                System.out.println("错误消息: " + e.getMessage());
                System.out.println("错误原因: " + e.getCause());
                
                if (e.getReasonCode() == 32109) {
                    System.out.println("检测到连接断开错误(32109)，可能的原因:");
                    System.out.println("1. 网络连接不稳定");
                    System.out.println("2. SSL/TLS配置问题");
                    System.out.println("3. 认证信息错误");
                    System.out.println("4. 防火墙或代理阻止");
                    System.out.println("5. MQTT服务器不可用");
                }
                
                if (attempt < connectionRetryCount) {
                    System.out.println("等待 " + connectionRetryDelay + " ms 后进行第 " + (attempt + 1) + " 次尝试...");
                    try {
                        Thread.sleep(connectionRetryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT);
                    }
                }
                
                // 清理资源
                if (mqttClient != null) {
                    try {
                        mqttClient.close();
                    } catch (Exception closeException) {
                        System.out.println("关闭MQTT客户端时发生错误: " + closeException.getMessage());
                    }
                }
            } catch (Exception e) {
                System.out.println("=== 第 " + attempt + " 次连接尝试过程中发生未知错误 ===");
                System.out.println("错误类型: " + e.getClass().getSimpleName());
                System.out.println("错误消息: " + e.getMessage());
                System.out.println("堆栈跟踪:");
                e.printStackTrace();
                
                if (attempt < connectionRetryCount) {
                    System.out.println("等待 " + connectionRetryDelay + " ms 后进行第 " + (attempt + 1) + " 次尝试...");
                    try {
                        Thread.sleep(connectionRetryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT);
                    }
                }
            }
        }
        
        // 所有重试都失败了
        System.out.println("=== 所有连接尝试都失败了 ===");
        System.out.println("最终错误代码: " + lastException.getReasonCode());
        System.out.println("最终错误消息: " + lastException.getMessage());
        System.out.println("建议检查:");
        System.out.println("1. 网络连接是否正常");
        System.out.println("2. MQTT服务器地址是否正确");
        System.out.println("3. 认证信息是否正确");
        System.out.println("4. 防火墙设置");
        System.out.println("5. SSL证书配置");
        
        throw lastException;
    }
    
    /**
     * 创建MQTT连接选项
     */
    private MqttConnectOptions createConnectOptions() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(cleanSession);
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        options.setAutomaticReconnect(autoReconnect);
        options.setMaxInflight(maxInflight);
        
        // 设置SSL属性
        if (brokerUrl.startsWith("ssl://")) {
            System.out.println("检测到SSL连接，配置SSL属性...");
            try {
                // 创建信任所有证书的TrustManager（仅用于测试，生产环境应使用proper证书）
                TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
                };
                
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                
                options.setSocketFactory(sslContext.getSocketFactory());
                System.out.println("SSL配置完成");
                
            } catch (Exception e) {
                System.out.println("SSL配置失败: " + e.getMessage());
                System.out.println("将使用默认SSL配置");
            }
        }
        
        System.out.println("MQTT连接选项配置完成");
        
        // 优先使用阿里云AccessKey认证
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            System.out.println("使用阿里云AccessKey认证方式");
            System.out.println("AccessKey: " + accessKey.substring(0, Math.min(accessKey.length(), 8)) + "...");
            System.out.println("SecretKey: " + secretKey.substring(0, Math.min(secretKey.length(), 8)) + "...");
            // 阿里云MQTT使用AccessKey作为用户名，SecretKey作为密码
            options.setUserName(accessKey);
            options.setPassword(secretKey.toCharArray());
        } else if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            System.out.println("使用传统用户名密码认证方式");
            System.out.println("Username: " + username);
            System.out.println("Password: " + password.substring(0, Math.min(password.length(), 8)) + "...");
            // 备用：传统用户名密码认证
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        } else {
            System.out.println("警告: 未配置任何认证信息，将使用匿名连接");
        }
        
        return options;
    }
    
    /**
     * 设置MQTT回调
     */
    private void setupMqttCallbacks(MqttClient mqttClient) {
        mqttClient.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("=== MQTT连接丢失 ===");
                System.out.println("连接丢失时间: " + java.time.LocalDateTime.now());
                System.out.println("丢失原因: " + cause.getMessage());
                cause.printStackTrace();
            }
            
            @Override
            public void messageArrived(String topic, org.eclipse.paho.client.mqttv3.MqttMessage message) {
                System.out.println("=== MQTT消息到达 ===");
                System.out.println("主题: " + topic);
                System.out.println("消息内容: " + new String(message.getPayload()));
                System.out.println("QoS: " + message.getQos());
            }
            
            @Override
            public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                System.out.println("=== MQTT消息投递完成 ===");
                System.out.println("投递完成时间: " + java.time.LocalDateTime.now());
            }
        });
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
