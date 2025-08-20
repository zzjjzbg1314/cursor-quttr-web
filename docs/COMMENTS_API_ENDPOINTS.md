# 评论API端点文档

## 概述

本文档列出了所有评论相关的API端点，所有端点都有明确的URL路径。

## 基础URL

```
http://localhost:8080/api/comments
```

## API端点列表

### 1. 创建评论

**POST** `/api/comments/create`

**请求体** (CreateCommentRequest):
```json
{
    "postId": "uuid-string",
    "userId": "uuid-string",
    "userNickname": "用户昵称",
    "userStage": "用户阶段",
    "avatarUrl": "用户头像链接",
    "content": "评论内容"
}
```

**响应**:
```json
{
    "success": true,
    "message": "评论创建成功",
    "data": {
        "commentId": "uuid-string",
        "postId": "uuid-string",
        "userId": "uuid-string",
        "userNickname": "用户昵称",
        "userStage": "用户阶段",
        "avatarUrl": "用户头像链接",
        "content": "评论内容",
        "isDeleted": false,
        "createdAt": "2025-08-11T10:55:28.335+00:00",
        "updatedAt": "2025-08-11T10:55:28.335+00:00"
    }
}
```

### 2. 获取单个评论

**GET** `/api/comments/{commentId}`

**路径参数**:
- `commentId`: 评论ID (UUID)

**响应**:
```json
{
    "success": true,
    "message": "获取评论成功",
    "data": {
        "commentId": "uuid-string",
        "postId": "uuid-string",
        "userId": "uuid-string",
        "userNickname": "用户昵称",
        "userStage": "用户阶段",
        "content": "评论内容",
        "isDeleted": false,
        "createdAt": "2025-08-11T10:55:28.335+00:00",
        "updatedAt": "2025-08-11T10:55:28.335+00:00"
    }
}
```

### 3. 更新评论

**PUT** `/api/comments/{commentId}/update`

**路径参数**:
- `commentId`: 评论ID (UUID)

**请求体** (UpdateCommentRequest):
```json
{
    "content": "更新后的评论内容",
    "avatarUrl": "更新后的用户头像链接"
}
```

**响应**:
```json
{
    "success": true,
    "message": "评论更新成功",
    "data": {
        "commentId": "uuid-string",
        "postId": "uuid-string",
        "userId": "uuid-string",
        "userNickname": "用户昵称",
        "userStage": "用户阶段",
        "avatarUrl": "更新后的用户头像链接",
        "content": "更新后的评论内容",
        "isDeleted": false,
        "createdAt": "2025-08-11T10:55:28.335+00:00",
        "updatedAt": "2025-08-11T10:55:28.335+00:00"
    }
}
```

### 4. 删除评论

**DELETE** `/api/comments/{commentId}/delete`

**路径参数**:
- `commentId`: 评论ID (UUID)

**响应**:
```json
{
    "success": true,
    "message": "评论删除成功",
    "data": null
}
```

### 5. 根据帖子ID获取评论列表

**GET** `/api/comments/post/{postId}`

**路径参数**:
- `postId`: 帖子ID (UUID)

**响应**: 返回该帖子的所有评论列表

### 6. 根据帖子ID分页获取评论

**GET** `/api/comments/post/{postId}/page`

**路径参数**:
- `postId`: 帖子ID (UUID)

**查询参数**:
- `page`: 页码 (默认: 0)
- `size`: 每页大小 (默认: 10)
- `sortBy`: 排序字段 (默认: "createdAt")
- `sortDir`: 排序方向 (默认: "asc")

**示例**: `/api/comments/post/uuid-string/page?page=0&size=5&sortBy=createdAt&sortDir=desc`

**响应**: 返回分页的评论数据

### 7. 根据用户ID获取评论

**GET** `/api/comments/user/{userId}`

**路径参数**:
- `userId`: 用户ID (UUID)

**响应**: 返回该用户的所有评论列表

### 8. 根据用户昵称获取评论

**GET** `/api/comments/nickname/{userNickname}`

**路径参数**:
- `userNickname`: 用户昵称

**响应**: 返回该用户的所有评论列表

### 9. 根据用户阶段获取评论

**GET** `/api/comments/stage/{userStage}`

**路径参数**:
- `userStage`: 用户阶段

**响应**: 返回该阶段用户的所有评论列表

### 10. 搜索评论（按内容）

**GET** `/api/comments/search/content?content=关键词`

**查询参数**:
- `content`: 搜索关键词

**响应**: 返回内容包含关键词的评论列表

### 11. 获取所有评论（分页）

**GET** `/api/comments/getAllComments`

**查询参数**:
- `page`: 页码 (默认: 0)
- `size`: 每页大小 (默认: 10)
- `sortBy`: 排序字段 (默认: "createdAt")
- `sortDir`: 排序方向 (默认: "desc")

**示例**: `/api/comments/getAllComments?page=0&size=5&sortBy=createdAt&sortDir=desc`

**响应**: 返回分页的所有评论数据

### 12. 根据时间范围获取评论

**GET** `/api/comments/time-range?startTime=2025-08-01T00:00:00Z&endTime=2025-08-11T23:59:59Z`

**查询参数**:
- `startTime`: 开始时间 (ISO 8601格式)
- `endTime`: 结束时间 (ISO 8601格式)

**响应**: 返回指定时间范围内的评论列表

### 13. 统计帖子的评论数量

**GET** `/api/comments/count/post/{postId}`

**路径参数**:
- `postId`: 帖子ID (UUID)

**响应**:
```json
{
    "success": true,
    "message": "统计帖子评论数量成功",
    "data": 25
}
```

### 14. 统计用户的评论数量

**GET** `/api/comments/count/user/{userId}`

**路径参数**:
- `userId`: 用户ID (UUID)

**响应**:
```json
{
    "success": true,
    "message": "统计用户评论数量成功",
    "data": 15
}
```

### 15. 统计时间范围内的评论数量

**GET** `/api/comments/count/time-range?startTime=2025-08-01T00:00:00Z&endTime=2025-08-11T23:59:59Z`

**查询参数**:
- `startTime`: 开始时间 (ISO 8601格式)
- `endTime`: 结束时间 (ISO 8601格式)

**响应**:
```json
{
    "success": true,
    "message": "统计时间范围评论数量成功",
    "data": 150
}
```

## 数据模型

### CreateCommentRequest

创建评论请求DTO：

- `postId`: 帖子ID (UUID)
- `userId`: 用户ID (UUID)
- `userNickname`: 用户昵称
- `userStage`: 用户阶段
- `avatarUrl`: 用户头像链接
- `content`: 评论内容

### UpdateCommentRequest

更新评论请求DTO：

- `content`: 评论内容
- `avatarUrl`: 用户头像链接

### Comment

评论实体，包含以下字段：

- `commentId`: 评论ID (UUID)
- `postId`: 帖子ID (UUID)
- `userId`: 用户ID (UUID)
- `userNickname`: 用户昵称
- `userStage`: 用户阶段
- `avatarUrl`: 用户头像链接
- `content`: 评论内容
- `isDeleted`: 是否已删除
- `createdAt`: 创建时间
- `updatedAt`: 更新时间

## 注意事项

1. **URL路径**: 所有端点都有明确的URL路径，避免路径冲突
2. **分页支持**: 支持分页的端点都包含分页参数
3. **排序支持**: 支持按不同字段排序
4. **错误处理**: 所有端点都包含统一的错误处理
5. **软删除**: 删除操作采用软删除方式

## 测试建议

1. 测试创建评论功能
2. 测试获取评论列表（分页和不分页）
3. 测试按不同条件查询评论
4. 测试评论的增删改查操作
5. 测试统计功能

## 联系信息

如果在使用API时遇到问题，请联系开发团队。
