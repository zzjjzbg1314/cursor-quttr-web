# 配置指南

## 概述

本项目使用单一的 `application.yml` 配置文件，支持通过环境变量进行配置覆盖。

## 配置文件结构

```
src/main/resources/
├── application.yml          # 主配置文件
├── logback-spring.xml       # 日志配置
└── kejiapi.cn.jks          # SSL证书文件
```

## 主要配置项

### 服务器配置

```yaml
server:
  port: 8080                    # 服务器端口
  ssl:
    enabled: ${SSL_ENABLED:false}           # 是否启用HTTPS
    key-store: classpath:kejiapi.cn.jks     # SSL证书文件路径
    key-store-password: ${SSL_KEY_STORE_PASSWORD:your_keystore_password}  # 证书密码
    key-store-type: JKS                     # 证书类型
    key-alias: ${SSL_KEY_ALIAS:kejiapi.cn}  # 证书别名
```

### 数据库配置

```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/cursor_quitter_web}
    username: ${DATABASE_USERNAME:postgres}
    password: ${DATABASE_PASSWORD:password}
    
    # Druid连接池配置
    druid:
      initial-size: 5           # 初始连接数
      min-idle: 5               # 最小空闲连接数
      max-active: 20            # 最大活跃连接数
      max-wait: 60000           # 最大等待时间
```

### JPA配置

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update          # 数据库表结构更新策略
    show-sql: true              # 显示SQL语句
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        use_sql_comments: true
```

### 微信配置

```yaml
wechat:
  app-id: ${WECHAT_APP_ID:your_wechat_app_id}
  app-secret: ${WECHAT_APP_SECRET:your_wechat_app_secret}
  code2-session-url: https://api.weixin.qq.com/sns/jscode2session
  user-info-url: https://api.weixin.qq.com/sns/userinfo
```

### 日志配置

```yaml
logging:
  config: classpath:logback-spring.xml
  level:
    com.example.cursorquitterweb: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    com.alibaba.druid: INFO
```

## 环境变量配置

### SSL相关环境变量

```bash
# SSL证书密码
export SSL_KEY_STORE_PASSWORD=your_keystore_password

# SSL证书别名
export SSL_KEY_ALIAS=kejiapi.cn

# 是否启用SSL
export SSL_ENABLED=true
```

### 数据库相关环境变量

```bash
# 数据库连接URL
export DATABASE_URL=jdbc:postgresql://localhost:5432/cursor_quitter_web

# 数据库用户名
export DATABASE_USERNAME=postgres

# 数据库密码
export DATABASE_PASSWORD=your_password
```

### 微信相关环境变量

```bash
# 微信小程序AppID
export WECHAT_APP_ID=your_wechat_app_id

# 微信小程序AppSecret
export WECHAT_APP_SECRET=your_wechat_app_secret
```

## 配置优先级

1. **环境变量** - 最高优先级
2. **application.yml** - 默认配置
3. **系统默认值** - 最低优先级

## 配置验证

### 启动时验证

项目启动时会自动验证以下配置：

1. **SSL证书验证**
   - 检查证书文件是否存在
   - 验证证书密码是否正确
   - 检查证书别名是否存在

2. **数据库连接验证**
   - 检查数据库连接是否正常
   - 验证数据库用户权限

3. **微信配置验证**
   - 检查微信AppID和AppSecret是否配置

### 运行时验证

可以通过以下API接口验证配置：

```bash
# 检查SSL状态
GET /api/ssl/status

# 获取SSL证书信息
GET /api/ssl/info

# 检查应用健康状态
GET /api/health

# 检查微信服务状态
GET /api/wechat/health
```

## 常见配置问题

### 1. SSL证书配置问题

**问题**: SSL证书加载失败
**解决方案**:
1. 检查证书文件路径是否正确
2. 验证证书密码是否正确
3. 确认证书别名是否存在

```bash
# 检查证书文件
ls -la src/main/resources/kejiapi.cn.jks

# 验证证书信息
keytool -list -keystore src/main/resources/kejiapi.cn.jks
```

### 2. 数据库连接问题

**问题**: 数据库连接失败
**解决方案**:
1. 检查数据库服务是否启动
2. 验证数据库连接URL是否正确
3. 确认数据库用户权限

```bash
# 测试数据库连接
psql -h localhost -U postgres -d cursor_quitter_web
```

### 3. 微信配置问题

**问题**: 微信登录失败
**解决方案**:
1. 检查微信AppID和AppSecret是否正确
2. 确认微信小程序配置
3. 验证网络连接

```bash
# 检查微信配置
curl -X GET http://localhost:8080/api/wechat/health
```

## 配置最佳实践

### 1. 安全性

- 敏感信息使用环境变量配置
- 生产环境不要使用默认密码
- 定期更新SSL证书

### 2. 性能

- 根据实际需求调整连接池大小
- 合理设置日志级别
- 优化数据库查询

### 3. 可维护性

- 使用有意义的配置项名称
- 添加配置注释
- 保持配置结构清晰

## 配置示例

### 开发环境配置

```bash
# 开发环境环境变量
export SSL_ENABLED=false
export DATABASE_URL=jdbc:postgresql://localhost:5432/cursor_quitter_web_dev
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=dev_password
export WECHAT_APP_ID=dev_app_id
export WECHAT_APP_SECRET=dev_app_secret
```

### 生产环境配置

```bash
# 生产环境环境变量
export SSL_ENABLED=true
export SSL_KEY_STORE_PASSWORD=prod_keystore_password
export SSL_KEY_ALIAS=kejiapi.cn
export DATABASE_URL=jdbc:postgresql://prod-db:5432/cursor_quitter_web
export DATABASE_USERNAME=prod_user
export DATABASE_PASSWORD=prod_password
export WECHAT_APP_ID=prod_app_id
export WECHAT_APP_SECRET=prod_app_secret
``` 