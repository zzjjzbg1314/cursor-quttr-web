-- 仅供审查；未在线执行，也不挂入自动迁移。
-- 先核查 PRAGMA index_list / index_info，若已有同前缀等价索引，不重复创建。
-- 评论索引在旧建议文档中已存在，此处用于明确本轮查询计划依赖。
CREATE INDEX IF NOT EXISTS idx_comments_post_id_is_deleted ON comments(post_id, is_deleted);
-- 音乐分页按创建时间排序；需先在目标测试数据库验证真实规模、写入开销与查询计划。
CREATE INDEX IF NOT EXISTS idx_music_create_at ON music(create_at);
