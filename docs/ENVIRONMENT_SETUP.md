# 环境变量配置说明

## 🚨 重要安全提醒

**请勿在代码中硬编码AccessKey和SecretKey！** 这些敏感信息应该通过环境变量设置。

## 环境变量配置

### 1. 创建环境变量文件

在项目根目录创建 `.env` 文件（注意：此文件不应提交到Git）：

```bash
# 阿里云MQ4IoT环境变量配置
# 请填入你的真实凭据

# 阿里云MQ4IoT实例信息
MQTT_INSTANCE_ID=your_instance_id_here

# 接入点地址
MQTT_ENDPOINT=your_instance_id_here.mqtt.aliyuncs.com

# 阿里云AccessKey认证 - 请填入你的真实凭据
MQTT_ACCESS_KEY=your_access_key_here
MQTT_SECRET_KEY=your_secret_key_here

# MQ4IoT客户端配置
MQTT_GROUP_ID=GID_QUITTR
MQTT_DEVICE_ID=your_device_id_here

# 主题配置
MQTT_PARENT_TOPIC=CHAT_OFFICIAL_GROUP

# QoS配置
MQTT_QOS_LEVEL=0

# 连接配置 - 使用TCP连接
MQTT_TCP_PORT=1883
```

### 2. 设置环境变量

#### Linux/macOS
```bash
# 方法1：导出环境变量
export MQTT_ACCESS_KEY=your_access_key_here
export MQTT_SECRET_KEY=your_secret_key_here
export MQTT_INSTANCE_ID=your_instance_id_here
export MQTT_ENDPOINT=your_instance_id_here.mqtt.aliyuncs.com
export MQTT_GROUP_ID=GID_QUITTR
export MQTT_DEVICE_ID=your_device_id_here
export MQTT_PARENT_TOPIC=CHAT_OFFICIAL_GROUP
export MQTT_QOS_LEVEL=0
export MQTT_TCP_PORT=1883

# 方法2：使用source命令加载.env文件
source .env
```

#### Windows
```cmd
# 设置环境变量
set MQTT_ACCESS_KEY=your_access_key_here
set MQTT_SECRET_KEY=your_secret_key_here
set MQTT_INSTANCE_ID=your_instance_id_here
set MQTT_ENDPOINT=your_instance_id_here.mqtt.aliyuncs.com
set MQTT_GROUP_ID=GID_QUITTR
set MQTT_DEVICE_ID=your_device_id_here
set MQTT_PARENT_TOPIC=CHAT_OFFICIAL_GROUP
set MQTT_QOS_LEVEL=0
set MQTT_TCP_PORT=1883
```

### 3. 验证环境变量

```bash
# 检查环境变量是否设置成功
echo $MQTT_ACCESS_KEY
echo $MQTT_SECRET_KEY
echo $MQTT_INSTANCE_ID
```

## 安全最佳实践

### 1. 永远不要提交敏感信息
- 将 `.env` 文件添加到 `.gitignore`
- 不要在代码中硬编码凭据
- 不要在提交信息中包含敏感数据

### 2. 使用环境变量
- 开发环境：使用 `.env` 文件
- 生产环境：使用系统环境变量或容器环境变量
- CI/CD：使用安全的密钥管理服务

### 3. 定期轮换凭据
- 定期更新AccessKey和SecretKey
- 使用最小权限原则
- 监控API调用日志

## 故障排除

### 环境变量未生效
```bash
# 检查Spring Boot是否正确读取环境变量
java -jar your-app.jar --debug

# 或者在代码中添加调试日志
System.out.println("AccessKey: " + System.getenv("MQTT_ACCESS_KEY"));
```

### 权限问题
- 确保AccessKey有足够的权限访问MQ4IoT服务
- 检查阿里云控制台中的权限配置
- 验证实例ID和接入点地址是否正确

## 示例配置

### 开发环境
```bash
# 开发环境配置
MQTT_ACCESS_KEY=dev_access_key
MQTT_SECRET_KEY=dev_secret_key
MQTT_INSTANCE_ID=dev_instance
```

### 生产环境
```bash
# 生产环境配置
MQTT_ACCESS_KEY=prod_access_key
MQTT_SECRET_KEY=prod_secret_key
MQTT_INSTANCE_ID=prod_instance
```

## 相关文档

- [阿里云MQ4IoT官方文档](https://help.aliyun.com/product/164145.html)
- [Spring Boot环境变量配置](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
- [安全最佳实践指南](https://help.aliyun.com/document_detail/102600.html)
