# 帖子API端点文档

## 概述

本文档列出了所有帖子相关的API端点，包括新增的包含点赞数的端点。

## 基础URL

```
http://localhost:8080/api/posts
```

## API端点列表

### 1. 创建帖子

**POST** `/api/posts/create`

**请求体** (CreatePostRequest):
```json
{
    "userId": "uuid-string",
    "userNickname": "用户昵称",
    "userStage": "用户阶段",
    "title": "帖子标题",
    "content": "帖子内容"
}
```

**响应**:
```json
{
    "success": true,
    "message": "帖子创建成功",
    "data": {
        "postId": "uuid-string",
        "userId": "uuid-string",
        "userNickname": "用户昵称",
        "userStage": "用户阶段",
        "title": "帖子标题",
        "content": "帖子内容",
        "isDeleted": false,
        "createdAt": "2025-08-11T10:55:28.335+00:00",
        "updatedAt": "2025-08-11T10:55:28.335+00:00"
    }
}
```

### 2. 获取单个帖子

**GET** `/api/posts/{postId}`

**路径参数**:
- `postId`: 帖子ID (UUID)

**响应**:
```json
{
    "success": true,
    "message": "获取帖子成功",
    "data": {
        "postId": "uuid-string",
        "userId": "uuid-string",
        "userNickname": "用户昵称",
        "userStage": "用户阶段",
        "title": "帖子标题",
        "content": "帖子内容",
        "isDeleted": false,
        "createdAt": "2025-08-11T10:55:28.335+00:00",
        "updatedAt": "2025-08-11T10:55:28.335+00:00"
    }
}
```

### 3. 更新帖子

**PUT** `/api/posts/{postId}/update`

**路径参数**:
- `postId`: 帖子ID (UUID)

**请求体** (UpdatePostRequest):
```json
{
    "title": "更新后的标题",
    "content": "更新后的内容"
}
```

**响应**:
```json
{
    "success": true,
    "message": "帖子更新成功",
    "data": {
        "postId": "uuid-string",
        "userId": "uuid-string",
        "userNickname": "用户昵称",
        "userStage": "用户阶段",
        "title": "更新后的标题",
        "content": "更新后的内容",
        "isDeleted": false,
        "createdAt": "2025-08-11T10:55:28.335+00:00",
        "updatedAt": "2025-08-11T10:55:28.335+00:00"
    }
}
```

### 4. 删除帖子

**DELETE** `/api/posts/{postId}/delete`

**路径参数**:
- `postId`: 帖子ID (UUID)

**响应**:
```json
{
    "success": true,
    "message": "帖子删除成功",
    "data": null
}
```

### 5. 获取所有帖子（分页，包含点赞数）⭐ 新增

**GET** `/api/posts/getAllPosts`

**查询参数**:
- `page`: 页码 (默认: 0)
- `size`: 每页大小 (默认: 10)
- `sortBy`: 排序字段 (默认: "createdAt")
- `sortDir`: 排序方向 (默认: "desc")

**示例**: `/api/posts/getAllPosts?page=0&size=5&sortBy=createdAt&sortDir=desc`

**响应**:
```json
{
    "success": true,
    "message": "获取帖子列表成功",
    "data": {
        "content": [
            {
                "postId": "uuid-string",
                "userId": "uuid-string",
                "userNickname": "用户昵称",
                "userStage": "用户阶段",
                "title": "帖子标题",
                "content": "帖子内容",
                "isDeleted": false,
                "createdAt": "2025-08-11T10:55:28.335+00:00",
                "updatedAt": "2025-08-11T10:55:28.335+00:00",
                "upvotes": 5
            }
        ],
        "pageable": {
            "pageNumber": 0,
            "pageSize": 5,
            "sort": {
                "sorted": true,
                "unsorted": false
            }
        },
        "totalElements": 100,
        "totalPages": 20,
        "last": false,
        "first": true,
        "numberOfElements": 5
    }
}
```

### 6. 获取所有帖子（不分页，包含点赞数）⭐ 新增

**GET** `/api/posts/getAllPostsList`

**响应**:
```json
{
    "success": true,
    "message": "获取帖子列表成功",
    "data": [
        {
            "postId": "uuid-string",
            "userId": "uuid-string",
            "userNickname": "用户昵称",
            "userStage": "用户阶段",
            "title": "帖子标题",
            "content": "帖子内容",
            "isDeleted": false,
            "createdAt": "2025-08-11T10:55:28.335+00:00",
            "updatedAt": "2025-08-11T10:55:28.335+00:00",
            "upvotes": 5
        }
    ]
}
```

### 7. 根据用户ID获取帖子

**GET** `/api/posts/user/{userId}`

**路径参数**:
- `userId`: 用户ID (UUID)

**响应**: 返回该用户的所有帖子列表

### 8. 根据用户昵称获取帖子

**GET** `/api/posts/nickname/{userNickname}`

**路径参数**:
- `userNickname`: 用户昵称

**响应**: 返回该用户的所有帖子列表

### 9. 根据用户阶段获取帖子

**GET** `/api/posts/stage/{userStage}`

**路径参数**:
- `userStage`: 用户阶段

**响应**: 返回该阶段用户的所有帖子列表

### 10. 搜索帖子（按标题）

**GET** `/api/posts/search/title?title=关键词`

**查询参数**:
- `title`: 搜索关键词

**响应**: 返回标题包含关键词的帖子列表

### 11. 搜索帖子（按内容）

**GET** `/api/posts/search/content?content=关键词`

**查询参数**:
- `content`: 搜索关键词

**响应**: 返回内容包含关键词的帖子列表

### 12. 根据时间范围获取帖子

**GET** `/api/posts/time-range?startTime=2025-08-01T00:00:00Z&endTime=2025-08-11T23:59:59Z`

**查询参数**:
- `startTime`: 开始时间 (ISO 8601格式)
- `endTime`: 结束时间 (ISO 8601格式)

**响应**: 返回指定时间范围内的帖子列表

### 13. 统计用户的帖子数量

**GET** `/api/posts/count/user/{userId}`

**路径参数**:
- `userId`: 用户ID (UUID)

**响应**:
```json
{
    "success": true,
    "message": "统计用户帖子数量成功",
    "data": 25
}
```

### 14. 统计时间范围内的帖子数量

**GET** `/api/posts/count/time-range?startTime=2025-08-01T00:00:00Z&endTime=2025-08-11T23:59:59Z`

**查询参数**:
- `startTime`: 开始时间 (ISO 8601格式)
- `endTime`: 结束时间 (ISO 8601格式)

**响应**:
```json
{
    "success": true,
    "message": "统计时间范围帖子数量成功",
    "data": 150
}
```

## 数据模型

### PostWithUpvotesDto

包含点赞数的帖子DTO，包含以下字段：

- `postId`: 帖子ID (UUID)
- `userId`: 用户ID (UUID)
- `userNickname`: 用户昵称
- `userStage`: 用户阶段
- `title`: 帖子标题
- `content`: 帖子内容
- `isDeleted`: 是否已删除
- `createdAt`: 创建时间
- `updatedAt`: 更新时间
- `upvotes`: 点赞数 ⭐ 新增字段

## 注意事项

1. **点赞数获取**: 新增的端点会自动从`post_likes`表获取点赞数，如果查不到默认为0
2. **分页支持**: `getAllPosts`端点支持分页和排序
3. **错误处理**: 所有端点都包含统一的错误处理
4. **数据一致性**: 点赞数通过独立的表管理，确保数据一致性

## 测试建议

1. 测试创建帖子功能
2. 测试获取包含点赞数的帖子列表
3. 验证点赞数是否正确获取
4. 测试分页和排序功能
5. 测试各种查询条件

## 联系信息

如果在使用API时遇到问题，请联系开发团队。
