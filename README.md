# Cursor Quitter Web - Spring Boot 项目

这是一个基于Spring Boot 2.7.18和Java 8的Web应用程序，支持微信登录功能、完整的日志中心、HTTPS安全访问和阿里云PostgreSQL数据库集成。

## 技术栈

- **Java版本**: 1.8
- **Spring Boot版本**: 2.7.18
- **构建工具**: Maven
- **Web框架**: Spring Boot Web Starter
- **数据库**: 阿里云PostgreSQL
- **ORM框架**: Spring Data JPA
- **连接池**: Druid
- **数据库迁移**: Flyway
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
│   │   │       │   ├── SslConfig.java
│   │   │       │   └── DatabaseConfig.java
│   │   │       ├── controller/
│   │   │       │   ├── HelloController.java
│   │   │       │   ├── WechatController.java
│   │   │       │   ├── LogController.java
│   │   │       │   ├── SslController.java
│   │   │       │   └── UserController.java
│   │   │       ├── entity/
│   │   │       │   └── User.java
│   │   │       ├── repository/
│   │   │       │   └── UserRepository.java
│   │   │       ├── service/
│   │   │       │   ├── WechatService.java
│   │   │       │   ├── UserService.java
│   │   │       │   └── impl/
│   │   │       │       ├── WechatServiceImpl.java
│   │   │       │       └── UserServiceImpl.java
│   │   │       ├── dto/
│   │   │       │   ├── ApiResponse.java
│   │   │       │   ├── WechatLoginRequest.java
│   │   │       │   └── WechatUserInfo.java
│   │   │       ├── task/
│   │   │       │   └── LogCleanupTask.java
│   │   │       └── util/
│   │   │           └── LogUtil.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── logback-spring.xml
│   │       ├── kejiapi.cn.jks
│   │       └── db/
│   │           └── migration/
│   │               └── V1__Create_users_table.sql
│   └── test/
│       ├── java/
│       │   └── com/example/cursorquitterweb/
│       │       ├── config/
│       │       │   └── SslConfigTest.java
│       │       ├── controller/
│       │       │   ├── HelloControllerTest.java
│       │       │   └── WechatControllerTest.java
│       │       ├── repository/
│       │       │   └── UserRepositoryTest.java
│       │       ├── CursorQuitterWebApplicationTests.java
│       │       └── util/
│       │           └── LogUtilTest.java
│       └── resources/
│           └── application-test.yml
├── docs/
│   ├── api-examples.md
│   ├── configuration-guide.md
│   ├── logging-guide.md
│   └── database-integration.md
├── logs/
├── pom.xml
├── README.md
└── start.sh
```

## 快速开始

### 1. 环境要求

- Java 8+
- Maven 3.6+
- 阿里云PostgreSQL数据库

### 2. 配置数据库

1. 在阿里云RDS控制台创建PostgreSQL实例
2. 配置数据库连接信息
3. 设置环境变量：

```bash
export DATABASE_URL=jdbc:postgresql://your-host:5432/cursor_quitter_web
export DATABASE_USERNAME=your-username
export DATABASE_PASSWORD=your-password
```

### 3. 运行应用

```bash
# 克隆项目
git clone <repository-url>
cd cursor-quitter-web

# 编译项目
mvn clean compile

# 运行应用
mvn spring-boot:run
```

### 4. 访问应用

- 应用地址：https://localhost:8080
- 健康检查：https://localhost:8080/actuator/health
- Druid监控：https://localhost:8080/druid

## 主要功能

### 1. 用户管理

- 用户注册和登录
- 微信登录集成
- 用户信息管理
- 用户搜索和统计

### 2. 数据库集成

- 阿里云PostgreSQL支持
- 自动数据库迁移
- 连接池监控
- 数据备份和恢复

### 3. 日志系统

- 完整的日志记录
- 日志分类和轮转
- 动态日志级别调整
- 日志文件管理

### 4. 安全特性

- HTTPS/SSL支持
- 数据库连接加密
- 用户权限管理
- 安全配置

## API接口

### 用户管理接口

- `GET /api/users/{id}` - 获取用户信息
- `GET /api/users/wechat/{openid}` - 根据微信openid获取用户
- `POST /api/users` - 创建用户
- `PUT /api/users/{id}` - 更新用户信息
- `DELETE /api/users/{id}` - 删除用户
- `GET /api/users/search?nickname=关键词` - 搜索用户
- `GET /api/users/city/{city}` - 根据城市查询用户
- `GET /api/users/province/{province}` - 根据省份查询用户
- `GET /api/users/registration-time` - 根据注册时间范围查询用户
- `POST /api/users/{id}/reset-challenge` - 重置用户挑战时间
- `GET /api/users/stats/registration` - 获取注册统计信息
- `GET /api/users/stats/gender` - 获取性别统计信息

### 微信接口

- `POST /api/wechat/login` - 微信登录
- `GET /api/wechat/health` - 微信服务健康检查

### 系统接口

- `GET /` - 首页信息
- `GET /api/hello` - 欢迎信息
- `GET /api/health` - 健康检查
- `GET /api/ssl/info` - SSL证书信息
- `GET /api/ssl/status` - SSL状态
- `GET /api/logs/level` - 日志级别
- `GET /api/logs/files` - 日志文件信息

## 配置说明

### 数据库配置

```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/cursor_quitter_web}
    username: ${DATABASE_USERNAME:postgres}
    password: ${DATABASE_PASSWORD:password}
    
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: ${SHOW_SQL:false}
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

### 微信配置

```yaml
wechat:
  app-id: ${WECHAT_APP_ID:your-app-id}
  app-secret: ${WECHAT_APP_SECRET:your-app-secret}
```

### SSL配置

```yaml
server:
  ssl:
    enabled: ${SSL_ENABLED:true}
    key-store: classpath:kejiapi.cn.jks
    key-store-password: ${SSL_KEY_STORE_PASSWORD:your-password}
```

## 开发指南

### 1. 添加新实体

1. 在`entity`包下创建实体类
2. 在`repository`包下创建Repository接口
3. 在`service`包下创建Service接口和实现
4. 在`controller`包下创建Controller
5. 创建数据库迁移脚本

### 2. 数据库迁移

1. 在`src/main/resources/db/migration/`目录下创建迁移脚本
2. 脚本命名格式：`V{version}__{description}.sql`
3. 重启应用或运行`mvn flyway:migrate`

### 3. 测试

```bash
# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=UserRepositoryTest
```

## 部署

### 1. 打包

```bash
mvn clean package
```

### 2. 运行

```bash
java -jar target/cursor-quitter-web-0.0.1-SNAPSHOT.jar
```

### 3. 生产环境配置

```bash
export DATABASE_URL=jdbc:postgresql://prod-host:5432/cursor_quitter_web
export DATABASE_USERNAME=prod_user
export DATABASE_PASSWORD=prod_password
export SSL_ENABLED=true
export SHOW_SQL=false
```

## 监控和维护

### 1. 健康检查

- 应用健康：`/actuator/health`
- 数据库健康：`/actuator/health`
- 微信服务健康：`/api/wechat/health`

### 2. 日志监控

- 日志级别调整：`/api/logs/level`
- 日志文件信息：`/api/logs/files`
- 日志文件位置：`logs/`目录

### 3. 数据库监控

- Druid监控面板：`/druid`
- 连接池状态：`/actuator/metrics`
- 数据库性能：PostgreSQL监控工具

## 常见问题

### 1. 数据库连接问题

- 检查网络连接
- 确认白名单配置
- 验证数据库凭据

### 2. SSL证书问题

- 检查证书文件路径
- 验证证书密码
- 确认证书别名

### 3. 微信登录问题

- 检查AppID和AppSecret
- 确认微信小程序配置
- 验证网络连接

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 创建Pull Request

## 许可证

本项目采用MIT许可证。 

echo 'export MQTT_ACCESS_KEY="your-access-key-id"' >> ~/.bashrc
echo 'export MQTT_SECRET_KEY="your-access-key-secret"' >> ~/.bashrc
source ~/.bashrc