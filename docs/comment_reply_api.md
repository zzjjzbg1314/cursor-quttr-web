# 评论回复功能API文档

## 概述

本文档描述了小红书风格的评论回复功能API。支持两级评论结构：
- **一级评论**：直接评论帖子（comment_level=1）
- **二级评论**：回复评论（comment_level=2）

## 字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `parent_comment_id` | UUID | 父评论ID，NULL表示直接评论帖子 |
| `reply_to_user_id` | UUID | 被回复的用户ID |
| `reply_to_user_nickname` | String | 被回复的用户昵称 |
| `reply_to_comment_id` | UUID | 被回复的评论ID（用于定位具体哪条评论） |
| `comment_level` | Short | 评论层级：1=直接评论，2=回复评论 |
| `root_comment_id` | UUID | 根评论ID，同一回复链的所有评论指向同一个根评论 |

## API端点

### 1. 创建一级评论（直接评论帖子）

**端点**: `POST /api/comments/create`

**说明**: 创建一级评论，直接评论帖子。

**请求示例**:
```json
{
  "postId": "123e4567-e89b-12d3-a456-426614174000",
  "userId": "123e4567-e89b-12d3-a456-426614174001",
  "userNickname": "张三",
  "userStage": "戒断第10天",
  "avatarUrl": "https://example.com/avatar.jpg",
  "content": "这是一条一级评论"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "评论创建成功",
  "data": {
    "commentId": "123e4567-e89b-12d3-a456-426614174002",
    "postId": "123e4567-e89b-12d3-a456-426614174000",
    "userId": "123e4567-e89b-12d3-a456-426614174001",
    "userNickname": "张三",
    "userStage": "戒断第10天",
    "avatarUrl": "https://example.com/avatar.jpg",
    "content": "这是一条一级评论",
    "commentLevel": 1,
    "parentCommentId": null,
    "rootCommentId": null,
    "replyToUserId": null,
    "replyToUserNickname": null,
    "replyToCommentId": null,
    "createdAt": "2025-10-23T10:00:00Z",
    "updatedAt": "2025-10-23T10:00:00Z",
    "isDeleted": false
  }
}
```

### 2. 创建回复评论（回复某条评论）

**端点**: `POST /api/comments/reply`

**说明**: 创建回复评论，回复某条已存在的评论。使用专门的`CreateReplyRequest`，必须提供`parentCommentId`和`replyToCommentId`字段。

**请求示例**:
```json
{
  "postId": "123e4567-e89b-12d3-a456-426614174000",
  "userId": "123e4567-e89b-12d3-a456-426614174003",
  "userNickname": "李四",
  "userStage": "戒断第20天",
  "avatarUrl": "https://example.com/avatar2.jpg",
  "content": "回复@张三：我同意你的观点",
  "parentCommentId": "123e4567-e89b-12d3-a456-426614174002",
  "replyToUserId": "123e4567-e89b-12d3-a456-426614174001",
  "replyToUserNickname": "张三",
  "replyToCommentId": "123e4567-e89b-12d3-a456-426614174002"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "回复创建成功",
  "data": {
    "commentId": "123e4567-e89b-12d3-a456-426614174004",
    "postId": "123e4567-e89b-12d3-a456-426614174000",
    "userId": "123e4567-e89b-12d3-a456-426614174003",
    "userNickname": "李四",
    "userStage": "戒断第20天",
    "avatarUrl": "https://example.com/avatar2.jpg",
    "content": "回复@张三：我同意你的观点",
    "commentLevel": 2,
    "parentCommentId": "123e4567-e89b-12d3-a456-426614174002",
    "rootCommentId": "123e4567-e89b-12d3-a456-426614174002",
    "replyToUserId": "123e4567-e89b-12d3-a456-426614174001",
    "replyToUserNickname": "张三",
    "replyToCommentId": "123e4567-e89b-12d3-a456-426614174002",
    "createdAt": "2025-10-23T10:05:00Z",
    "updatedAt": "2025-10-23T10:05:00Z",
    "isDeleted": false
  }
}
```

### 3. 获取帖子的所有一级评论（不包括回复）

**端点**: `GET /api/comments/post/{postId}/top-level`

**说明**: 只返回直接评论帖子的一级评论，不包括回复。

**响应示例**:
```json
{
  "code": 200,
  "message": "获取一级评论成功",
  "data": [
    {
      "commentId": "123e4567-e89b-12d3-a456-426614174002",
      "commentLevel": 1,
      "content": "这是一条一级评论",
      ...
    }
  ]
}
```

### 4. 分页获取帖子的一级评论

**端点**: `GET /api/comments/post/{postId}/top-level/page`

**参数**:
- `page`: 页码，默认0
- `size`: 每页数量，默认10
- `sortBy`: 排序字段，默认createdAt
- `sortDir`: 排序方向，默认asc

**示例**: `GET /api/comments/post/{postId}/top-level/page?page=0&size=10&sortBy=createdAt&sortDir=asc`

### 5. 获取某个评论的所有回复

**端点**: `GET /api/comments/{rootCommentId}/replies`

**说明**: 获取某个一级评论下的所有二级回复。

**响应示例**:
```json
{
  "code": 200,
  "message": "获取回复成功",
  "data": [
    {
      "commentId": "123e4567-e89b-12d3-a456-426614174004",
      "commentLevel": 2,
      "rootCommentId": "123e4567-e89b-12d3-a456-426614174002",
      "replyToUserNickname": "张三",
      "content": "回复@张三：我同意你的观点",
      ...
    }
  ]
}
```

### 6. 获取帖子的所有评论及其回复（小红书风格）★推荐

**端点**: `GET /api/comments/post/{postId}/with-replies`

**说明**: 这是最常用的API，返回格式类似小红书：每个一级评论包含其所有回复。

**响应示例**:
```json
{
  "code": 200,
  "message": "获取评论及回复成功",
  "data": [
    {
      "comment": {
        "commentId": "123e4567-e89b-12d3-a456-426614174002",
        "commentLevel": 1,
        "userNickname": "张三",
        "content": "这是一条一级评论",
        ...
      },
      "replies": [
        {
          "commentId": "123e4567-e89b-12d3-a456-426614174004",
          "commentLevel": 2,
          "userNickname": "李四",
          "replyToUserNickname": "张三",
          "content": "回复@张三：我同意你的观点",
          ...
        },
        {
          "commentId": "123e4567-e89b-12d3-a456-426614174005",
          "commentLevel": 2,
          "userNickname": "王五",
          "replyToUserNickname": "李四",
          "content": "回复@李四：确实如此",
          ...
        }
      ],
      "replyCount": 2
    }
  ]
}
```

### 7. 分页获取帖子的评论及其回复

**端点**: `GET /api/comments/post/{postId}/with-replies/page`

**参数**:
- `page`: 页码，默认0
- `size`: 每页数量，默认10
- `sortBy`: 排序字段，默认createdAt
- `sortDir`: 排序方向，默认asc

**说明**: 对一级评论进行分页，每个一级评论包含其所有回复（回复不分页）。

### 8. 统计某个评论的回复数量

**端点**: `GET /api/comments/{rootCommentId}/replies/count`

**响应示例**:
```json
{
  "code": 200,
  "message": "统计回复数量成功",
  "data": 5
}
```

### 9. 删除评论及其所有回复（级联删除）

**端点**: `DELETE /api/comments/{commentId}/delete-with-replies`

**说明**: 删除一个一级评论时，会同时软删除该评论下的所有回复。

**响应示例**:
```json
{
  "code": 200,
  "message": "评论及其回复删除成功",
  "data": null
}
```

### 10. 获取单个评论（原有API）

**端点**: `GET /api/comments/{commentId}`

### 11. 更新评论（原有API）

**端点**: `PUT /api/comments/{commentId}/update`

### 12. 删除单个评论（原有API，不删除回复）

**端点**: `DELETE /api/comments/{commentId}/delete`

**说明**: 只删除该评论本身，不会删除其回复。如需级联删除，请使用`/delete-with-replies`端点。

## 使用场景示例

### 场景1：显示帖子的所有评论（小红书风格）

前端推荐使用：`GET /api/comments/post/{postId}/with-replies/page`

这个API会返回分页的一级评论，每个一级评论自动包含其所有回复，前端可以直接渲染成小红书的评论展示格式。

### 场景2：用户点击"查看更多回复"

如果某个评论的回复很多，可以使用：`GET /api/comments/{rootCommentId}/replies`

### 场景3：用户回复评论

用户点击某条评论的"回复"按钮时，前端需要构造以下数据并调用 `POST /api/comments/reply` 接口：

```json
{
  "postId": "帖子ID",
  "userId": "当前用户ID",
  "userNickname": "当前用户昵称",
  "userStage": "当前用户阶段",
  "avatarUrl": "当前用户头像",
  "content": "回复内容",
  "parentCommentId": "被回复的评论ID",
  "replyToUserId": "被回复用户的ID",
  "replyToUserNickname": "被回复用户的昵称",
  "replyToCommentId": "被回复的评论ID"
}
```

### 场景4：回复的回复

小红书风格的评论只支持两级：
- A评论帖子 → B回复A → C回复B

在这种情况下：
- C的`parentCommentId`指向B
- C的`replyToCommentId`指向B
- C的`rootCommentId`指向A（因为A是根评论）

这样所有的回复都会展示在A评论下方。

## 数据库索引

为了优化查询性能，已创建以下索引：

```sql
CREATE INDEX "idx_comments_parent_id" ON "public"."comments" ("parent_comment_id");
CREATE INDEX "idx_comments_root_id" ON "public"."comments" ("root_comment_id");
CREATE INDEX "idx_comments_level_post" ON "public"."comments" ("post_id", "comment_level");
CREATE INDEX "idx_comments_thread" ON "public"."comments" ("root_comment_id", "created_at");
```

## 性能优化

1. **批量查询回复**：使用`findRepliesByRootCommentIds`方法批量查询多个一级评论的回复，避免N+1查询问题。

2. **分页策略**：对一级评论进行分页，每个一级评论包含其所有回复。如果某个评论的回复特别多，前端可以只显示前几条，提供"查看更多"按钮。

3. **缓存建议**：可以对热门帖子的评论数据进行缓存，减少数据库查询。

## 注意事项

1. **层级限制**：目前只支持两级评论（一级评论+回复），不支持更深的嵌套。

2. **删除策略**：
   - 使用`/delete`端点只删除单个评论
   - 使用`/delete-with-replies`端点级联删除评论及其所有回复

3. **软删除**：所有删除操作都是软删除（`is_deleted=true`），数据不会真正从数据库中移除。

4. **外键约束**：数据库已设置外键约束和级联删除，确保数据一致性。

