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
### StoryAI 独立 R2 模板预览

部署本版本后，模板列表和版本详情增加可空的 `previewMp4` 字段。原 `preview`、
`full_mv`、`browser_parity_reference` 及 Stream 上传协议保持不变。
新资源写入现有 `template_media` 表的独立 `preview_mp4` 角色，无需手工修改表结构。
只有校验通过、且源视频身份仍匹配的 MP4 才返回给前端。

后端环境变量：

| 变量 | 配置 |
| --- | --- |
| `MUSIC_MV_PREVIEW_R2_ENDPOINT` | 对应账号的 `https://账号ID.r2.cloudflarestorage.com`，不含桶名 |
| `MUSIC_MV_PREVIEW_R2_BUCKET` | `video-previews` |
| `MUSIC_MV_PREVIEW_R2_PUBLIC_BASE_URL` | `https://video.musicvideomaker.net` |
| `MUSIC_MV_PREVIEW_R2_ACCESS_KEY` | 对该桶有读写权限的 Access Key ID |
| `MUSIC_MV_PREVIEW_R2_SECRET_KEY` | 对应 Secret Access Key |

未设置专用 endpoint/凭据时兼容现有 `cloudflare.r2` 配置；生产部署应核对实际账号和
桶权限。不要修改原 `music-mv.r2.bucket` 或 `cloudflare.r2.bucket`。
自定义域名需已激活，桶 CORS 允许网站域名的 GET/HEAD、Range，并暴露 Content-Range、
Content-Length、Accept-Ranges。MP4 上传带一年 immutable 缓存头，地址包含内容摘要。

内部上传接口使用现有 `X-Music-Mv-Template-Sync-Token` 鉴权：
`/internal/music-mv/v1/templates/{templateId}/versions/{versionId}/preview-mp4/`
下的 `source`（GET）、`upload-session`（POST）、`complete`（POST）。
先部署后端再部署前端，最后更新模板管理服务。历史补齐使用模板管理仓库的
`scripts/backfill-template-previews.py`；部署本身不会自动批量迁移历史数据。
