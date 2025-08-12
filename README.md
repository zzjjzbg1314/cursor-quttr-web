# Cursor Quitter Web

## 安全配置说明

⚠️ **重要安全提醒**：本项目包含敏感配置信息，请按照以下步骤安全配置：

### 1. 环境变量配置

复制 `env.example` 文件为 `.env` 文件：

```bash
cp env.example .env
```

然后编辑 `.env` 文件，填入你的真实配置信息：

```bash
# 阿里云MQ4IoT配置
MQTT_INSTANCE_ID=your_real_instance_id
MQTT_ENDPOINT=your_real_mqtt_endpoint
MQTT_ACCESS_KEY=your_real_access_key
MQTT_SECRET_KEY=your_real_secret_key
MQTT_GROUP_ID=GID_QUITTR
MQTT_DEVICE_ID=your_device_id
MQTT_PARENT_TOPIC=CHAT_OFFICIAL_GROUP
MQTT_QOS_LEVEL=0
MQTT_TCP_PORT=1883

# 微信配置
WECHAT_APP_ID=your_real_wechat_app_id
WECHAT_APP_SECRET=your_real_wechat_app_secret
```

### 2. 安全注意事项

- **永远不要**将真实的访问密钥提交到 Git 仓库
- `.env` 文件已被 `.gitignore` 忽略，不会被提交
- 如果意外提交了敏感信息，请立即：
  1. 在阿里云控制台轮换访问密钥
  2. 在微信开发者平台重置应用密钥
  3. 联系 GitHub 支持移除敏感信息

### 3. 运行项目

```bash
# 安装依赖
mvn install

# 运行项目
mvn spring-boot:run
```

### 4. 获取帮助

如果遇到安全问题，请参考：
- [GitHub 密钥扫描文档](https://docs.github.com/code-security/secret-scanning)
- [阿里云访问密钥管理](https://help.aliyun.com/document_detail/53045.html)
- [微信开发者平台](https://developers.weixin.qq.com/)