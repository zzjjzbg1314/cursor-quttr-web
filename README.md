# Cursor Quitter Web - Spring Boot 项目

这是一个基于Spring Boot 2.7.18和Java 8的Web应用程序，支持微信登录功能、完整的日志中心和HTTPS安全访问。

## 技术栈

- **Java版本**: 1.8
- **Spring Boot版本**: 2.7.18
- **构建工具**: Maven
- **Web框架**: Spring Boot Web Starter
- **测试框架**: Spring Boot Test
- **日志框架**: Logback + SLF4J
- **AOP框架**: Spring AOP
- **安全协议**: HTTPS/SSL

## 项目结构

```
cursor-quitter-web/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/cursorquitterweb/
│   │   │       ├── CursorQuitterWebApplication.java
│   │   │       ├── aspect/
│   │   │       │   └── LogAspect.java
│   │   │       ├── config/
│   │   │       │   ├── WechatConfig.java
│   │   │       │   ├── WebConfig.java
│   │   │       │   └── SslConfig.java
│   │   │       ├── controller/
│   │   │       │   ├── HelloController.java
│   │   │       │   ├── WechatController.java
│   │   │       │   ├── LogController.java
│   │   │       │   └── SslController.java
│   │   │       ├── dto/
│   │   │       │   ├── ApiResponse.java
│   │   │       │   ├── WechatLoginRequest.java
│   │   │       │   └── WechatUserInfo.java
│   │   │       ├── service/
│   │   │       │   ├── WechatService.java
│   │   │       │   └── impl/
│   │   │       │       └── WechatServiceImpl.java
│   │   │       ├── task/
│   │   │       │   └── LogCleanupTask.java
│   │   │       └── util/
│   │   │           └── LogUtil.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── logback-spring.xml
│   │       └── kejiapi.cn.jks
│   └── test/
│       └── java/
│           └── com/example/cursorquitterweb/
│               ├── CursorQuitterWebApplicationTests.java
│               ├── controller/
│               │   ├── HelloControllerTest.java
│               │   └── WechatControllerTest.java
│               ├── config/
│               │   └── SslConfigTest.java
│               └── util/
│                   └── LogUtilTest.java
├── logs/                                    # 日志文件目录
├── docs/
│   ├── api-examples.md
│   ├── logging-guide.md
│   └── configuration-guide.md
├── pom.xml
├── README.md
└── start.sh
```

## 快速开始

### 前提条件

- Java 8 或更高版本
- Maven 3.6 或更高版本
- 微信小程序AppID和AppSecret
- SSL证书文件（可选）

### 配置SSL证书

1. **准备SSL证书**
   - 将JKS证书文件放在 `src/main/resources/` 目录下
   - 默认证书文件名：`kejiapi.cn.jks`

2. **配置环境变量**
   ```bash
   export SSL_KEY_STORE_PASSWORD=your_keystore_password
   export SSL_KEY_ALIAS=kejiapi.cn
   ```

3. **或者直接在application.yml中配置**
   ```yaml
   server:
     ssl:
       enabled: true
       key-store: classpath:kejiapi.cn.jks
       key-store-password: your_keystore_password
       key-store-type: JKS
       key-alias: kejiapi.cn
   ```

### 配置微信登录

1. **获取微信小程序AppID和AppSecret**
   - 登录微信公众平台
   - 创建小程序或使用现有小程序
   - 获取AppID和AppSecret

2. **配置环境变量**
   ```bash
   export WECHAT_APP_ID=your_wechat_app_id
   export WECHAT_APP_SECRET=your_wechat_app_secret
   ```

3. **或者直接在application.yml中配置**
   ```yaml
   wechat:
     app-id: your_wechat_app_id
     app-secret: your_wechat_app_secret
   ```

### 运行应用

1. **编译项目**:
   ```bash
   mvn clean compile
   ```

2. **运行应用**:
   ```bash
   mvn spring-boot:run
   ```

3. **打包应用**:
   ```bash
   mvn clean package
   ```

4. **运行测试**:
   ```bash
   mvn test
   ```

### 访问应用

应用启动后，可以通过以下URL访问：

#### HTTP模式（默认）
- **主页**: http://localhost:8080
- **Hello接口**: http://localhost:8080/api/hello
- **健康检查**: http://localhost:8080/api/health
- **微信登录**: http://localhost:8080/api/wechat/login
- **微信健康检查**: http://localhost:8080/api/wechat/health
- **日志管理**: http://localhost:8080/api/logs/level
- **SSL信息**: http://localhost:8080/api/ssl/info
- **Actuator健康检查**: http://localhost:8080/actuator/health

#### HTTPS模式（需要配置SSL证书）
- **主页**: https://localhost:8080
- **Hello接口**: https://localhost:8080/api/hello
- **健康检查**: https://localhost:8080/api/health
- **微信登录**: https://localhost:8080/api/wechat/login
- **微信健康检查**: https://localhost:8080/api/wechat/health
- **日志管理**: https://localhost:8080/api/logs/level
- **SSL信息**: https://localhost:8080/api/ssl/info
- **Actuator健康检查**: https://localhost:8080/actuator/health

## HTTPS配置

### SSL证书管理

项目支持JKS格式的SSL证书，提供以下功能：

1. **证书验证**
   - 自动验证证书文件是否存在
   - 验证证书别名是否正确
   - 检查证书有效期

2. **HTTP到HTTPS重定向**
   - 启用SSL时自动将HTTP请求重定向到HTTPS
   - 支持80端口到443端口的重定向

3. **证书信息查询**
   - 查看证书详细信息
   - 检查证书有效期
   - 监控证书状态

### SSL配置示例

```yaml
server:
  port: 8080
  ssl:
    enabled: ${SSL_ENABLED:false}  # 可选择是否启用HTTPS
    key-store: classpath:kejiapi.cn.jks
    key-store-password: ${SSL_KEY_STORE_PASSWORD:your_keystore_password}
    key-store-type: JKS
    key-alias: ${SSL_KEY_ALIAS:kejiapi.cn}
```

### SSL管理API

#### 获取SSL证书信息
```bash
GET /api/ssl/info
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "enabled": true,
    "keyStorePath": "classpath:kejiapi.cn.jks",
    "keyAlias": "kejiapi.cn",
    "keyStoreExists": true,
    "keyStoreSize": 1,
    "certificate": {
      "subject": "CN=kejiapi.cn",
      "issuer": "CN=Let's Encrypt Authority X3",
      "serialNumber": "1234567890",
      "notBefore": "2024-01-01T00:00:00",
      "notAfter": "2024-04-01T00:00:00"
    }
  }
}
```

#### 检查SSL状态
```bash
GET /api/ssl/status
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "enabled": true,
    "secure": true,
    "protocol": "HTTPS"
  }
}
```

## 日志中心

### 日志文件结构

项目使用Logback作为日志框架，日志文件按功能分类存储：

```
logs/
├── system.log              # 系统日志
├── error.log               # 错误日志
├── wechat.log              # 微信相关日志
├── sql.log                 # SQL日志
└── system.2024-01-01.0.log # 历史日志文件
```

### 日志级别

- **DEBUG**: 调试信息，包含方法调用、参数等详细信息
- **INFO**: 一般信息，如业务操作、系统状态等
- **WARN**: 警告信息，如配置问题、性能警告等
- **ERROR**: 错误信息，如异常、系统错误等

### 日志保留策略

- **保留时间**: 7天
- **文件大小**: 单个日志文件最大100MB
- **清理时间**: 每天凌晨2点自动清理过期日志

### 日志管理API

#### 获取当前日志级别
```bash
GET /api/logs/level
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "root": "INFO",
    "com.example.cursorquitterweb": "DEBUG",
    "org.springframework.web": "INFO"
  }
}
```

#### 设置日志级别
```bash
POST /api/logs/level?loggerName=com.example.cursorquitterweb&level=DEBUG
```

**响应示例**:
```json
{
  "success": true,
  "message": "设置日志级别成功"
}
```

#### 获取日志文件信息
```bash
GET /api/logs/files
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "logHome": "logs/",
    "files": [
      "system.log",
      "error.log",
      "wechat.log",
      "sql.log"
    ]
  }
}
```

## API接口

### GET /api/hello
返回欢迎信息

**响应示例**:
```json
"Hello, Spring Boot! 欢迎使用Java 8版本的Spring Boot项目"
```

### GET /api/health
返回应用健康状态

**响应示例**:
```json
"应用运行正常"
```

### POST /api/wechat/login
微信登录接口

**请求示例**:
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
    "openId": "用户的openid",
    "nickname": "微信用户",
    "headimgurl": "",
    "unionid": "用户的unionid"
  }
}
```

### GET /api/wechat/health
微信服务健康检查

**响应示例**:
```json
{
  "success": true,
  "message": "操作成功",
  "data": "微信服务正常"
}
```

## 微信登录实现说明

### 登录流程

1. **前端获取授权码**
   - 前端调用微信小程序API获取授权码
   - 授权码有效期5分钟

2. **后端处理登录**
   - 接收前端传来的授权码
   - 调用微信API获取openid和session_key
   - 返回用户信息给前端

3. **用户信息**
   - openId: 用户的唯一标识
   - unionid: 用户在同一开放平台下的唯一标识（可选）
   - nickname: 用户昵称（默认为"微信用户"）
   - headimgurl: 用户头像URL（默认为空）

## 开发说明

1. 项目使用Spring Boot 2.7.18，兼容Java 8
2. 包含Spring Boot DevTools，支持热重载
3. 集成了Spring Boot Actuator用于监控
4. 包含完整的测试用例
5. 支持微信小程序登录功能
6. 完整的日志中心，支持日志轮转和自动清理
7. AOP切面，自动记录方法调用日志
8. HTTPS安全访问，支持SSL证书管理

## 构建和部署

### 本地开发
```bash
mvn spring-boot:run
```

### 打包部署
```bash
mvn clean package
java -jar target/cursor-quitter-web-0.0.1-SNAPSHOT.jar
```

### 生产环境部署

1. **设置环境变量**
   ```bash
   export SSL_KEY_STORE_PASSWORD=your_keystore_password
   export SSL_KEY_ALIAS=kejiapi.cn
   export WECHAT_APP_ID=your_wechat_app_id
   export WECHAT_APP_SECRET=your_wechat_app_secret
   ```

2. **启动应用**
   ```bash
   java -jar target/cursor-quitter-web-0.0.1-SNAPSHOT.jar
   ```

## 许可证

本项目采用MIT许可证。 