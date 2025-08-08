# 阿里云PostgreSQL数据库集成指南

## 概述

本项目已集成阿里云PostgreSQL数据库，支持用户管理、微信登录等功能。

## 数据库表结构

### users表

```sql
CREATE TABLE "public"."users"
(
 "id" uuid NOT NULL DEFAULT gen_random_uuid() ,
 "wechat_openid" text NOT NULL ,
 "wechat_unionid" text ,
 "nickname" text ,
 "avatar_url" text ,
 "gender" smallint ,
 "country" text ,
 "province" text ,
 "city" text ,
 "language" text DEFAULT 'zh_CN'::text ,
 "registration_time" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP ,
 "challenge_reset_time" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP ,
 "created_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP ,
 "updated_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP ,
CONSTRAINT "pk_public_users" PRIMARY KEY ("id") ,
CONSTRAINT "uk_users_wechat_openid" UNIQUE ("wechat_openid") WITH (FILLFACTOR=100) ,
CONSTRAINT "users_gender_check"  CHECK (gender = ANY (ARRAY[0, 1, 2])) NOT VALID 
);
```

## 环境变量配置

### 必需的环境变量

```bash
# 数据库连接URL（阿里云PostgreSQL格式）
export DATABASE_URL=jdbc:postgresql://your-host:5432/your-database

# 数据库用户名
export DATABASE_USERNAME=your-username

# 数据库密码
export DATABASE_PASSWORD=your-password
```

### 可选的环境变量

```bash
# 是否显示SQL日志（开发环境建议true，生产环境建议false）
export SHOW_SQL=false

# 是否显示Hibernate SQL日志
export HIBERNATE_SQL_LOG=false
```

## 阿里云PostgreSQL配置步骤

### 1. 创建数据库实例

1. 登录阿里云控制台
2. 进入RDS PostgreSQL控制台
3. 创建PostgreSQL实例
4. 选择合适的地域、可用区、规格等

### 2. 配置网络

1. 在RDS控制台中选择"数据库连接"
2. 配置白名单，添加应用服务器的IP地址
3. 或者配置VPC内网连接

### 3. 创建数据库和用户

```sql
-- 连接到PostgreSQL实例
psql -h your-host -U postgres -d postgres

-- 创建数据库
CREATE DATABASE cursor_quitter_web;

-- 创建用户
CREATE USER cursor_user WITH PASSWORD 'your-password';

-- 授权
GRANT ALL PRIVILEGES ON DATABASE cursor_quitter_web TO cursor_user;
```

### 4. 配置应用

更新`application.yml`中的数据库配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://your-host:5432/cursor_quitter_web
    username: cursor_user
    password: your-password
```

## 数据库迁移

项目使用Flyway进行数据库迁移管理。

### 迁移脚本位置

```
src/main/resources/db/migration/
├── V1__Create_users_table.sql
└── ...
```

### 运行迁移

项目启动时会自动运行迁移脚本。也可以手动运行：

```bash
# 使用Maven
mvn flyway:migrate

# 或者使用Flyway命令行工具
flyway -url=jdbc:postgresql://your-host:5432/cursor_quitter_web \
       -user=cursor_user \
       -password=your-password \
       migrate
```

## API接口

### 用户管理接口

#### 1. 获取用户信息

```http
GET /api/users/{id}
```

#### 2. 根据微信openid获取用户

```http
GET /api/users/wechat/{openid}
```

#### 3. 创建用户

```http
POST /api/users
Content-Type: application/json

{
  "wechatOpenid": "wx_openid_123",
  "nickname": "用户昵称",
  "avatarUrl": "https://example.com/avatar.jpg"
}
```

#### 4. 更新用户信息

```http
PUT /api/users/{id}
Content-Type: application/json

{
  "nickname": "新昵称",
  "avatarUrl": "https://example.com/new-avatar.jpg",
  "gender": 1,
  "country": "中国",
  "province": "广东",
  "city": "深圳"
}
```

#### 5. 删除用户

```http
DELETE /api/users/{id}
```

#### 6. 搜索用户

```http
GET /api/users/search?nickname=关键词
```

#### 7. 根据城市查询用户

```http
GET /api/users/city/{city}
```

#### 8. 根据省份查询用户

```http
GET /api/users/province/{province}
```

#### 9. 根据注册时间范围查询用户

```http
GET /api/users/registration-time?startTime=2024-01-01T00:00:00Z&endTime=2024-12-31T23:59:59Z
```

#### 10. 重置用户挑战时间

```http
POST /api/users/{id}/reset-challenge
```

#### 11. 获取注册统计信息

```http
GET /api/users/stats/registration?startTime=2024-01-01T00:00:00Z&endTime=2024-12-31T23:59:59Z
```

#### 12. 获取性别统计信息

```http
GET /api/users/stats/gender
```

## 监控和管理

### Druid连接池监控

访问 `http://your-domain/druid` 查看数据库连接池监控信息。

默认登录信息：
- 用户名：admin
- 密码：admin

### 健康检查

```http
GET /actuator/health
```

## 常见问题

### 1. 连接超时

**问题**：数据库连接超时
**解决方案**：
- 检查网络连接
- 确认白名单配置
- 检查数据库实例状态

### 2. UUID类型错误

**问题**：UUID类型不支持
**解决方案**：
- 确保使用PostgreSQL 9.4+
- 检查Hibernate方言配置

### 3. 迁移失败

**问题**：Flyway迁移失败
**解决方案**：
- 检查数据库权限
- 确认迁移脚本语法
- 查看详细错误日志

### 4. 性能问题

**问题**：查询性能差
**解决方案**：
- 检查索引是否正确创建
- 优化查询语句
- 调整连接池配置

## 最佳实践

### 1. 安全性

- 使用强密码
- 定期更换密码
- 限制数据库访问IP
- 启用SSL连接

### 2. 性能

- 合理配置连接池大小
- 创建必要的索引
- 定期分析慢查询
- 监控数据库性能

### 3. 备份

- 定期备份数据库
- 测试备份恢复流程
- 保存多个备份版本

### 4. 监控

- 监控数据库连接数
- 监控查询性能
- 设置告警机制
- 定期检查日志
