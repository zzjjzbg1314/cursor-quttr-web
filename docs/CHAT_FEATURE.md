# 群组聊天功能说明

## 功能概述

群组聊天功能是一个基于MQTT的实时消息广播系统，支持不同类型的群组消息发送和接收。

## 主要特性

- **实时消息广播**: 通过阿里云MQTT服务实现消息的实时推送
- **多群组支持**: 支持官方群组、仅限女性、普通消息、系统消息等多种类型
- **无需存储**: 消息直接广播，不保存到数据库
- **IP记录**: 自动记录发送消息的客户端IP地址

## 消息类型

| 类型 | 描述 | MQTT主题 |
|------|------|----------|
| OFFICIAL_GROUP | 官方群组消息 | chat/official_group |
| FEMALE_ONLY | 仅限女性消息 | chat/female_only |
| GENERAL | 普通消息 | chat/general |
| SYSTEM | 系统消息 | chat/system |

## API接口

### 发送消息

**接口地址**: `POST /api/chat/sendMsg`

**请求参数**:
```json
{
    "nickName": "用户昵称",
    "userStage": "用户阶段",
    "content": "消息内容",
    "msgType": "OFFICIAL_GROUP"
}
```

**响应示例**:
```json
{
    "success": true,
    "message": "消息发送成功",
    "data": "消息发送成功"
}
```

### 获取功能信息

**接口地址**: `GET /api/chat/info`

**响应示例**:
```json
{
    "success": true,
    "message": "操作成功",
    "data": "群组聊天功能已启用，支持实时消息广播"
}
```

## MQTT配置

### 环境变量配置

```bash
# MQTT服务器地址
export MQTT_BROKER_URL=ssl://mqtt-cn-hangzhou.aliyuncs.com:1883

# MQTT客户端ID
export MQTT_CLIENT_ID=cursor-quitter-chat-123

# MQTT用户名（可选）
export MQTT_USERNAME=your_username

# MQTT密码（可选）
export MQTT_PASSWORD=your_password

# 连接超时时间（秒）
export MQTT_CONNECTION_TIMEOUT=30

# 心跳间隔（秒）
export MQTT_KEEP_ALIVE_INTERVAL=60

# 是否清理会话
export MQTT_CLEAN_SESSION=true

# 是否自动重连
export MQTT_AUTO_RECONNECT=true
```

### 配置文件配置

在 `application.yml` 中配置：

```yaml
mqtt:
  broker:
    url: ssl://mqtt-cn-hangzhou.aliyuncs.com:1883
  client:
    id: cursor-quitter-chat-${random.uuid}
  username: 
  password: 
  connection:
    timeout: 30
  keep:
    alive:
      interval: 60
  clean:
    session: true
  auto:
    reconnect: true
```

## 客户端订阅

客户端可以通过MQTT客户端订阅相应的主题来接收消息：

```bash
# 订阅官方群组消息
mosquitto_sub -h mqtt-cn-hangzhou.aliyuncs.com -t "chat/official_group"

# 订阅仅限女性消息
mosquitto_sub -h mqtt-cn-hangzhou.aliyuncs.com -t "chat/female_only"

# 订阅普通消息
mosquitto_sub -h mqtt-cn-hangzhou.aliyuncs.com -t "chat/general"

# 订阅系统消息
mosquitto_sub -h mqtt-cn-hangzhou.aliyuncs.com -t "chat/system"
```

## 消息格式

MQTT消息内容为JSON格式：

```json
{
    "nickName": "用户昵称",
    "userStage": "用户阶段",
    "content": "消息内容",
    "msgType": "OFFICIAL_GROUP",
    "createdAt": "2024-01-01T12:00:00",
    "ipAddress": "192.168.1.100"
}
```

## 使用示例

### 发送官方群组消息

```bash
curl -X POST http://localhost:8080/api/chat/sendMsg \
  -H "Content-Type: application/json" \
  -d '{
    "nickName": "张三",
    "userStage": "新手",
    "content": "大家好，我是新来的！",
    "msgType": "OFFICIAL_GROUP"
  }'
```

### 发送仅限女性消息

```bash
curl -X POST http://localhost:8080/api/chat/sendMsg \
  -H "Content-Type: application/json" \
  -d '{
    "nickName": "李四",
    "userStage": "资深用户",
    "content": "姐妹们，今天天气真好！",
    "msgType": "FEMALE_ONLY"
  }'
```

## 注意事项

1. **消息不持久化**: 所有消息只进行实时广播，不会保存到数据库
2. **IP地址记录**: 系统会自动记录发送消息的客户端IP地址
3. **MQTT连接**: 确保MQTT服务器配置正确，客户端能够正常连接
4. **消息验证**: 所有消息都会进行参数验证，确保必要字段不为空
5. **错误处理**: 发送失败时会返回详细的错误信息

## 故障排除

### 常见问题

1. **MQTT连接失败**
   - 检查MQTT服务器地址和端口
   - 验证用户名和密码
   - 检查网络连接

2. **消息发送失败**
   - 检查请求参数格式
   - 验证消息类型枚举值
   - 查看服务器日志

3. **消息接收不到**
   - 确认MQTT主题订阅正确
   - 检查客户端连接状态
   - 验证消息类型匹配

### 日志查看

查看聊天功能的详细日志：

```bash
# 查看应用日志
tail -f logs/application.log

# 查看特定聊天相关的日志
grep "聊天消息" logs/application.log
```
