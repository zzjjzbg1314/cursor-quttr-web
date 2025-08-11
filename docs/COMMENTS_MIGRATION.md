# 评论表结构迁移指南

## 概述

本次迁移将评论表（comments）的ID字段从 `BIGINT` 类型改为 `UUID` 类型，以保持与用户表（users）和帖子表（posts）的一致性。

## 变更内容

### 数据库表结构变更

- `comment_id`: `BIGINT` → `UUID` (主键)
- `post_id`: `BIGINT` → `UUID` (外键，引用posts表)
- `user_id`: `BIGINT` → `UUID` (外键，引用users表)

### 代码变更

- 更新了 `Comment` 实体类
- 更新了 `CreateCommentRequest` DTO
- 更新了 `CommentService` 接口
- 更新了 `CommentServiceImpl` 实现类
- 更新了 `CommentRepository` 接口
- 更新了 `CommentController` 控制器

## 迁移步骤

### 1. 备份现有数据

在执行迁移之前，请务必备份现有的评论数据：

```sql
-- 备份现有评论表
CREATE TABLE comments_backup AS SELECT * FROM comments;

-- 备份相关的外键关系
-- 如果有其他表引用了comments表，也需要备份
```

### 2. 执行结构迁移

运行 `V2__update_comments_table_structure.sql` 脚本：

```sql
-- 这个脚本会：
-- 1. 删除外键约束和索引
-- 2. 删除旧表
-- 3. 创建新表结构
-- 4. 重新创建索引和约束
```

### 3. 数据迁移

如果现有表中有数据，需要执行数据迁移：

1. 分析现有数据的ID映射关系
2. 根据业务逻辑设计UUID生成策略
3. 执行 `V3__migrate_comments_data.sql` 脚本

### 4. 验证迁移结果

```sql
-- 检查表结构
\d comments

-- 检查数据完整性
SELECT COUNT(*) FROM comments;

-- 检查外键约束
SELECT 
    tc.table_name, 
    kcu.column_name, 
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name 
FROM 
    information_schema.table_constraints AS tc 
    JOIN information_schema.key_column_usage AS kcu
      ON tc.constraint_name = kcu.constraint_name
      AND tc.table_schema = kcu.table_schema
    JOIN information_schema.constraint_column_usage AS ccu
      ON ccu.constraint_name = tc.constraint_name
      AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_name='comments';
```

## 注意事项

### 1. 数据一致性

- 确保 `posts` 表和 `users` 表已经使用UUID类型
- 检查是否有其他表引用了 `comments` 表
- 验证外键关系的完整性

### 2. 应用程序兼容性

- 更新后的API接口使用UUID类型的ID
- 前端代码需要相应更新
- 测试所有相关的API端点

### 3. 性能考虑

- UUID索引的性能可能比BIGINT索引稍差
- 考虑使用 `uuid-ossp` 扩展的 `gen_random_uuid()` 函数
- 监控查询性能，必要时调整索引策略

## 回滚计划

如果迁移过程中出现问题，可以按以下步骤回滚：

```sql
-- 1. 删除新表
DROP TABLE IF EXISTS comments;

-- 2. 恢复备份表
ALTER TABLE comments_backup RENAME TO comments;

-- 3. 重新创建索引和约束
-- 根据原始表结构重新创建
```

## 测试建议

1. 在测试环境中先执行完整的迁移流程
2. 验证所有API端点的功能
3. 测试数据的一致性和完整性
4. 性能测试，确保查询性能满足要求
5. 制定详细的测试用例和验收标准

## 联系信息

如果在迁移过程中遇到问题，请联系开发团队或数据库管理员。
