# Cursor Quitter Web - Spring Boot 项目

这是一个基于Spring Boot 2.7.18和Java 8的Web应用程序，支持微信登录功能。

## 技术栈

- **Java版本**: 1.8
- **Spring Boot版本**: 2.7.18
- **构建工具**: Maven
- **Web框架**: Spring Boot Web Starter
- **测试框架**: Spring Boot Test

## 项目结构

```
cursor-quitter-web/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/cursorquitterweb/
│   │   │       ├── CursorQuitterWebApplication.java
│   │   │       ├── config/
│   │   │       │   ├── WechatConfig.java
│   │   │       │   └── WebConfig.java
│   │   │       ├── controller/
│   │   │       │   ├── HelloController.java
│   │   │       │   └── WechatController.java
│   │   │       ├── dto/
│   │   │       │   ├── ApiResponse.java
│   │   │       │   ├── WechatLoginRequest.java
│   │   │       │   └── WechatUserInfo.java
│   │   │       └── service/
│   │   │           ├── WechatService.java
│   │   │           └── impl/
│   │   │               └── WechatServiceImpl.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-dev.yml
│   └── test/
│       └── java/
│           └── com/example/cursorquitterweb/
│               ├── CursorQuitterWebApplicationTests.java
│               ├── controller/
│               │   ├── HelloControllerTest.java
│               │   └── WechatControllerTest.java
├── pom.xml
└── README.md
```

## 快速开始

### 前提条件

- Java 8 或更高版本
- Maven 3.6 或更高版本
- 微信小程序AppID和AppSecret

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

- **主页**: http://localhost:8080
- **Hello接口**: http://localhost:8080/api/hello
- **健康检查**: http://localhost:8080/api/health
- **微信登录**: http://localhost:8080/api/wechat/login
- **微信健康检查**: http://localhost:8080/api/wechat/health
- **Actuator健康检查**: http://localhost:8080/actuator/health

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

### 配置说明

#### 开发环境配置
- 端口: 8080
- 日志级别: DEBUG
- 包含H2数据库配置（可选）

#### 生产环境配置
- 端口: 8080
- 日志级别: INFO

## 开发说明

1. 项目使用Spring Boot 2.7.18，兼容Java 8
2. 包含Spring Boot DevTools，支持热重载
3. 集成了Spring Boot Actuator用于监控
4. 包含完整的测试用例
5. 支持微信小程序登录功能

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

## 许可证

本项目采用MIT许可证。 