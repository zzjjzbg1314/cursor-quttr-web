# API 使用示例

## 微信登录接口

### 1. 微信登录

**请求URL**: `POST /api/wechat/login`

**请求头**:
```
Content-Type: application/json
```

**请求体**:
```json
{
  "code": "微信授权码"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    "openId": "oWx1234567890abcdef",
    "nickname": "微信用户",
    "headimgurl": "",
    "unionid": "oWx1234567890abcdef"
  }
}
```

**错误响应示例**:
```json
{
  "success": false,
  "message": "微信登录失败: 获取微信session失败",
  "data": null
}
```

### 2. 微信服务健康检查

**请求URL**: `GET /api/wechat/health`

**响应示例**:
```json
{
  "success": true,
  "message": "操作成功",
  "data": "微信服务正常"
}
```

## 用户相关接口

### 更新用户挑战开始时间

**接口地址：** `PUT /api/users/{id}/challenge-start-time`

**请求参数：**
- `id`: 用户ID字符串 (路径参数)
- `newStartTime`: 新的挑战开始时间字符串 (请求体，支持ISO 8601格式)

**请求示例：**
```bash
curl -X PUT "http://localhost:8080/api/users/123e4567-e89b-12d3-a456-426614174000/challenge-start-time" \
  -H "Content-Type: application/json" \
  -d '{
    "newStartTime": "2024-01-15T10:00:00+08:00"
  }'
```

**响应示例：**
```json
{
  "success": true,
  "message": "挑战开始时间更新成功",
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "nickname": "用户昵称",
    "challengeResetTime": "2024-01-15T10:00:00+08:00",
    "bestRecord": 30,
    "registrationTime": "2024-01-01T00:00:00+08:00"
  }
}
```

**说明：**
- 此接口用于修改用户的挑战开始时间
- 时间格式使用ISO 8601标准，支持时区偏移
- 更新成功后，用户的挑战计时将从新的开始时间重新计算
- 只有存在的用户ID才能更新，否则会返回错误信息

### 获取用户挑战开始时间

**接口地址：** `GET /api/users/{id}/challenge-start-time`

**请求参数：**
- `id`: 用户ID (路径参数)

**请求示例：**
```bash
curl -X GET "http://localhost:8080/api/users/123e4567-e89b-12d3-a456-426614174000/challenge-start-time"
```

**响应示例：**
```json
{
  "success": true,
  "message": "获取挑战开始时间成功",
  "data": "2024-01-15T10:00:00+08:00"
}
```

**说明：**
- 此接口用于查询用户当前的挑战开始时间
- 返回的时间格式为ISO 8601标准，包含时区信息
- 可用于前端显示用户的挑战计时状态

## 测试步骤

### 1. 获取微信授权码

1. 在微信小程序中调用 `wx.login()` 获取授权码
2. 授权码有效期5分钟，需要及时使用

### 2. 调用登录接口

使用Postman或其他HTTP客户端工具：

1. 设置请求方法为 `POST`
2. 设置URL为 `http://localhost:8080/api/wechat/login`
3. 设置Content-Type为 `application/json`
4. 在请求体中添加：
   ```json
   {
     "code": "你的微信授权码"
   }
   ```

### 3. 处理响应

- 如果 `success` 为 `true`，登录成功
- 如果 `success` 为 `false`，查看 `message` 字段获取错误信息

## 注意事项

1. **配置要求**: 需要先配置微信小程序的AppID和AppSecret
2. **环境变量**: 建议使用环境变量配置敏感信息
3. **错误处理**: 接口会返回详细的错误信息
4. **日志记录**: 所有操作都会记录到日志中

## 开发环境配置

在 `application-dev.yml` 中添加：

```yaml
wechat:
  app-id: your_test_app_id
  app-secret: your_test_app_secret
```

## 生产环境配置

使用环境变量：

```bash
export WECHAT_APP_ID=your_production_app_id
export WECHAT_APP_SECRET=your_production_app_secret
``` 