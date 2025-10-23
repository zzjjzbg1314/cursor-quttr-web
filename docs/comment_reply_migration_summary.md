# 评论表结构升级 - 完整适配总结

## 更新概述

本次更新实现了类似小红书的评论回复功能，支持两级评论结构：
- **一级评论（comment_level=1）**：直接评论帖子
- **二级评论（comment_level=2）**：回复评论

## 数据库表结构变更

### 新增字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `parent_comment_id` | UUID | 父评论ID，NULL表示直接评论帖子 |
| `reply_to_user_id` | UUID | 被回复的用户ID |
| `reply_to_user_nickname` | VARCHAR(100) | 被回复的用户昵称 |
| `reply_to_comment_id` | UUID | 被回复的评论ID（用于定位具体哪条评论） |
| `comment_level` | SMALLINT | 评论层级：1=直接评论，2=回复评论 |
| `root_comment_id` | UUID | 根评论ID，同一回复链的所有评论指向同一个根评论 |

### 新增索引

```sql
CREATE INDEX "idx_comments_parent_id" ON "public"."comments" ("parent_comment_id");
CREATE INDEX "idx_comments_root_id" ON "public"."comments" ("root_comment_id");
CREATE INDEX "idx_comments_level_post" ON "public"."comments" ("post_id", "comment_level");
CREATE INDEX "idx_comments_thread" ON "public"."comments" ("root_comment_id", "created_at");
```

## 代码更改清单

### 1. 实体类（Entity）

**文件**: `src/main/java/com/example/cursorquitterweb/entity/Comment.java`

**主要更改**:
- ✅ 添加了6个新字段及其getter/setter方法
- ✅ 添加了新的构造函数支持创建回复评论
- ✅ 更新了`toString()`方法包含新字段
- ✅ 默认`commentLevel`为1

### 2. DTO类（Data Transfer Object）

#### CreateCommentRequest
**文件**: `src/main/java/com/example/cursorquitterweb/dto/CreateCommentRequest.java`

**主要更改**:
- ✅ 添加了回复相关的请求字段
- ✅ 添加了对应的getter/setter方法
- ✅ 更新了`toString()`方法

#### CommentWithRepliesDTO（新建）
**文件**: `src/main/java/com/example/cursorquitterweb/dto/CommentWithRepliesDTO.java`

**说明**: 新建的DTO类，用于返回一级评论及其所有回复，支持小红书风格的展示。

**包含字段**:
- `comment`: 一级评论对象
- `replies`: 回复列表
- `replyCount`: 回复数量

### 3. Repository层

**文件**: `src/main/java/com/example/cursorquitterweb/repository/CommentRepository.java`

**新增方法**:
- ✅ `findByPostIdAndCommentLevelAndIsDeletedFalseOrderByCreatedAtAsc()` - 获取一级评论
- ✅ `findByPostIdAndCommentLevelAndIsDeletedFalse()` - 分页获取一级评论
- ✅ `findByRootCommentIdAndCommentLevelAndIsDeletedFalseOrderByCreatedAtAsc()` - 获取某评论的所有回复
- ✅ `findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtAsc()` - 获取直接回复
- ✅ `countByRootCommentIdAndCommentLevelAndIsDeletedFalse()` - 统计回复数量
- ✅ `countByParentCommentIdAndIsDeletedFalse()` - 统计直接回复数量
- ✅ `findRepliesByRootCommentIds()` - 批量查询回复（性能优化）
- ✅ `softDeleteCommentAndReplies()` - 级联删除评论及回复
- ✅ `findByPostIdAndUserIdAndIsDeletedFalseOrderByCreatedAtDesc()` - 查询用户在帖子下的所有评论
- ✅ `countByReplyToCommentIdAndIsDeletedFalse()` - 统计被回复次数

### 4. Service层

**文件**: `src/main/java/com/example/cursorquitterweb/service/CommentService.java`

**新增接口方法**:
- ✅ `createReplyComment()` - 创建回复评论
- ✅ `findTopLevelCommentsByPostId()` - 获取一级评论
- ✅ `findTopLevelCommentsByPostId(Pageable)` - 分页获取一级评论
- ✅ `findRepliesByRootCommentId()` - 获取某评论的所有回复
- ✅ `findCommentsWithRepliesByPostId()` - 获取评论及回复（小红书风格）
- ✅ `findCommentsWithRepliesByPostId(Pageable)` - 分页获取评论及回复
- ✅ `countRepliesByRootCommentId()` - 统计回复数量
- ✅ `deleteCommentAndReplies()` - 级联删除

### 5. ServiceImpl层

**文件**: `src/main/java/com/example/cursorquitterweb/service/impl/CommentServiceImpl.java`

**主要更改**:
- ✅ 导入了新的DTO类和工具类
- ✅ 实现了所有Service接口的新方法
- ✅ 更新了`createComment()`方法，确保一级评论的`commentLevel=1`
- ✅ 实现了智能的回复逻辑，自动计算`root_comment_id`
- ✅ 实现了批量查询优化，避免N+1问题

**关键实现逻辑**:

```java
// 创建回复评论时自动计算root_comment_id
if (parentCommentUuid != null) {
    Optional<Comment> parentComment = commentRepository.findByCommentIdAndIsDeletedFalse(parentCommentUuid);
    if (parentComment.isPresent()) {
        Comment parent = parentComment.get();
        // 如果父评论是一级评论，则它就是根评论；否则使用父评论的根评论ID
        rootCommentUuid = parent.getCommentLevel() == 1 ? parent.getCommentId() : parent.getRootCommentId();
    }
}
```

### 6. Controller层

**文件**: `src/main/java/com/example/cursorquitterweb/controller/CommentController.java`

**主要更改**:
- ✅ 导入了`CommentWithRepliesDTO`
- ✅ 更新了`createComment()`方法，支持自动判断创建评论还是回复
- ✅ 新增了8个API端点

**新增API端点**:

1. `GET /api/comments/post/{postId}/top-level` - 获取一级评论
2. `GET /api/comments/post/{postId}/top-level/page` - 分页获取一级评论
3. `GET /api/comments/{rootCommentId}/replies` - 获取某评论的所有回复
4. `GET /api/comments/post/{postId}/with-replies` - 获取评论及回复（小红书风格）★推荐
5. `GET /api/comments/post/{postId}/with-replies/page` - 分页获取评论及回复★推荐
6. `GET /api/comments/{rootCommentId}/replies/count` - 统计回复数量
7. `DELETE /api/comments/{commentId}/delete-with-replies` - 级联删除

## 功能特性

### 1. 智能评论创建

- 统一使用 `POST /api/comments/create` 端点
- 自动识别是创建一级评论还是回复评论
- 自动计算`root_comment_id`和`comment_level`

### 2. 小红书风格展示

使用 `GET /api/comments/post/{postId}/with-replies/page` 可以获取类似小红书的评论展示数据：

```
评论1（一级评论）
  ├─ 回复1.1：@用户A 的回复内容
  ├─ 回复1.2：@用户B 的回复内容
  └─ 回复1.3：@用户C 的回复内容
  
评论2（一级评论）
  ├─ 回复2.1：@用户D 的回复内容
  └─ 回复2.2：@用户E 的回复内容
```

### 3. 性能优化

- ✅ 使用批量查询避免N+1问题
- ✅ 合理的索引设计
- ✅ 分页支持
- ✅ 使用Stream和Lambda优化数据组装

### 4. 数据一致性

- ✅ 外键约束确保数据完整性
- ✅ 级联删除支持
- ✅ 软删除机制
- ✅ 自动更新时间戳

## 向后兼容性

### 原有API保持不变

所有原有的API端点继续正常工作：
- ✅ `POST /api/comments/create` - 不传回复字段时创建一级评论
- ✅ `GET /api/comments/{commentId}` - 获取单个评论
- ✅ `PUT /api/comments/{commentId}/update` - 更新评论
- ✅ `DELETE /api/comments/{commentId}/delete` - 删除评论
- ✅ `GET /api/comments/post/{postId}` - 获取帖子的所有评论（包括回复）
- ✅ `GET /api/comments/post/{postId}/page` - 分页获取帖子评论

### 数据迁移

对于已有的评论数据，需要执行以下SQL确保数据完整性：

```sql
-- 为所有现有评论设置comment_level=1（一级评论）
UPDATE comments 
SET comment_level = 1 
WHERE comment_level IS NULL OR comment_level = 0;

-- 确保所有现有评论的parent_comment_id、root_comment_id等字段为NULL
UPDATE comments 
SET parent_comment_id = NULL,
    reply_to_user_id = NULL,
    reply_to_user_nickname = NULL,
    reply_to_comment_id = NULL,
    root_comment_id = NULL
WHERE comment_level = 1;
```

## 使用建议

### 前端推荐用法

1. **显示帖子评论列表**：使用 `GET /api/comments/post/{postId}/with-replies/page`
   - 一次请求获取所有数据
   - 每个一级评论自动包含其所有回复
   - 支持分页

2. **创建评论**：使用 `POST /api/comments/create`
   - 直接评论帖子：不传`parentCommentId`
   - 回复评论：传入`parentCommentId`、`replyToUserId`等字段

3. **删除评论**：
   - 只删除单条：`DELETE /api/comments/{commentId}/delete`
   - 级联删除：`DELETE /api/comments/{commentId}/delete-with-replies`

### 前端显示格式建议

```jsx
{commentsWithReplies.map(item => (
  <div key={item.comment.commentId} className="comment">
    {/* 一级评论 */}
    <div className="comment-main">
      <Avatar src={item.comment.avatarUrl} />
      <div>
        <div className="comment-header">
          <span className="nickname">{item.comment.userNickname}</span>
          <span className="stage">{item.comment.userStage}</span>
        </div>
        <div className="comment-content">{item.comment.content}</div>
        <div className="comment-actions">
          <button onClick={() => handleReply(item.comment)}>回复</button>
        </div>
      </div>
    </div>
    
    {/* 回复列表 */}
    {item.replies && item.replies.length > 0 && (
      <div className="replies">
        {item.replies.map(reply => (
          <div key={reply.commentId} className="reply">
            <Avatar src={reply.avatarUrl} size="small" />
            <div>
              <span className="nickname">{reply.userNickname}</span>
              <span> 回复 </span>
              <span className="nickname">{reply.replyToUserNickname}</span>
              <span>：{reply.content}</span>
            </div>
          </div>
        ))}
      </div>
    )}
  </div>
))}
```

## 测试建议

### 测试场景

1. ✅ 创建一级评论
2. ✅ 回复一级评论
3. ✅ 回复二级评论（形成回复链）
4. ✅ 获取评论及回复列表
5. ✅ 删除一级评论（验证级联删除）
6. ✅ 统计回复数量
7. ✅ 分页功能
8. ✅ 性能测试（大量评论场景）

### 测试SQL

```sql
-- 查看帖子的评论结构
SELECT 
    c.comment_id,
    c.comment_level,
    c.user_nickname,
    c.content,
    c.parent_comment_id,
    c.root_comment_id,
    c.reply_to_user_nickname
FROM comments c
WHERE c.post_id = '你的帖子ID'
  AND c.is_deleted = false
ORDER BY 
    COALESCE(c.root_comment_id, c.comment_id),
    c.comment_level,
    c.created_at;
```

## 文档

详细的API文档请参考：`docs/comment_reply_api.md`

## 总结

本次更新完整实现了小红书风格的评论回复功能，所有代码已通过linter检查，无错误。主要改动包括：

- ✅ 1个实体类更新
- ✅ 1个DTO类更新 + 1个新DTO类
- ✅ 1个Repository接口更新（新增10个方法）
- ✅ 1个Service接口更新（新增8个方法）
- ✅ 1个ServiceImpl实现（新增8个方法实现）
- ✅ 1个Controller更新（更新1个方法 + 新增7个API端点）
- ✅ 完整的API文档
- ✅ 向后兼容所有原有功能

所有代码均已完成，可以直接使用！🎉

