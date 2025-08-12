# MQTT连接问题排查指南

## 常见错误代码

### 错误代码 32109 - "已断开连接"

**错误描述**: `java.io.EOFException` - 文件结束异常

**可能原因**:
1. **网络连接问题**
   - 网络不稳定
   - 防火墙阻止
   - 代理服务器问题

2. **SSL/TLS配置问题**
   - SSL证书验证失败
   - TLS版本不兼容
   - 加密套件不支持

3. **认证信息错误**
   - AccessKey/SecretKey不正确
   - 认证方式不匹配
   - 权限不足

4. **MQTT服务器问题**
   - 服务器不可用
   - 服务维护中
   - 连接数超限

## 排查步骤

### 1. 检查网络连通性

```bash
# 检查主机连通性
ping your_instance_id_here.mqtt.aliyuncs.com

# 检查端口连通性
telnet your_instance_id_here.mqtt.aliyuncs.com 8883

# 或者使用nc命令
nc -zv your_instance_id_here.mqtt.aliyuncs.com 8883
```

### 2. 检查SSL/TLS支持

```bash
# 检查SSL连接
openssl s_client -connect your_instance_id_here.mqtt.aliyuncs.com:8883 -servername your_instance_id_here.mqtt.aliyuncs.com
```

### 3. 验证认证信息

检查以下配置是否正确：

```yaml
mqtt:
  access-key: your_access_key_here
  secret-key: your_secret_key_here
  instance:
    id: your_instance_id_here
  group:
    id: GID_QUITTR
```

### 4. 使用诊断工具

应用启动时会自动执行诊断，或者手动调用：

```bash
# 查看控制台日志中的诊断信息
# 或者调用诊断API
curl -X POST http://localhost:8080/api/mqtt/check
```

## 阿里云MQTT认证方式

### 方式1: 直接认证
```
用户名: AccessKey
密码: SecretKey
```

### 方式2: 签名认证
```
用户名: AccessKey
密码: HMAC-SHA1(ClientId&InstanceId, SecretKey)
```

### 方式3: 简化认证
```
用户名: AccessKey
密码: (空)
```

## 配置建议

### 1. 增加重试机制
```yaml
mqtt:
  connection:
    retry:
      count: 5
      delay: 10000  # 10秒
```

### 2. 调整超时设置
```yaml
mqtt:
  connection:
    timeout: 60  # 60秒
  keep:
    alive:
      interval: 120  # 120秒
```

### 3. 启用自动重连
```yaml
mqtt:
  auto:
    reconnect: true
```

## 测试连接

### 1. 使用MQTT测试客户端
```bash
# 安装mosquitto客户端
brew install mosquitto  # macOS
apt-get install mosquitto-clients  # Ubuntu

# 测试连接
mosquitto_pub -h your_instance_id_here.mqtt.aliyuncs.com -p 8883 -u "AccessKey" -P "SecretKey" -t "test" -m "hello"
```

### 2. 使用应用内测试API
```bash
# 测试连接状态
curl http://localhost:8080/api/mqtt/status

# 测试消息发布
curl -X POST "http://localhost:8080/api/mqtt/test-publish?message=test"
```

## 常见解决方案

### 1. 网络问题
- 检查防火墙设置
- 确认代理配置
- 尝试不同的网络环境

### 2. SSL问题
- 更新Java版本
- 检查SSL证书
- 尝试TCP连接（非SSL）

### 3. 认证问题
- 验证AccessKey/SecretKey
- 检查阿里云MQTT实例状态
- 确认权限配置

### 4. 配置问题
- 检查ClientId格式
- 确认InstanceId
- 验证GroupId

## 日志分析

应用启动时会输出详细的MQTT连接日志，包括：

1. **配置信息**: 显示所有MQTT配置参数
2. **连接过程**: 详细的连接步骤和耗时
3. **认证信息**: 认证方式和结果
4. **错误详情**: 详细的错误信息和堆栈跟踪
5. **重试过程**: 连接重试的次数和结果

## 联系支持

如果问题仍然存在，请：

1. 收集完整的错误日志
2. 提供网络环境信息
3. 确认阿里云MQTT实例状态
4. 联系阿里云技术支持
