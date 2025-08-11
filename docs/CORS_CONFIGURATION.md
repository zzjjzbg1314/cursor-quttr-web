# CORS配置说明

## 概述

本文档说明了项目中CORS（跨域资源共享）的配置方式和注意事项。

## 当前配置

### 全局CORS配置 (WebConfig.java)

```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOriginPatterns("*")  // 允许所有来源
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)  // 不发送凭据
            .maxAge(3600);
}
```

### 控制器级别CORS配置

所有控制器都使用简单的CORS注解：

```java
@CrossOrigin(origins = "*")
```

## 配置说明

### 1. allowedOriginPatterns("*")
- 使用`allowedOriginPatterns`而不是`allowedOrigins`
- 允许所有来源访问API
- 适用于开发环境

### 2. allowCredentials(false)
- 设置为`false`以避免CORS问题
- 当设置为`true`时，不能使用通配符`*`
- 如果需要发送凭据，需要明确指定允许的来源

### 3. 允许的HTTP方法
- GET: 获取数据
- POST: 创建数据
- PUT: 更新数据
- DELETE: 删除数据
- OPTIONS: 预检请求

### 4. 允许的请求头
- `*`: 允许所有请求头

### 5. 预检请求缓存时间
- `maxAge(3600)`: 预检请求结果缓存1小时

## 生产环境配置建议

### 1. 限制允许的来源

```java
.allowedOrigins(
    "https://yourdomain.com",
    "https://www.yourdomain.com"
)
```

### 2. 启用凭据支持

```java
.allowCredentials(true)
```

### 3. 限制允许的请求头

```java
.allowedHeaders(
    "Authorization",
    "Content-Type",
    "Accept"
)
```

## 常见问题解决

### 1. CORS错误：allowCredentials和通配符冲突

**问题**：当`allowCredentials`为`true`时，不能使用`allowedOrigins("*")`

**解决方案**：
- 使用`allowedOriginPatterns("*")`（Spring Boot 2.4+）
- 或者明确指定允许的来源
- 或者设置`allowCredentials(false)`

### 2. 预检请求失败

**问题**：OPTIONS请求返回405错误

**解决方案**：
- 确保在`allowedMethods`中包含`OPTIONS`
- 检查是否有拦截器阻止OPTIONS请求

### 3. 请求头被拒绝

**问题**：自定义请求头被CORS阻止

**解决方案**：
- 在`allowedHeaders`中添加自定义请求头
- 或者使用`allowedHeaders("*")`允许所有请求头

## 测试CORS配置

### 1. 使用浏览器开发者工具
- 查看Network标签页
- 检查CORS相关的错误信息

### 2. 使用Postman或类似工具
- 设置Origin请求头
- 测试不同的HTTP方法

### 3. 使用curl命令
```bash
curl -H "Origin: http://localhost:3000" \
     -H "Access-Control-Request-Method: POST" \
     -H "Access-Control-Request-Headers: Content-Type" \
     -X OPTIONS \
     http://localhost:8080/api/posts
```

## 安全注意事项

### 1. 不要在生产环境使用通配符
- 明确指定允许的来源
- 限制允许的HTTP方法
- 限制允许的请求头

### 2. 考虑使用代理
- 在开发环境中使用代理避免CORS问题
- 在生产环境中使用反向代理

### 3. 监控CORS请求
- 记录CORS相关的错误
- 监控异常的跨域请求

## 联系信息

如果在配置CORS时遇到问题，请联系开发团队。
