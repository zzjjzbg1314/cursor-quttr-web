-- 索引优化建议
-- 基于实际查询模式分析，为Cloudflare D1数据库创建索引以提升性能

-- ============================================
-- posts表索引优化
-- ============================================

-- 1. 最常用查询：获取所有帖子（分页，按创建时间排序）
-- 查询模式：WHERE is_deleted = ? ORDER BY created_at DESC
-- 索引：覆盖is_deleted和created_at，支持快速过滤和排序
CREATE INDEX IF NOT EXISTS idx_posts_is_deleted_created_at 
ON posts(is_deleted, created_at DESC);

-- 2. 按用户ID查询帖子
-- 查询模式：WHERE user_id = ? AND is_deleted = ? ORDER BY created_at DESC
-- 索引：覆盖user_id、is_deleted和created_at
CREATE INDEX IF NOT EXISTS idx_posts_user_id_is_deleted_created_at 
ON posts(user_id, is_deleted, created_at DESC);

-- 3. 按用户昵称查询帖子
-- 查询模式：WHERE user_nickname = ? AND is_deleted = ? ORDER BY created_at DESC
-- 索引：覆盖user_nickname、is_deleted和created_at
CREATE INDEX IF NOT EXISTS idx_posts_user_nickname_is_deleted_created_at 
ON posts(user_nickname, is_deleted, created_at DESC);

-- 4. 按用户阶段查询帖子
-- 查询模式：WHERE user_stage = ? AND is_deleted = ? ORDER BY created_at DESC
-- 索引：覆盖user_stage、is_deleted和created_at
CREATE INDEX IF NOT EXISTS idx_posts_user_stage_is_deleted_created_at 
ON posts(user_stage, is_deleted, created_at DESC);

-- 5. 按更新时间排序（支持动态排序）
-- 查询模式：WHERE is_deleted = ? ORDER BY updated_at DESC
-- 索引：覆盖is_deleted和updated_at
CREATE INDEX IF NOT EXISTS idx_posts_is_deleted_updated_at 
ON posts(is_deleted, updated_at DESC);

-- 注意：post_id是主键，已经有索引，不需要额外创建

-- ============================================
-- post_likes表索引优化
-- ============================================

-- post_id是主键，已经有索引
-- JOIN查询：LEFT JOIN post_likes pl ON p.post_id = pl.post_id
-- 主键索引已经足够，不需要额外索引

-- 如果需要按点赞数排序，可以考虑：
-- CREATE INDEX IF NOT EXISTS idx_post_likes_like_count 
-- ON post_likes(like_count DESC);

-- ============================================
-- comments表索引优化
-- ============================================

-- 1. 批量查询评论数（最关键的优化）
-- 查询模式：WHERE post_id IN (...) AND is_deleted = ? GROUP BY post_id
-- 这是优化后的getAllPostsWithUpvotesAndCount方法中的子查询
-- 索引：覆盖post_id和is_deleted，支持快速过滤和分组
CREATE INDEX IF NOT EXISTS idx_comments_post_id_is_deleted 
ON comments(post_id, is_deleted);

-- 2. 按帖子ID查询评论（一级评论）
-- 查询模式：WHERE post_id = ? AND is_deleted = ? ORDER BY created_at ASC
-- 索引：覆盖post_id、is_deleted和created_at
CREATE INDEX IF NOT EXISTS idx_comments_post_id_is_deleted_created_at 
ON comments(post_id, is_deleted, created_at ASC);

-- 3. 按帖子ID和评论级别查询（一级评论分页）
-- 查询模式：WHERE post_id = ? AND comment_level = ? AND is_deleted = ? ORDER BY created_at ASC
-- 索引：覆盖post_id、comment_level、is_deleted和created_at
CREATE INDEX IF NOT EXISTS idx_comments_post_id_level_is_deleted_created_at 
ON comments(post_id, comment_level, is_deleted, created_at ASC);

-- 4. 查询回复（按根评论ID）
-- 查询模式：WHERE root_comment_id = ? AND comment_level = ? AND is_deleted = ? ORDER BY created_at ASC
-- 索引：覆盖root_comment_id、comment_level、is_deleted和created_at
CREATE INDEX IF NOT EXISTS idx_comments_root_comment_id_level_is_deleted_created_at 
ON comments(root_comment_id, comment_level, is_deleted, created_at ASC);

-- 5. 按用户ID查询评论
-- 查询模式：WHERE user_id = ? AND is_deleted = ? ORDER BY created_at DESC
-- 索引：覆盖user_id、is_deleted和created_at
CREATE INDEX IF NOT EXISTS idx_comments_user_id_is_deleted_created_at 
ON comments(user_id, is_deleted, created_at DESC);

-- 6. 获取所有评论（分页）
-- 查询模式：WHERE is_deleted = ? ORDER BY created_at DESC
-- 索引：覆盖is_deleted和created_at
CREATE INDEX IF NOT EXISTS idx_comments_is_deleted_created_at 
ON comments(is_deleted, created_at DESC);

-- 7. 按用户昵称查询评论
-- 查询模式：WHERE user_nickname = ? AND is_deleted = ? ORDER BY created_at DESC
-- 索引：覆盖user_nickname、is_deleted和created_at
CREATE INDEX IF NOT EXISTS idx_comments_user_nickname_is_deleted_created_at 
ON comments(user_nickname, is_deleted, created_at DESC);

-- 8. 按用户阶段查询评论
-- 查询模式：WHERE user_stage = ? AND is_deleted = ? ORDER BY created_at DESC
-- 索引：覆盖user_stage、is_deleted和created_at
CREATE INDEX IF NOT EXISTS idx_comments_user_stage_is_deleted_created_at 
ON comments(user_stage, is_deleted, created_at DESC);

-- 注意：comment_id是主键，已经有索引，不需要额外创建

-- ============================================
-- 索引创建优先级说明
-- ============================================

-- 高优先级（立即创建，对性能影响最大）：
-- 1. idx_posts_is_deleted_created_at - 优化获取帖子列表（最常用）
-- 2. idx_comments_post_id_is_deleted - 优化批量查询评论数（JOIN查询中的子查询）
-- 3. idx_comments_post_id_is_deleted_created_at - 优化获取帖子评论列表

-- 中优先级（根据实际使用情况创建）：
-- 4. idx_posts_user_id_is_deleted_created_at - 优化按用户查询帖子
-- 5. idx_comments_post_id_level_is_deleted_created_at - 优化评论分页查询
-- 6. idx_comments_root_comment_id_level_is_deleted_created_at - 优化回复查询

-- 低优先级（按需创建）：
-- 其他索引根据实际查询频率决定是否创建

-- ============================================
-- 索引维护建议
-- ============================================

-- 1. 定期分析索引使用情况
-- 2. 删除未使用的索引以节省存储空间
-- 3. 监控索引大小，避免过度索引
-- 4. 对于Cloudflare D1，索引会自动维护，无需手动操作

-- ============================================
-- 性能预期
-- ============================================

-- 创建这些索引后，预期性能提升：
-- 1. getAllPostsWithUpvotesAndCount: 从999-1461ms降低到200-400ms
-- 2. 批量查询评论数: 从数百毫秒降低到几十毫秒
-- 3. 按用户查询帖子: 从数百毫秒降低到几十毫秒
-- 4. 获取评论列表: 从数百毫秒降低到几十毫秒

