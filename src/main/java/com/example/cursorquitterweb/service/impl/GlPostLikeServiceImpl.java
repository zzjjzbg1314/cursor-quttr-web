package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.GlPostLike;
import com.example.cursorquitterweb.service.GlPostLikeService;
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
 * 海外帖子点赞服务实现类
 */
@Service
public class GlPostLikeServiceImpl implements GlPostLikeService {

    @Autowired
    private CloudflareD1Util d1Util;

    @Override
    public Optional<GlPostLike> findByPostId(UUID postId) {
        String sql = "SELECT * FROM post_likes_gl WHERE post_id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(postId));
        return row != null ? Optional.of(mapToPostLike(row)) : Optional.empty();
    }

    @Override
    public GlPostLike createPostLike(UUID postId) {
        GlPostLike postLike = new GlPostLike();
        postLike.setPostId(postId);
        return savePostLike(postLike);
    }

    @Override
    public GlPostLike likePost(UUID postId) {
        Optional<GlPostLike> existingLike = findByPostId(postId);
        if (existingLike.isPresent()) {
            String sql = "UPDATE post_likes_gl SET like_count = like_count + 1, updated_at = ? WHERE post_id = ?";
            d1Util.execute(sql, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(postId));
            return findByPostId(postId).orElse(null);
        }
        return savePostLike(new GlPostLike(postId, 1));
    }

    @Override
    public GlPostLike unlikePost(UUID postId) {
        Optional<GlPostLike> existingLike = findByPostId(postId);
        if (existingLike.isPresent()) {
            String sql = "UPDATE post_likes_gl SET like_count = CASE WHEN like_count - 1 < 0 THEN 0 ELSE like_count - 1 END, updated_at = ? WHERE post_id = ?";
            d1Util.execute(sql, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(postId));
            return findByPostId(postId).orElse(null);
        }
        return null;
    }

    @Override
    public GlPostLike setLikeCount(UUID postId, Integer likeCount) {
        if (likeCount < 0) {
            throw new IllegalArgumentException("点赞数不能为负数");
        }
        Optional<GlPostLike> existingLike = findByPostId(postId);
        if (existingLike.isPresent()) {
            String sql = "UPDATE post_likes_gl SET like_count = ?, updated_at = ? WHERE post_id = ?";
            d1Util.execute(sql, likeCount, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(postId));
            return findByPostId(postId).orElse(null);
        }
        return savePostLike(new GlPostLike(postId, likeCount));
    }

    @Override
    public GlPostLike resetLikeCount(UUID postId) {
        return setLikeCount(postId, 0);
    }

    @Override
    public Integer getLikeCount(UUID postId) {
        Optional<GlPostLike> postLike = findByPostId(postId);
        return postLike.map(GlPostLike::getLikeCount).orElse(0);
    }

    @Override
    public Map<UUID, Integer> getLikeCountsBatch(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new HashMap<>();
        }
        String placeholders = postIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> params = postIds.stream().map(EntityMapper::uuidToString).collect(Collectors.toList());
        String sql = "SELECT post_id, like_count FROM post_likes_gl WHERE post_id IN (" + placeholders + ")";
        List<Map<String, Object>> rows = d1Util.queryList(sql, params.toArray());
        Map<UUID, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID postId = EntityMapper.getUUID(row, "post_id");
            Integer likeCount = EntityMapper.getInteger(row, "like_count");
            if (postId != null) {
                result.put(postId, likeCount != null ? likeCount : 0);
            }
        }
        for (UUID postId : postIds) {
            result.putIfAbsent(postId, 0);
        }
        return result;
    }

    @Override
    public List<GlPostLike> findByLikeCountRange(Integer minCount, Integer maxCount) {
        if (minCount == null) minCount = 0;
        if (maxCount == null) maxCount = Integer.MAX_VALUE;
        String sql = "SELECT * FROM post_likes_gl WHERE like_count >= ? AND like_count <= ? ORDER BY like_count DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, minCount, maxCount);
        return rows.stream().map(this::mapToPostLike).collect(Collectors.toList());
    }

    @Override
    public List<GlPostLike> findByLikeCountGreaterThan(Integer minCount) {
        if (minCount == null) minCount = 0;
        String sql = "SELECT * FROM post_likes_gl WHERE like_count > ? ORDER BY like_count DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, minCount);
        return rows.stream().map(this::mapToPostLike).collect(Collectors.toList());
    }

    @Override
    public List<GlPostLike> findByLikeCountLessThan(Integer maxCount) {
        if (maxCount == null) maxCount = Integer.MAX_VALUE;
        String sql = "SELECT * FROM post_likes_gl WHERE like_count < ? ORDER BY like_count DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, maxCount);
        return rows.stream().map(this::mapToPostLike).collect(Collectors.toList());
    }

    @Override
    public List<GlPostLike> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM post_likes_gl WHERE updated_at >= ? AND updated_at <= ? ORDER BY updated_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql,
            EntityMapper.offsetDateTimeToString(startTime),
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToPostLike).collect(Collectors.toList());
    }

    @Override
    public Long getTotalLikeCount() {
        String sql = "SELECT SUM(like_count) as total FROM post_likes_gl";
        Object total = d1Util.querySingleValue(sql);
        return total != null ? ((Number) total).longValue() : 0L;
    }

    @Override
    public Double getAverageLikeCount() {
        String sql = "SELECT AVG(like_count) as avg FROM post_likes_gl";
        Object avg = d1Util.querySingleValue(sql);
        return avg != null ? ((Number) avg).doubleValue() : 0.0;
    }

    @Override
    public List<GlPostLike> getTopLikedPosts() {
        return getTopLikedPosts(10);
    }

    @Override
    public List<GlPostLike> getTopLikedPosts(int limit) {
        String sql = "SELECT * FROM post_likes_gl ORDER BY like_count DESC LIMIT ?";
        List<Map<String, Object>> rows = d1Util.queryList(sql, limit);
        return rows.stream().map(this::mapToPostLike).collect(Collectors.toList());
    }

    @Override
    public List<GlPostLike> createPostLikesForPosts(List<UUID> postIds) {
        List<GlPostLike> createdLikes = new ArrayList<>();
        for (UUID postId : postIds) {
            Optional<GlPostLike> existing = findByPostId(postId);
            if (!existing.isPresent()) {
                createdLikes.add(savePostLike(new GlPostLike(postId)));
            }
        }
        return createdLikes;
    }

    @Override
    public void deletePostLike(UUID postId) {
        d1Util.deleteById("post_likes_gl", "post_id", EntityMapper.uuidToString(postId));
    }

    private GlPostLike savePostLike(GlPostLike postLike) {
        if (postLike.getPostId() == null) {
            throw new IllegalArgumentException("postId cannot be null");
        }
        postLike.setUpdatedAt(OffsetDateTime.now());
        Map<String, Object> data = postLikeToMap(postLike);
        if (findByPostId(postLike.getPostId()).isPresent()) {
            d1Util.updateById("post_likes_gl", data, "post_id", EntityMapper.uuidToString(postLike.getPostId()));
        } else {
            d1Util.insert("post_likes_gl", data);
        }
        return postLike;
    }

    private GlPostLike mapToPostLike(Map<String, Object> row) {
        GlPostLike postLike = new GlPostLike();
        postLike.setPostId(EntityMapper.getUUID(row, "post_id"));
        postLike.setLikeCount(EntityMapper.getInteger(row, "like_count"));
        postLike.setUpdatedAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
        return postLike;
    }

    private Map<String, Object> postLikeToMap(GlPostLike postLike) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "post_id", postLike.getPostId());
        EntityMapper.putIfNotNull(data, "like_count", postLike.getLikeCount());
        EntityMapper.putIfNotNull(data, "updated_at", postLike.getUpdatedAt());
        return data;
    }
}
