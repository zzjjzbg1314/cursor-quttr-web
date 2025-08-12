# MQTT阿里云官方实现说明

## 概述

本项目已完全按照阿里云MQ4IoT官方示例代码改造MQTT发送消息机制，使用标准的签名鉴权模式和官方推荐的连接方式。

## 核心组件

### 1. ConnectionOptionWrapper

**位置**: `src/main/java/com/example/cursorquitterweb/util/ConnectionOptionWrapper.java`

**功能**: 生成阿里云MQ4IoT标准的连接选项
- 用户名格式: `AccessKey&InstanceId`
- 密码格式: `HMAC-SHA1(ClientId&InstanceId&Timestamp, SecretKey)`
- 使用UTC时间戳进行签名

**关键方法**:
```java
public MqttConnectOptions getMqttConnectOptions()
private String generateUsername()
private String generatePassword()
```

### 2. MqttConfig

**位置**: `src/main/java/com/example/cursorquitterweb/config/MqttConfig.java`

**功能**: MQTT客户端配置和连接管理
- 完全按照阿里云官方示例实现
- 支持SSL和TCP连接
- 自动订阅主题
- 完整的回调处理

**关键特性**:
- 使用`MqttCallbackExtended`接口
- 自动订阅`parentTopic/test`主题
- 设置发送超时时间防止阻塞
- 线程池管理

### 3. ChatServiceImpl

**位置**: `src/main/java/com/example/cursorquitterweb/service/impl/ChatServiceImpl.java`

**功能**: 聊天消息的MQTT发送服务
- 支持广播消息和点对点消息
- 完整的消息序列化
- 详细的日志记录

## 配置说明

### application.yml配置

```yaml
mqtt:
  # 阿里云MQ4IoT实例信息
  instance:
    id: ${MQTT_INSTANCE_ID:your_instance_id_here}
  # 接入点地址
  endpoint: ${MQTT_ENDPOINT:your_instance_id_here.mqtt.aliyuncs.com}
  # 阿里云AccessKey认证
  access-key: ${MQTT_ACCESS_KEY:your_access_key_here}
  secret-key: ${MQTT_SECRET_KEY:your_secret_key_here}
  # MQ4IoT客户端配置
  group:
    id: ${MQTT_GROUP_ID:GID_QUITTR}
  device:
    id: ${MQTT_DEVICE_ID:${random.uuid}}
  # 主题配置
  parent:
    topic: ${MQTT_PARENT_TOPIC:CHAT_OFFICIAL_GROUP}
  # QoS配置
  qos:
    level: ${MQTT_QOS_LEVEL:0}
  # 连接配置 - 使用TCP连接
  port:
    tcp: ${MQTT_TCP_PORT:1883}
```

### 环境变量配置

```bash
# 阿里云MQ4IoT实例ID
export MQTT_INSTANCE_ID=your_instance_id_here

# 接入点地址
export MQTT_ENDPOINT=your_instance_id_here.mqtt.aliyuncs.com

# AccessKey和SecretKey - 请使用你自己的凭据
export MQTT_ACCESS_KEY=your_access_key_here
export MQTT_SECRET_KEY=your_secret_key_here

# 客户端配置
export MQTT_GROUP_ID=GID_QUITTR
export MQTT_DEVICE_ID=your-device-id

# 主题配置
export MQTT_PARENT_TOPIC=CHAT_OFFICIAL_GROUP

# QoS级别
export MQTT_QOS_LEVEL=0

# 连接方式 - 使用TCP
export MQTT_TCP_PORT=1883
```

## 连接流程

### 1. 客户端创建

```java
// 构造ClientId：GroupID@@@DeviceId
String clientId = groupId + "@@@" + deviceId;

// 构造连接URL - 使用TCP连接
String brokerUrl = "tcp://" + endPoint + ":" + tcpPort;

// 创建MQTT客户端
MqttClient mqttClient = new MqttClient(brokerUrl, clientId, memoryPersistence);
```

### 2. 连接选项配置

```java
// 创建ConnectionOptionWrapper
ConnectionOptionWrapper wrapper = new ConnectionOptionWrapper(
    instanceId, accessKey, secretKey, clientId);

// 获取连接选项
MqttConnectOptions options = wrapper.getMqttConnectOptions();
```

### 3. 连接建立

```java
// 连接MQTT服务器
mqttClient.connect(options);
```

### 4. 主题订阅

```java
// 自动订阅主题
final String topicFilter[] = {parentTopic + "/test"};
final int[] qos = {qosLevel};
mqttClient.subscribe(topicFilter, qos);
```

## 消息发送

### 1. 广播消息

```java
// 构造主题
String topic = parentTopic + "/chat";

// 创建消息
MqttMessage message = new MqttMessage(payload.getBytes());
message.setQos(qosLevel);

// 发布消息
mqttClient.publish(topic, message);
```

### 2. 点对点消息

```java
// 点对点主题格式：parentTopic/p2p/targetClientId
String targetClientId = groupId + "@@@" + deviceId;
String p2pTopic = parentTopic + "/p2p/" + targetClientId;

// 发送点对点消息
mqttClient.publish(p2pTopic, message);
```

## 回调处理

### 1. 连接完成回调

```java
@Override
public void connectComplete(boolean reconnect, String serverURI) {
    // 连接成功后自动订阅主题
    executorService.submit(() -> {
        try {
            mqttClient.subscribe(topicFilter, qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    });
}
```

### 2. 连接丢失回调

```java
@Override
public void connectionLost(Throwable throwable) {
    // 记录连接丢失信息
    System.out.println("MQTT连接丢失: " + throwable.getMessage());
}
```

### 3. 消息到达回调

```java
@Override
public void messageArrived(String topic, MqttMessage message) throws Exception {
    // 处理接收到的消息
    System.out.println("收到消息: " + new String(message.getPayload()));
}
```

### 4. 消息投递完成回调

```java
@Override
public void deliveryComplete(IMqttDeliveryToken token) {
    // 消息发送完成确认
    System.out.println("消息发送成功: " + token.getTopics()[0]);
}
```

## 日志输出

启动应用后，控制台会输出详细的MQTT连接日志：

```
=== MQTT客户端创建开始（阿里云MQ4IoT官方方式） ===
Instance ID: your_instance_id_here
End Point: your_instance_id_here.mqtt.aliyuncs.com
Access Key: LTAI5tKQ...
Secret Key: lS4KnJ6...
Group ID: GID_QUITTR
Device ID: 12345678-1234-1234-1234-123456789abc
Parent Topic: CHAT_OFFICIAL_GROUP
QoS Level: 0
使用TCP连接，端口: 1883
Client ID: GID_QUITTR@@@12345678-1234-1234-1234-123456789abc
使用TCP连接: tcp://your_instance_id_here.mqtt.aliyuncs.com:1883
正在创建MQTT客户端...
=== 生成阿里云MQ4IoT连接选项 ===
Instance ID: your_instance_id_here
Access Key: LTAI5tKQ...
Secret Key: lS4KnJ6...
Client ID: GID_QUITTR@@@12345678-1234-1234-1234-123456789abc
当前时间戳: 2025-08-12T10:30:00Z
签名字符串: GID_QUITTR@@@12345678-1234-1234-1234-123456789abc&your_instance_id_here&2025-08-12T10:30:00Z
签名结果: ABC123DEF456...
生成的用户名: your_access_key_here&your_instance_id_here
生成的密码长度: 28
MQTT连接选项生成完成
MQTT客户端创建成功，发送超时时间: 5000ms
线程池创建成功
正在设置MQTT回调...
MQTT回调设置完成
正在连接到MQTT服务器: tcp://your_instance_id_here.mqtt.aliyuncs.com:1883
MQTT连接成功! 耗时: 1234 ms
客户端ID: GID_QUITTR@@@12345678-1234-1234-1234-123456789abc
服务器URI: ssl://your_instance_id_here.mqtt.aliyuncs.com:8883
连接状态: 已连接
=== MQTT连接完成 ===
是否重连: false
服务器URI: ssl://your_instance_id_here.mqtt.aliyuncs.com:8883
正在订阅主题...
主题订阅成功: CHAT_OFFICIAL_GROUP/test
=== MQTT客户端创建完成 ===
```

## 测试验证

### 1. 连接状态检查

```bash
curl http://localhost:8080/api/mqtt/status
```

### 2. 手动状态检查

```bash
curl -X POST http://localhost:8080/api/mqtt/check
```

### 3. 测试消息发布

```bash
curl -X POST "http://localhost:8080/api/mqtt/test-publish?message=hello"
```

### 4. 聊天消息发送

通过聊天API发送消息，观察MQTT日志输出。

## 注意事项

1. **ClientId格式**: 必须使用`GroupID@@@DeviceId`格式，确保全局唯一
2. **时间戳**: 签名使用UTC时间戳，确保时间同步
3. **连接方式**: 使用TCP连接，端口1883
4. **主题权限**: 确保在阿里云控制台申请了相应的主题权限
5. **连接限制**: 注意阿里云MQ4IoT的连接数限制

## 故障排除

如果遇到连接问题，请参考`docs/MQTT_TROUBLESHOOTING.md`文档进行排查。
