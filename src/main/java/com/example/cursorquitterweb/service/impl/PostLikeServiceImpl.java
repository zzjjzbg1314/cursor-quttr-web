package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.PostLike;
import com.example.cursorquitterweb.service.PostLikeService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 帖子点赞服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class PostLikeServiceImpl implements PostLikeService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public Optional<PostLike> findByPostId(UUID postId) {
        String sql = "SELECT * FROM post_likes WHERE post_id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(postId));
        return row != null ? Optional.of(mapToPostLike(row)) : Optional.empty();
    }
    
    @Override
    public PostLike createPostLike(UUID postId) {
        PostLike postLike = new PostLike(postId);
        return savePostLike(postLike);
    }
    
    @Override
    public PostLike likePost(UUID postId) {
        Optional<PostLike> existingLike = findByPostId(postId);
        if (existingLike.isPresent()) {
            // 如果记录存在，增加点赞数
            String sql = "UPDATE post_likes SET like_count = like_count + 1, updated_at = ? WHERE post_id = ?";
            d1Util.execute(sql, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(postId));
            return findByPostId(postId).orElse(null);
        } else {
            // 如果记录不存在，创建新记录并设置点赞数为1
            PostLike newLike = new PostLike(postId, 1);
            return savePostLike(newLike);
        }
    }
    
    @Override
    public PostLike unlikePost(UUID postId) {
        Optional<PostLike> existingLike = findByPostId(postId);
        if (existingLike.isPresent()) {
            // 减少点赞数（不能小于0）
            String sql = "UPDATE post_likes SET like_count = CASE WHEN like_count - 1 < 0 THEN 0 ELSE like_count - 1 END, updated_at = ? WHERE post_id = ?";
            d1Util.execute(sql, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(postId));
            return findByPostId(postId).orElse(null);
        }
        return null;
    }
    
    @Override
    public PostLike setLikeCount(UUID postId, Integer likeCount) {
        if (likeCount < 0) {
            throw new IllegalArgumentException("点赞数不能为负数");
        }
        
        Optional<PostLike> existingLike = findByPostId(postId);
        if (existingLike.isPresent()) {
            String sql = "UPDATE post_likes SET like_count = ?, updated_at = ? WHERE post_id = ?";
            d1Util.execute(sql, likeCount, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(postId));
            return findByPostId(postId).orElse(null);
        } else {
            PostLike newLike = new PostLike(postId, likeCount);
            return savePostLike(newLike);
        }
    }
    
    @Override
    public PostLike resetLikeCount(UUID postId) {
        return setLikeCount(postId, 0);
    }
    
    @Override
    public Integer getLikeCount(UUID postId) {
        Optional<PostLike> postLike = findByPostId(postId);
        return postLike.map(PostLike::getLikeCount).orElse(0);
    }
    
    @Override
    public Map<UUID, Integer> getLikeCountsBatch(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new HashMap<>();
        }
        
        // 构建 IN 子句的占位符
        String placeholders = postIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));
        
        // 构建参数列表
        List<Object> params = postIds.stream()
                .map(EntityMapper::uuidToString)
                .collect(Collectors.toList());
        
        String sql = "SELECT post_id, like_count FROM post_likes WHERE post_id IN (" + placeholders + ")";
        List<Map<String, Object>> rows = d1Util.queryList(sql, params.toArray());
        
        // 构建结果 Map
        Map<UUID, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID postId = EntityMapper.getUUID(row, "post_id");
            Integer likeCount = EntityMapper.getInteger(row, "like_count");
            if (postId != null) {
                result.put(postId, likeCount != null ? likeCount : 0);
            }
        }
        
        // 对于没有点赞记录的帖子，默认返回 0
        for (UUID postId : postIds) {
            result.putIfAbsent(postId, 0);
        }
        
        return result;
    }
    
    @Override
    public List<PostLike> findByLikeCountRange(Integer minCount, Integer maxCount) {
        if (minCount == null) minCount = 0;
        if (maxCount == null) maxCount = Integer.MAX_VALUE;
        String sql = "SELECT * FROM post_likes WHERE like_count >= ? AND like_count <= ? ORDER BY like_count DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, minCount, maxCount);
        return rows.stream().map(this::mapToPostLike).collect(Collectors.toList());
    }
    
    @Override
    public List<PostLike> findByLikeCountGreaterThan(Integer minCount) {
        if (minCount == null) minCount = 0;
        String sql = "SELECT * FROM post_likes WHERE like_count > ? ORDER BY like_count DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, minCount);
        return rows.stream().map(this::mapToPostLike).collect(Collectors.toList());
    }
    
    @Override
    public List<PostLike> findByLikeCountLessThan(Integer maxCount) {
        if (maxCount == null) maxCount = Integer.MAX_VALUE;
        String sql = "SELECT * FROM post_likes WHERE like_count < ? ORDER BY like_count DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, maxCount);
        return rows.stream().map(this::mapToPostLike).collect(Collectors.toList());
    }
    
    @Override
    public List<PostLike> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM post_likes WHERE updated_at >= ? AND updated_at <= ? ORDER BY updated_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToPostLike).collect(Collectors.toList());
    }
    
    @Override
    public Long getTotalLikeCount() {
        String sql = "SELECT SUM(like_count) as total FROM post_likes";
        Object total = d1Util.querySingleValue(sql);
        return total != null ? ((Number) total).longValue() : 0L;
    }
    
    @Override
    public Double getAverageLikeCount() {
        String sql = "SELECT AVG(like_count) as avg FROM post_likes";
        Object avg = d1Util.querySingleValue(sql);
        return avg != null ? ((Number) avg).doubleValue() : 0.0;
    }
    
    @Override
    public List<PostLike> getTopLikedPosts() {
        return getTopLikedPosts(10);
    }
    
    @Override
    public List<PostLike> getTopLikedPosts(int limit) {
        String sql = "SELECT * FROM post_likes ORDER BY like_count DESC LIMIT ?";
        List<Map<String, Object>> rows = d1Util.queryList(sql, limit);
        return rows.stream().map(this::mapToPostLike).collect(Collectors.toList());
    }
    
    @Override
    public List<PostLike> createPostLikesForPosts(List<UUID> postIds) {
        List<PostLike> createdLikes = new ArrayList<>();
        for (UUID postId : postIds) {
            Optional<PostLike> existing = findByPostId(postId);
            if (!existing.isPresent()) {
                PostLike newLike = new PostLike(postId);
                createdLikes.add(savePostLike(newLike));
            }
        }
        return createdLikes;
    }
    
    @Override
    public void deletePostLike(UUID postId) {
        d1Util.deleteById("post_likes", "post_id", EntityMapper.uuidToString(postId));
    }
    
    /**
     * 保存帖子点赞
     */
    private PostLike savePostLike(PostLike postLike) {
        if (postLike.getPostId() == null) {
            throw new IllegalArgumentException("postId cannot be null");
        }
        
        // 检查是否已存在
        Optional<PostLike> existing = findByPostId(postLike.getPostId());
        if (existing.isPresent()) {
            // 更新记录
            postLike.setUpdatedAt(OffsetDateTime.now());
            Map<String, Object> data = postLikeToMap(postLike);
            d1Util.updateById("post_likes", data, "post_id", EntityMapper.uuidToString(postLike.getPostId()));
            return postLike;
        } else {
            // 插入新记录
            postLike.setUpdatedAt(OffsetDateTime.now());
            Map<String, Object> data = postLikeToMap(postLike);
            d1Util.insert("post_likes", data);
            return postLike;
        }
    }
    
    /**
     * 将 Map 转换为 PostLike 实体
     */
    private PostLike mapToPostLike(Map<String, Object> row) {
        PostLike postLike = new PostLike();
        postLike.setPostId(EntityMapper.getUUID(row, "post_id"));
        postLike.setLikeCount(EntityMapper.getInteger(row, "like_count"));
        postLike.setUpdatedAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
        return postLike;
    }
    
    /**
     * 将 PostLike 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> postLikeToMap(PostLike postLike) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "post_id", postLike.getPostId());
        EntityMapper.putIfNotNull(data, "like_count", postLike.getLikeCount());
        EntityMapper.putIfNotNull(data, "updated_at", postLike.getUpdatedAt());
        return data;
    }
}
