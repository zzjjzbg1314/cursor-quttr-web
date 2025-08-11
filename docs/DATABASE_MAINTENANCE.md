# 数据库维护说明

## 概述

本项目已移除Flyway自动迁移功能，改为手动管理数据库结构。所有数据库变更需要手动执行SQL脚本。

## 数据库结构

### 当前表结构

1. **users表** - 用户信息
2. **posts表** - 帖子内容  
3. **comments表** - 评论信息
4. **post_likes表** - 帖子点赞统计

### 表关系

```
users (1) ←→ (N) posts (1) ←→ (N) comments
posts (1) ←→ (1) post_likes
```

## 初始化数据库

### 首次部署

1. 连接到PostgreSQL数据库
2. 执行 `docs/database_init.sql` 脚本
3. 验证表创建成功

```bash
# 使用psql连接数据库
psql -h your_host -U your_user -d your_database -f docs/database_init.sql
```

### 验证表创建

```sql
-- 检查表是否存在
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- 检查表结构
\d users
\d posts  
\d comments
```

## 数据库变更管理

### 添加新表

1. 创建新的SQL脚本文件
2. 在测试环境验证
3. 在生产环境执行
4. 更新文档

### 修改现有表

1. 创建ALTER语句脚本
2. 备份相关数据
3. 在测试环境验证
4. 在生产环境执行

### 示例：添加新字段

```sql
-- 为posts表添加新字段
ALTER TABLE posts ADD COLUMN tags TEXT[];
ALTER TABLE posts ADD COLUMN view_count INTEGER DEFAULT 0;

-- 创建索引
CREATE INDEX idx_posts_tags ON posts USING GIN(tags);
CREATE INDEX idx_posts_view_count ON posts(view_count);
```

## 备份策略

### 定期备份

```bash
# 全库备份
pg_dump -h host -U user -d database > backup_$(date +%Y%m%d).sql

# 单表备份
pg_dump -h host -U user -d database -t table_name > table_backup.sql
```

### 恢复数据

```bash
# 恢复全库
psql -h host -U user -d database < backup_file.sql

# 恢复单表
psql -h host -U user -d database < table_backup.sql
```

## 性能优化

### 索引维护

```sql
-- 查看索引使用情况
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- 重建索引
REINDEX INDEX index_name;
```

### 表统计信息

```sql
-- 更新表统计信息
ANALYZE table_name;

-- 查看表大小
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

## 监控和维护

### 连接数监控

```sql
-- 查看当前连接数
SELECT count(*) FROM pg_stat_activity;

-- 查看长时间运行的查询
SELECT pid, now() - pg_stat_activity.query_start AS duration, query 
FROM pg_stat_activity 
WHERE (now() - pg_stat_activity.query_start) > interval '5 minutes';
```

### 日志分析

```sql
-- 查看慢查询
SELECT query, calls, total_time, mean_time
FROM pg_stat_statements 
ORDER BY mean_time DESC 
LIMIT 10;
```

## 注意事项

1. **生产环境变更前必须备份**
2. **重要变更先在测试环境验证**
3. **记录所有数据库变更操作**
4. **定期检查数据库性能指标**
5. **保持数据库版本与代码版本同步**

## 常见问题

### Q: 如何回滚表结构变更？
A: 使用备份恢复，或手动执行反向SQL语句

### Q: 如何添加新的索引？
A: 创建CREATE INDEX语句，在低峰期执行

### Q: 如何优化查询性能？
A: 分析执行计划，添加合适的索引，优化SQL语句

## 联系信息

如有数据库相关问题，请联系数据库管理员或开发团队。
