# 帖子管理API文档

## 概述

本文档描述了帖子管理系统的API接口，包括创建、查询、更新、删除帖子等操作。

## 数据库表结构

### posts表

```sql
CREATE TABLE posts (
    post_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_nickname VARCHAR(100) NOT NULL,
    user_stage VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0
);
```

## API接口

### 基础URL
```
http://localhost:8080/api/posts
```

### 1. 创建帖子

**POST** `/api/posts`

**请求体：**
```json
{
    "userId": 1,
    "userNickname": "张三",
    "userStage": "新手",
    "title": "我的第一篇帖子",
    "content": "这是帖子的内容..."
}
```

**响应：**
```json
{
    "success": true,
    "message": "帖子创建成功",
    "data": {
        "postId": 1,
        "userId": 1,
        "userNickname": "张三",
        "userStage": "新手",
        "title": "我的第一篇帖子",
        "content": "这是帖子的内容...",
        "isDeleted": false,
        "createdAt": "2024-01-01T10:00:00Z",
        "updatedAt": "2024-01-01T10:00:00Z",
        "likeCount": 0,
        "commentCount": 0
    }
}
```

### 2. 获取帖子详情

**GET** `/api/posts/{postId}`

**响应：**
```json
{
    "success": true,
    "message": "获取帖子成功",
    "data": {
        "postId": 1,
        "userId": 1,
        "userNickname": "张三",
        "userStage": "新手",
        "title": "我的第一篇帖子",
        "content": "这是帖子的内容...",
        "isDeleted": false,
        "createdAt": "2024-01-01T10:00:00Z",
        "updatedAt": "2024-01-01T10:00:00Z",
        "likeCount": 0,
        "commentCount": 0
    }
}
```

### 3. 更新帖子

**PUT** `/api/posts/{postId}`

**请求体：**
```json
{
    "title": "更新后的标题",
    "content": "更新后的内容..."
}
```

**响应：**
```json
{
    "success": true,
    "message": "帖子更新成功",
    "data": {
        "postId": 1,
        "userId": 1,
        "userNickname": "张三",
        "userStage": "新手",
        "title": "更新后的标题",
        "content": "更新后的内容...",
        "isDeleted": false,
        "createdAt": "2024-01-01T10:00:00Z",
        "updatedAt": "2024-01-01T11:00:00Z",
        "likeCount": 0,
        "commentCount": 0
    }
}
```

### 4. 删除帖子

**DELETE** `/api/posts/{postId}`

**响应：**
```json
{
    "success": true,
    "message": "帖子删除成功",
    "data": null
}
```

### 5. 获取帖子列表（分页）

**GET** `/api/posts?page=0&size=10&sortBy=createdAt&sortDir=desc`

**查询参数：**
- `page`: 页码（从0开始）
- `size`: 每页大小
- `sortBy`: 排序字段
- `sortDir`: 排序方向（asc/desc）

**响应：**
```json
{
    "success": true,
    "message": "获取帖子列表成功",
    "data": {
        "content": [
            {
                "postId": 1,
                "userId": 1,
                "userNickname": "张三",
                "userStage": "新手",
                "title": "我的第一篇帖子",
                "content": "这是帖子的内容...",
                "isDeleted": false,
                "createdAt": "2024-01-01T10:00:00Z",
                "updatedAt": "2024-01-01T10:00:00Z",
                "likeCount": 0,
                "commentCount": 0
            }
        ],
        "pageable": {
            "pageNumber": 0,
            "pageSize": 10,
            "sort": {
                "sorted": true,
                "unsorted": false
            }
        },
        "totalElements": 1,
        "totalPages": 1,
        "last": true,
        "first": true,
        "numberOfElements": 1
    }
}
```

### 6. 根据用户ID获取帖子

**GET** `/api/posts/user/{userId}`

**响应：**
```json
{
    "success": true,
    "message": "获取用户帖子成功",
    "data": [
        {
            "postId": 1,
            "userId": 1,
            "userNickname": "张三",
            "userStage": "新手",
            "title": "我的第一篇帖子",
            "content": "这是帖子的内容...",
            "isDeleted": false,
            "createdAt": "2024-01-01T10:00:00Z",
            "updatedAt": "2024-01-01T10:00:00Z",
            "likeCount": 0,
            "commentCount": 0
        }
    ]
}
```

### 7. 根据用户昵称获取帖子

**GET** `/api/posts/nickname/{userNickname}`

### 8. 根据用户阶段获取帖子

**GET** `/api/posts/stage/{userStage}`

### 9. 搜索帖子（按标题）

**GET** `/api/posts/search/title?title=关键词`

### 10. 搜索帖子（按内容）

**GET** `/api/posts/search/content?content=关键词`

### 11. 获取热门帖子（按点赞数）

**GET** `/api/posts/hot/likes`

### 12. 获取热门帖子（按评论数）

**GET** `/api/posts/hot/comments`

### 13. 点赞帖子

**POST** `/api/posts/{postId}/like`

### 14. 取消点赞帖子

**POST** `/api/posts/{postId}/unlike`

### 15. 根据时间范围获取帖子

**GET** `/api/posts/time-range?startTime=2024-01-01T00:00:00Z&endTime=2024-01-31T23:59:59Z`

### 16. 统计用户的帖子数量

**GET** `/api/posts/count/user/{userId}`

### 17. 统计时间范围内的帖子数量

**GET** `/api/posts/count/time-range?startTime=2024-01-01T00:00:00Z&endTime=2024-01-31T23:59:59Z`

## 错误处理

当API调用失败时，会返回错误响应：

```json
{
    "success": false,
    "message": "错误描述信息",
    "data": null
}
```

## 注意事项

1. 所有时间字段使用ISO 8601格式（UTC时区）
2. 删除操作采用软删除，不会物理删除数据
3. 点赞数和评论数不能为负数
4. 查询接口默认排除已删除的帖子
5. 分页查询从0开始计数

## 测试

运行项目后，可以使用以下命令测试API：

```bash
# 创建帖子
curl -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "userNickname": "测试用户",
    "userStage": "新手",
    "title": "测试帖子",
    "content": "测试内容"
  }'

# 获取帖子列表
curl http://localhost:8080/api/posts

# 获取特定帖子
curl http://localhost:8080/api/posts/1
```
