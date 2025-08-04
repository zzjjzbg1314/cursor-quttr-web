# Cursor Quitter Web - Spring Boot 项目

这是一个基于Spring Boot 2.7.18和Java 8的Web应用程序。

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
│   │   │       └── controller/
│   │   │           └── HelloController.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-dev.yml
│   └── test/
│       └── java/
│           └── com/example/cursorquitterweb/
│               ├── CursorQuitterWebApplicationTests.java
│               └── controller/
│                   └── HelloControllerTest.java
├── pom.xml
└── README.md
```

## 快速开始

### 前提条件

- Java 8 或更高版本
- Maven 3.6 或更高版本

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

## 配置说明

### 开发环境配置
- 端口: 8080
- 日志级别: DEBUG
- 包含H2数据库配置（可选）

### 生产环境配置
- 端口: 8080
- 日志级别: INFO

## 开发说明

1. 项目使用Spring Boot 2.7.18，兼容Java 8
2. 包含Spring Boot DevTools，支持热重载
3. 集成了Spring Boot Actuator用于监控
4. 包含完整的测试用例

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