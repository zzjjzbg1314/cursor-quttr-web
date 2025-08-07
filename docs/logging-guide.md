# 日志使用指南

## 概述

本项目使用Logback作为日志框架，提供了完整的日志中心功能，包括日志分类、轮转、自动清理和动态调整日志级别。

## 日志文件结构

```
logs/
├── system.log              # 系统日志（所有INFO级别以上的日志）
├── error.log               # 错误日志（仅ERROR级别）
├── wechat.log              # 微信相关日志
├── sql.log                 # SQL日志
└── system.2024-01-01.0.log # 历史日志文件（按日期轮转）
```

## 日志级别

### DEBUG
- 方法调用和退出
- 参数和返回值
- 详细的调试信息

### INFO
- 业务操作记录
- 系统状态信息
- 重要的业务事件

### WARN
- 配置问题
- 性能警告
- 非致命错误

### ERROR
- 系统异常
- 业务错误
- 致命错误

## 日志配置

### 开发环境配置

```yaml
# application-dev.yml
logging:
  level:
    root: INFO
    com.example.cursorquitterweb: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    com.alibaba.druid: DEBUG
```

### 生产环境配置

```yaml
# application-prod.yml
logging:
  level:
    root: WARN
    com.example.cursorquitterweb: INFO
    org.hibernate.SQL: OFF
    org.hibernate.type.descriptor.sql.BasicBinder: OFF
    com.alibaba.druid: INFO
```

## 日志使用示例

### 1. 在类中使用日志

```java
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;

@Service
public class UserService {
    
    private static final Logger logger = LogUtil.getLogger(UserService.class);
    
    public User createUser(UserRequest request) {
        LogUtil.logInfo(logger, "开始创建用户: {}", request.getUsername());
        
        try {
            // 业务逻辑
            User user = new User();
            user.setUsername(request.getUsername());
            
            LogUtil.logInfo(logger, "用户创建成功: {}", user.getId());
            return user;
            
        } catch (Exception e) {
            LogUtil.logError(logger, "用户创建失败", e);
            throw e;
        }
    }
}
```

### 2. 使用日志工具类

```java
// 记录方法进入
LogUtil.logMethodEntry(logger, "createUser", request);

// 记录信息日志
LogUtil.logInfo(logger, "用户登录成功: {}", userId);

// 记录警告日志
LogUtil.logWarn(logger, "用户 {} 登录失败次数过多", username);

// 记录错误日志
LogUtil.logError(logger, "数据库连接失败", e);

// 记录调试日志
LogUtil.logDebug(logger, "查询参数: {}", params);
```

### 3. 使用AOP自动记录日志

项目已经配置了AOP切面，会自动记录Controller和Service方法的调用日志：

```java
@RestController
public class UserController {
    
    // 这个方法会自动记录：
    // 1. 方法进入日志（包含参数）
    // 2. 方法退出日志（包含返回值和耗时）
    // 3. 异常日志（如果发生异常）
    @PostMapping("/users")
    public ResponseEntity<User> createUser(@RequestBody UserRequest request) {
        // 业务逻辑
        return ResponseEntity.ok(user);
    }
}
```

## 日志管理API

### 获取当前日志级别

```bash
curl -X GET http://localhost:8080/api/logs/level
```

响应：
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

### 设置日志级别

```bash
# 设置根日志级别为DEBUG
curl -X POST "http://localhost:8080/api/logs/level?loggerName=ROOT&level=DEBUG"

# 设置特定包的日志级别
curl -X POST "http://localhost:8080/api/logs/level?loggerName=com.example.cursorquitterweb&level=DEBUG"
```

### 获取日志文件信息

```bash
curl -X GET http://localhost:8080/api/logs/files
```

响应：
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

## 日志轮转和清理

### 轮转策略

- **按时间轮转**: 每天轮转一次
- **按大小轮转**: 单个文件最大100MB
- **保留策略**: 保留最近7天的日志文件

### 自动清理

- **清理时间**: 每天凌晨2点
- **清理策略**: 删除超过7天的日志文件
- **清理日志**: 清理过程会记录到系统日志中

## 最佳实践

### 1. 日志级别选择

- **DEBUG**: 用于开发调试，生产环境通常关闭
- **INFO**: 记录重要的业务操作和系统状态
- **WARN**: 记录可能的问题和警告
- **ERROR**: 记录错误和异常

### 2. 日志内容

- 包含足够的上下文信息
- 避免记录敏感信息（密码、token等）
- 使用占位符而不是字符串拼接

### 3. 性能考虑

- 使用条件日志（isDebugEnabled()）
- 避免在日志中执行复杂操作
- 合理设置日志级别

### 4. 日志格式

项目使用统一的日志格式：

```
2024-01-01 12:00:00.123 [http-nio-8080-exec-1] INFO  c.e.c.controller.UserController - 用户登录成功: 12345
```

格式说明：
- 时间戳：精确到毫秒
- 线程名：方括号包围
- 日志级别：5字符对齐
- 类名：50字符对齐
- 消息内容

## 故障排查

### 1. 日志文件不存在

检查配置：
```yaml
logging:
  config: classpath:logback-spring.xml
```

### 2. 日志级别不生效

检查环境配置：
```yaml
spring:
  profiles:
    active: dev
```

### 3. 日志文件过大

检查轮转配置：
```xml
<MaxFileSize>100MB</MaxFileSize>
<MaxHistory>7</MaxHistory>
```

### 4. 日志清理不工作

检查定时任务配置：
```java
@Scheduled(cron = "0 0 2 * * ?")
```

## 监控和告警

### 1. 日志监控

- 监控ERROR日志数量
- 监控日志文件大小
- 监控日志清理任务执行情况

### 2. 告警配置

- 错误日志数量超过阈值时告警
- 日志文件大小超过阈值时告警
- 日志清理任务失败时告警 