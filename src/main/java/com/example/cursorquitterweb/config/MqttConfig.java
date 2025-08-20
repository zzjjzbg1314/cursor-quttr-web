package com.example.cursorquitterweb.config;

import com.example.cursorquitterweb.util.ConnectionOptionWrapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * MQTT配置类 - 完全按照阿里云MQ4IoT官方示例实现
 */
//@Configuration
//@PropertySource("classpath:application.yml")
public class MqttConfig {
    
    @Value("${mqtt.instance.id:your_instance_id_here}")
    private String instanceId;
    
    @Value("${mqtt.endpoint:your_instance_id_here.mqtt.aliyuncs.com}")
    private String endPoint;
    
    @Value("${mqtt.access-key:}")
    private String accessKey;

    @Value("${mqtt.secret-key:}")
    private String secretKey;
    
    @Value("${mqtt.group.id:GID_QUITTR}")
    private String groupId;
    
    @Value("${mqtt.device.id:${random.uuid}}")
    private String deviceId;
    
    @Value("${mqtt.parent.topic:CHAT_OFFICIAL_GROUP}")
    private String parentTopic;
    
    @Value("${mqtt.qos.level:0}")
    private int qosLevel;
    
    @Value("${mqtt.port.tcp:1883}")
    private int tcpPort;
    
    @Value("${mqtt.port.ssl:8883}")
    private int sslPort;
    
    @Value("${mqtt.connection.use-ssl:true}")
    private boolean useSsl;
    
    @Value("${mqtt.connection.timeout:30}")
    private int connectionTimeout;
    
    @Value("${mqtt.connection.keep-alive:120}")
    private int keepAliveInterval;
    
    @Value("${mqtt.connection.auto-reconnect:true}")
    private boolean autoReconnect;
    
    @Value("${mqtt.connection.reconnect-delay:5000}")
    private int reconnectDelay;
    
    @Value("${mqtt.connection.max-reconnect-attempts:10}")
    private int maxReconnectAttempts;
    
    /**
     * 创建MQTT客户端
     * 完全按照阿里云MQ4IoT官方示例实现
     */
    @Bean
    public MqttClient mqttClient() throws Exception {
        System.out.println("=== MQTT客户端创建开始（阿里云MQ4IoT官方方式） ===");
        
        // 检查环境变量
        System.out.println("=== 环境变量检查 ===");
        System.out.println("系统环境变量 MQTT_ACCESS_KEY: " + System.getenv("MQTT_ACCESS_KEY"));
        System.out.println("系统环境变量 MQTT_SECRET_KEY: " + System.getenv("MQTT_SECRET_KEY"));
        System.out.println("系统环境变量 MQTT_INSTANCE_ID: " + System.getenv("MQTT_INSTANCE_ID"));
        
        // 检查注入的值
        System.out.println("=== 注入值检查 ===");
        System.out.println("Instance ID: " + instanceId);
        System.out.println("End Point: " + endPoint);
        System.out.println("Access Key: " + (accessKey != null && !accessKey.isEmpty() ? accessKey.substring(0, Math.min(accessKey.length(), 8)) + "..." : "NULL或空"));
        System.out.println("Secret Key: " + (secretKey != null && !secretKey.isEmpty() ? secretKey.substring(0, Math.min(secretKey.length(), 8)) + "..." : "NULL或空"));
        
        // 验证关键参数
        if (accessKey == null || accessKey.trim().isEmpty()) {
            throw new IllegalArgumentException("MQTT_ACCESS_KEY 环境变量未设置或为空");
        }
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalArgumentException("MQTT_SECRET_KEY 环境变量未设置或为空");
        }
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("MQTT_INSTANCE_ID 环境变量未设置或为空");
        }
        System.out.println("Group ID: " + groupId);
        System.out.println("Device ID: " + deviceId);
        System.out.println("Parent Topic: " + parentTopic);
        System.out.println("QoS Level: " + qosLevel);
        System.out.println("使用TCP连接，端口: " + tcpPort);
        
        // 构造ClientId：GroupID@@@DeviceId
        String clientId = groupId + "@@@" + deviceId;
        System.out.println("Client ID: " + clientId);
        
        // 构造连接URL - 根据配置选择SSL或TCP连接
        String brokerUrl;
        if (useSsl) {
            brokerUrl = "ssl://" + endPoint + ":" + sslPort;
            System.out.println("使用SSL连接: " + brokerUrl);
        } else {
            brokerUrl = "tcp://" + endPoint + ":" + tcpPort;
            System.out.println("使用TCP连接: " + brokerUrl);
        }
        
        // 创建ConnectionOptionWrapper
        ConnectionOptionWrapper connectionOptionWrapper = new ConnectionOptionWrapper(
            instanceId, accessKey, secretKey, clientId, 
            connectionTimeout, keepAliveInterval, autoReconnect);
        
        // 创建MQTT客户端
        System.out.println("正在创建MQTT客户端...");
        final MemoryPersistence memoryPersistence = new MemoryPersistence();
        final MqttClient mqttClient = new MqttClient(brokerUrl, clientId, memoryPersistence);
        
        // 设置发送超时时间，防止无限阻塞
        mqttClient.setTimeToWait(5000);
        System.out.println("MQTT客户端创建成功，发送超时时间: 5000ms");
        
        // 输出连接配置信息
        System.out.println("=== MQTT连接配置信息 ===");
        System.out.println("连接超时时间: " + connectionTimeout + " 秒");
        System.out.println("保持连接间隔: " + keepAliveInterval + " 秒");
        System.out.println("自动重连: " + (autoReconnect ? "启用" : "禁用"));
        System.out.println("重连延迟: " + reconnectDelay + " 毫秒");
        System.out.println("最大重连次数: " + maxReconnectAttempts);
        
        // 创建线程池
        final ExecutorService executorService = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        System.out.println("线程池创建成功");
        
        // 设置回调
        setupMqttCallbacks(mqttClient, executorService);
        
        // 连接MQTT服务器
        System.out.println("正在连接到MQTT服务器: " + brokerUrl);
        long startTime = System.currentTimeMillis();
        
        mqttClient.connect(connectionOptionWrapper.getMqttConnectOptions());
        
        long endTime = System.currentTimeMillis();
        System.out.println("MQTT连接成功! 耗时: " + (endTime - startTime) + " ms");
        System.out.println("客户端ID: " + mqttClient.getClientId());
        System.out.println("服务器URI: " + mqttClient.getServerURI());
        System.out.println("连接状态: " + (mqttClient.isConnected() ? "已连接" : "未连接"));
        
        System.out.println("=== MQTT客户端创建完成 ===");
        
        return mqttClient;
    }
    
    /**
     * 设置MQTT回调
     * 完全按照阿里云官方示例实现
     */
    private void setupMqttCallbacks(MqttClient mqttClient, ExecutorService executorService) {
        System.out.println("正在设置MQTT回调...");
        
        mqttClient.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                System.out.println("=== MQTT连接完成 ===");
                System.out.println("是否重连: " + reconnect);
                System.out.println("服务器URI: " + serverURI);
                
                // 客户端连接成功后就需要尽快订阅需要的topic
                System.out.println("正在订阅主题...");
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final String topicFilter[] = {parentTopic + "/test"};
                            final int[] qos = {qosLevel};
                            mqttClient.subscribe(topicFilter, qos);
                            System.out.println("主题订阅成功: " + topicFilter[0]);
                        } catch (MqttException e) {
                            System.out.println("主题订阅失败: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });
            }
            
            @Override
            public void connectionLost(Throwable throwable) {
                System.out.println("=== MQTT连接丢失 ===");
                System.out.println("连接丢失时间: " + java.time.LocalDateTime.now());
                System.out.println("丢失原因: " + throwable.getMessage());
                throwable.printStackTrace();
            }
            
            @Override
            public void messageArrived(String topic, org.eclipse.paho.client.mqttv3.MqttMessage message) throws Exception {
                System.out.println("=== MQTT消息到达 ===");
                System.out.println("主题: " + topic);
                System.out.println("消息内容: " + new String(message.getPayload()));
                System.out.println("QoS: " + message.getQos());
                
                // 消费消息的回调接口，需要确保该接口不抛异常
                // 该接口运行返回即代表消息消费成功
                // 消费消息需要保证在规定时间内完成
            }
            
            @Override
            public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                System.out.println("=== MQTT消息投递完成 ===");
                System.out.println("投递完成时间: " + java.time.LocalDateTime.now());
                try {
                    System.out.println("发送消息成功，主题: " + token.getTopics()[0]);
                } catch (Exception e) {
                    System.out.println("获取投递主题失败: " + e.getMessage());
                }
            }
        });
        
        System.out.println("MQTT回调设置完成");
    }
    
    // Getter方法
    public String getInstanceId() {
        return instanceId;
    }
    
    public String getEndPoint() {
        return endPoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public String getParentTopic() {
        return parentTopic;
    }
    
    public int getQosLevel() {
        return qosLevel;
    }
    

}
