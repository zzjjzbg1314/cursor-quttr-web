# 呼吸练习功能集成文档

## 概述
已成功为呼吸练习表（breathe）集成了完整的CRUD功能和查询接口，包括实体类、Repository、Service、Controller和DTO。

## 数据库表结构
```sql
CREATE TABLE "public"."breathe" (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "title" VARCHAR(255) NOT NULL,
    "time" VARCHAR(50),
    "audiourl" VARCHAR(1024),
    "createAt" TIMESTAMPTZ DEFAULT NOW(),
    "updateAt" TIMESTAMPTZ DEFAULT NOW()
);
```

## 创建的文件

### 1. 实体类 (Entity)
- **文件**: `src/main/java/com/example/cursorquitterweb/entity/Breathe.java`
- **功能**: 定义呼吸练习实体，包含JPA注解和字段映射
- **字段**: id, title, time, audiourl, createAt, updateAt

### 2. 数据访问层 (Repository)
- **文件**: `src/main/java/com/example/cursorquitterweb/repository/BreatheRepository.java`
- **功能**: 继承JpaRepository，提供数据访问方法
- **主要方法**:
  - 基础CRUD操作
  - 按标题搜索（模糊查询和精确查询）
  - 按时间范围查询
  - 分页查询
  - 中文全文搜索
  - 统计功能

### 3. 服务层 (Service)
- **文件**: `src/main/java/com/example/cursorquitterweb/service/BreatheService.java`
- **功能**: 业务逻辑处理，事务管理
- **主要方法**:
  - 创建、更新、删除呼吸练习
  - 各种查询和搜索功能
  - 分页处理
  - DTO转换

### 4. 数据传输对象 (DTO)
- **文件**: `src/main/java/com/example/cursorquitterweb/dto/BreatheDto.java`
- **功能**: 前端数据传输对象
- **字段**: id, title, time, audiourl, createAt, updateAt

### 5. 控制器 (Controller)
- **文件**: `src/main/java/com/example/cursorquitterweb/controller/BreatheController.java`
- **功能**: REST API接口
- **基础路径**: `/api/breathe`

## API接口列表

### 基础CRUD操作
- `GET /api/breathe/{id}` - 根据ID获取呼吸练习
- `POST /api/breathe/create` - 创建新呼吸练习
- `PUT /api/breathe/{id}` - 更新呼吸练习信息
- `DELETE /api/breathe/{id}` - 删除呼吸练习

### 查询和搜索
- `GET /api/breathe/search/title?title={title}` - 按标题模糊搜索
- `GET /api/breathe/search/keyword/title?keyword={keyword}` - 标题关键词搜索（中文全文搜索）
- `GET /api/breathe/title/{title}` - 按标题精确查询
- `GET /api/breathe/getAllBreathe` - 获取所有呼吸练习

### 时间范围查询
- `GET /api/breathe/create-time?startTime={start}&endTime={end}` - 按创建时间范围查询
- `GET /api/breathe/update-time?startTime={start}&endTime={end}` - 按更新时间范围查询

### 分页查询
- `GET /api/breathe/page?page={page}&size={size}` - 分页查询
- `GET /api/breathe/search/title/page?title={title}&page={page}&size={size}` - 按标题分页搜索

### 特殊查询
- `GET /api/breathe/with-audio-url` - 获取有音频链接的呼吸练习
- `GET /api/breathe/latest?limit={limit}` - 获取最新呼吸练习列表

### 统计功能
- `GET /api/breathe/count` - 统计呼吸练习总数
- `GET /api/breathe/stats/create-time?startTime={start}&endTime={end}` - 按时间范围统计

### 更新操作
- `PUT /api/breathe/{id}/audio-url` - 更新音频链接

## 请求/响应格式

### 创建呼吸练习请求
```json
{
    "title": "深呼吸练习",
    "time": "5分钟",
    "audiourl": "http://example.com/audio.mp3"
}
```

### 更新呼吸练习请求
```json
{
    "title": "更新后的标题",
    "time": "10分钟",
    "audiourl": "http://example.com/new-audio.mp3"
}
```

### 标准响应格式
```json
{
    "success": true,
    "message": "操作成功",
    "data": {
        "id": "uuid",
        "title": "标题",
        "time": "时长",
        "audiourl": "音频链接",
        "createAt": "2024-01-01T00:00:00Z",
        "updateAt": "2024-01-01T00:00:00Z"
    }
}
```

## 测试
- **文件**: `src/test/java/com/example/cursorquitterweb/BreatheIntegrationTest.java`
- **功能**: 集成测试，验证所有主要功能
- **测试用例**:
  - 创建呼吸练习
  - 根据ID查找
  - 按标题搜索
  - 更新操作
  - 删除操作
  - 获取最新列表
  - 统计功能

## 特性
1. **完整的CRUD操作** - 支持创建、读取、更新、删除
2. **多种查询方式** - 精确查询、模糊搜索、关键词搜索
3. **分页支持** - 支持分页查询，提高性能
4. **时间范围查询** - 支持按创建时间和更新时间范围查询
5. **中文全文搜索** - 使用PostgreSQL的中文全文搜索功能
6. **事务管理** - 使用Spring的@Transactional注解
7. **日志记录** - 使用LogUtil记录操作日志
8. **统一响应格式** - 使用ApiResponse统一API响应格式
9. **数据验证** - 使用@Valid注解进行请求数据验证
10. **自动时间戳** - 自动管理创建时间和更新时间

## 使用说明
1. 确保数据库表已创建
2. 启动Spring Boot应用
3. 使用Postman或其他API工具测试接口
4. 所有接口都返回统一的JSON格式响应
5. 支持分页查询，默认每页20条记录
6. 时间参数使用ISO 8601格式

## 注意事项
- 标题字段为必填项
- 音频链接字段可选
- 时间字段存储为字符串格式
- 所有时间戳使用OffsetDateTime类型，支持时区
- 删除操作不可逆，请谨慎使用
- 分页查询的页码从0开始
