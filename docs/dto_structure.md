# DTO类结构说明

## 概述

为了使API更加清晰和易于使用，我们将评论相关的DTO类进行了明确的职责分离。

## DTO类列表

### 1. CreateCommentRequest

**文件**: `src/main/java/com/example/cursorquitterweb/dto/CreateCommentRequest.java`

**用途**: 创建一级评论（直接评论帖子）

**字段**:
```java
private String postId;          // 帖子ID
private String userId;          // 用户ID
private String userNickname;    // 用户昵称
private String userStage;       // 用户阶段
private String avatarUrl;       // 用户头像
private String content;         // 评论内容
```

**使用场景**: 用户直接对帖子发表评论

**API端点**: `POST /api/comments/create`

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

---

### 2. CreateReplyRequest

**文件**: `src/main/java/com/example/cursorquitterweb/dto/CreateReplyRequest.java`

**用途**: 创建回复评论（回复某条评论）

**字段**:
```java
// 基础字段
private String postId;              // 帖子ID
private String userId;              // 用户ID
private String userNickname;        // 用户昵称
private String userStage;           // 用户阶段
private String avatarUrl;           // 用户头像
private String content;             // 回复内容

// 回复相关字段
private String parentCommentId;     // 父评论ID（必需）
private String replyToUserId;       // 被回复的用户ID
private String replyToUserNickname; // 被回复的用户昵称
private String replyToCommentId;    // 被回复的评论ID（必需）
```

**使用场景**: 用户回复某条已存在的评论

**API端点**: `POST /api/comments/reply`

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

---

### 3. UpdateCommentRequest

**文件**: `src/main/java/com/example/cursorquitterweb/dto/UpdateCommentRequest.java`

**用途**: 更新评论内容

**字段**:
```java
private String content;    // 更新后的内容
private String avatarUrl;  // 更新后的头像（可选）
```

**使用场景**: 用户编辑自己的评论或回复

**API端点**: `PUT /api/comments/{commentId}/update`

---

### 4. CommentWithRepliesDTO

**文件**: `src/main/java/com/example/cursorquitterweb/dto/CommentWithRepliesDTO.java`

**用途**: 返回一级评论及其所有回复（小红书风格）

**字段**:
```java
private Comment comment;        // 一级评论对象
private List<Comment> replies;  // 该评论的所有回复
private long replyCount;        // 回复数量
```

**使用场景**: 前端需要以小红书风格展示评论和回复时使用

**API端点**: 
- `GET /api/comments/post/{postId}/with-replies`
- `GET /api/comments/post/{postId}/with-replies/page`

**响应示例**:
```json
{
  "code": 200,
  "message": "获取评论及回复成功",
  "data": [
    {
      "comment": {
        "commentId": "...",
        "commentLevel": 1,
        "userNickname": "张三",
        "content": "这是一条一级评论",
        ...
      },
      "replies": [
        {
          "commentId": "...",
          "commentLevel": 2,
          "userNickname": "李四",
          "replyToUserNickname": "张三",
          "content": "回复@张三：我同意你的观点",
          ...
        }
      ],
      "replyCount": 1
    }
  ]
}
```

---

## 设计原则

### 1. 单一职责原则 (SRP)

每个DTO类只负责一个特定的功能：
- `CreateCommentRequest` - 只负责创建一级评论
- `CreateReplyRequest` - 只负责创建回复
- `UpdateCommentRequest` - 只负责更新评论
- `CommentWithRepliesDTO` - 只负责返回评论及回复的组合数据

### 2. 明确的接口语义

通过不同的DTO类，API的用途一目了然：
- 看到`CreateCommentRequest`，立即知道这是创建一级评论
- 看到`CreateReplyRequest`，立即知道这是创建回复

### 3. 类型安全

分离DTO后，编译器可以帮助我们检查：
- 创建评论时不会误传回复相关字段
- 创建回复时必须提供回复相关字段

### 4. 易于维护

当需求变更时：
- 修改评论创建逻辑，只需修改`CreateCommentRequest`
- 修改回复创建逻辑，只需修改`CreateReplyRequest`
- 互不干扰，降低维护成本

## 前端集成建议

### TypeScript接口定义

```typescript
// 创建一级评论
interface CreateCommentRequest {
  postId: string;
  userId: string;
  userNickname: string;
  userStage: string;
  avatarUrl?: string;
  content: string;
}

// 创建回复
interface CreateReplyRequest {
  postId: string;
  userId: string;
  userNickname: string;
  userStage: string;
  avatarUrl?: string;
  content: string;
  parentCommentId: string;     // 必需
  replyToUserId?: string;
  replyToUserNickname?: string;
  replyToCommentId: string;    // 必需
}

// 评论及回复
interface CommentWithReplies {
  comment: Comment;
  replies: Comment[];
  replyCount: number;
}
```

### API调用示例

```typescript
// 创建一级评论
async function createComment(data: CreateCommentRequest) {
  return await fetch('/api/comments/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
}

// 创建回复
async function createReply(data: CreateReplyRequest) {
  return await fetch('/api/comments/reply', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
}

// 获取评论及回复
async function getCommentsWithReplies(postId: string, page = 0, size = 10) {
  return await fetch(
    `/api/comments/post/${postId}/with-replies/page?page=${page}&size=${size}`
  );
}
```

## 总结

通过清晰的DTO职责分离：
- ✅ 提高了代码可读性
- ✅ 增强了类型安全性
- ✅ 简化了API使用
- ✅ 便于后续维护和扩展
- ✅ 降低了前后端对接成本

