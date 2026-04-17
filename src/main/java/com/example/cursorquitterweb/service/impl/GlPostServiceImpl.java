package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.PostPageResult;
import com.example.cursorquitterweb.dto.PostWithUpvotesDto;
import com.example.cursorquitterweb.entity.GlPost;
import com.example.cursorquitterweb.service.GlCommentService;
import com.example.cursorquitterweb.service.GlPostLikeService;
import com.example.cursorquitterweb.service.GlPostService;
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

@Service
public class GlPostServiceImpl implements GlPostService {

    @Autowired
    private CloudflareD1Util d1Util;

    @Autowired
    private GlPostLikeService postLikeService;

    @Autowired
    private GlCommentService commentService;

    @Override
    public Optional<GlPost> findById(UUID postId) {
        String sql = "SELECT * FROM posts_gl WHERE post_id = ? AND is_deleted = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(postId), false);
        return row != null ? Optional.of(mapToPost(row)) : Optional.empty();
    }

    @Override
    public Optional<PostWithUpvotesDto> findByIdWithUpvotes(UUID postId) {
        Optional<GlPost> postOpt = findById(postId);
        return postOpt.map(this::convertToPostWithUpvotesDto);
    }

    @Override
    public GlPost createPost(UUID userId, String userNickname, String userStage, String content) {
        return savePost(new GlPost(userId, userNickname, userStage, content));
    }

    @Override
    public GlPost createPost(UUID userId, String userNickname, String userStage, String avatarUrl, String content) {
        return savePost(new GlPost(userId, userNickname, userStage, avatarUrl, content));
    }

    @Override
    public GlPost updatePost(UUID postId, String content) {
        GlPost post = findById(postId).orElseThrow(() -> new RuntimeException("帖子不存在或已被删除"));
        post.setContent(content);
        post.preUpdate();
        return savePost(post);
    }

    @Override
    public void deletePost(UUID postId) {
        String sql = "UPDATE posts_gl SET is_deleted = ?, updated_at = ? WHERE post_id = ?";
        d1Util.execute(sql, true, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(postId));
    }

    @Override
    public List<GlPost> findByUserId(UUID userId) {
        String sql = "SELECT * FROM posts_gl WHERE user_id = ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, EntityMapper.uuidToString(userId), false).stream().map(this::mapToPost).collect(Collectors.toList());
    }

    @Override
    public List<GlPost> findByUserId(UUID userId, int page, int size) {
        String sql = "SELECT * FROM posts_gl WHERE user_id = ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(userId), false).stream()
            .map(this::mapToPost).collect(Collectors.toList());
    }

    @Override
    public List<PostWithUpvotesDto> findByUserIdWithUpvotes(UUID userId, int page, int size) {
        return findByUserId(userId, page, size).stream().map(this::convertToPostWithUpvotesDto).collect(Collectors.toList());
    }

    @Override
    public List<GlPost> findByUserNickname(String userNickname) {
        String sql = "SELECT * FROM posts_gl WHERE user_nickname = ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, userNickname, false).stream().map(this::mapToPost).collect(Collectors.toList());
    }

    @Override
    public List<GlPost> findByUserStage(String userStage) {
        String sql = "SELECT * FROM posts_gl WHERE user_stage = ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, userStage, false).stream().map(this::mapToPost).collect(Collectors.toList());
    }

    @Override
    public List<GlPost> searchByContent(String content) {
        String sql = "SELECT * FROM posts_gl WHERE LOWER(content) LIKE LOWER(?) AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, "%" + content + "%", false).stream().map(this::mapToPost).collect(Collectors.toList());
    }

    @Override
    public List<GlPost> getAllPosts(int page, int size) {
        String sql = "SELECT * FROM posts_gl WHERE is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, false).stream().map(this::mapToPost).collect(Collectors.toList());
    }

    @Override
    public List<GlPost> getAllPosts() {
        String sql = "SELECT * FROM posts_gl WHERE is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, false).stream().map(this::mapToPost).collect(Collectors.toList());
    }

    @Override
    public List<PostWithUpvotesDto> getAllPostsWithUpvotes(int page, int size) {
        return getAllPosts(page, size).stream().map(this::convertToPostWithUpvotesDto).collect(Collectors.toList());
    }

    @Override
    public List<PostWithUpvotesDto> getAllPostsWithUpvotes(int page, int size, String sortBy, String sortDir) {
        return getAllPosts(page, size, sortBy, sortDir).stream().map(this::convertToPostWithUpvotesDto).collect(Collectors.toList());
    }

    @Override
    public PostPageResult getAllPostsWithUpvotesAndCount(int page, int size, String sortBy, String sortDir) {
        String validSortBy = validateSortField(sortBy);
        String validSortDir = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";
        String sql = String.format(
            "SELECT p.post_id, p.user_id, p.user_nickname, p.user_stage, p.avatar_url, p.content, p.is_deleted, p.created_at, p.updated_at, " +
            "COALESCE(pl.like_count, 0) as upvotes, COALESCE(comment_counts.comment_count, 0) as comment_count, COUNT(*) OVER() as total_count " +
            "FROM posts_gl p " +
            "LEFT JOIN post_likes_gl pl ON p.post_id = pl.post_id " +
            "LEFT JOIN (SELECT post_id, COUNT(*) as comment_count FROM comments_gl WHERE is_deleted = ? GROUP BY post_id) comment_counts ON p.post_id = comment_counts.post_id " +
            "WHERE p.is_deleted = ? ORDER BY p.%s %s LIMIT ? OFFSET ?",
            validSortBy, validSortDir
        );

        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, false, false, size, offset);

        long totalElements = 0;
        List<PostWithUpvotesDto> content = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }
            GlPost post = new GlPost();
            post.setPostId(EntityMapper.getUUID(row, "post_id"));
            post.setUserId(EntityMapper.getUUID(row, "user_id"));
            post.setUserNickname(EntityMapper.getString(row, "user_nickname"));
            post.setUserStage(EntityMapper.getString(row, "user_stage"));
            post.setAvatarUrl(EntityMapper.getString(row, "avatar_url"));
            post.setContent(EntityMapper.getString(row, "content"));
            Object isDeletedObj = row.get("is_deleted");
            if (isDeletedObj instanceof Boolean) {
                post.setIsDeleted((Boolean) isDeletedObj);
            } else if (isDeletedObj instanceof Number) {
                post.setIsDeleted(((Number) isDeletedObj).intValue() != 0);
            }
            post.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
            post.setUpdatedAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
            Integer upvotes = row.get("upvotes") != null ? ((Number) row.get("upvotes")).intValue() : 0;
            Integer commentCount = row.get("comment_count") != null ? ((Number) row.get("comment_count")).intValue() : 0;
            content.add(new PostWithUpvotesDto(
                post.getPostId(), post.getUserId(), post.getUserNickname(), post.getUserStage(), post.getAvatarUrl(),
                post.getContent(), post.getIsDeleted(), post.getCreatedAt(), post.getUpdatedAt(), upvotes, commentCount
            ));
        }
        return new PostPageResult(content, totalElements);
    }

    @Override
    public List<PostWithUpvotesDto> getAllPostsWithUpvotes() {
        return getAllPosts().stream().map(this::convertToPostWithUpvotesDto).collect(Collectors.toList());
    }

    @Override
    public List<GlPost> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM posts_gl WHERE created_at >= ? AND created_at <= ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql,
            EntityMapper.offsetDateTimeToString(startTime),
            EntityMapper.offsetDateTimeToString(endTime),
            false).stream().map(this::mapToPost).collect(Collectors.toList());
    }

    @Override
    public long countByUserId(UUID userId) {
        return d1Util.queryLong("SELECT COUNT(*) as count FROM posts_gl WHERE user_id = ? AND is_deleted = ?",
            EntityMapper.uuidToString(userId), false);
    }

    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return d1Util.queryLong("SELECT COUNT(*) as count FROM posts_gl WHERE created_at >= ? AND created_at <= ? AND is_deleted = ?",
            EntityMapper.offsetDateTimeToString(startTime), EntityMapper.offsetDateTimeToString(endTime), false);
    }

    @Override
    public long count() {
        return d1Util.queryLong("SELECT COUNT(*) as count FROM posts_gl WHERE is_deleted = ?", false);
    }

    @Override
    public boolean existsByContent(String content) {
        return d1Util.queryLong("SELECT COUNT(*) as count FROM posts_gl WHERE content = ? AND is_deleted = ?", content, false) > 0;
    }

    private GlPost savePost(GlPost post) {
        if (post.getPostId() == null) {
            if (post.getContent() != null && existsByContent(post.getContent())) {
                throw new RuntimeException("帖子内容已存在，无法重复创建");
            }
            post.setPostId(UUID.randomUUID());
            post.setCreatedAt(OffsetDateTime.now());
            post.setUpdatedAt(OffsetDateTime.now());
            post.setIsDeleted(false);
            d1Util.insert("posts_gl", postToMap(post));
        } else {
            d1Util.updateById("posts_gl", postToMap(post), "post_id", EntityMapper.uuidToString(post.getPostId()));
        }
        return post;
    }

    private GlPost mapToPost(Map<String, Object> row) {
        GlPost post = new GlPost();
        post.setPostId(EntityMapper.getUUID(row, "post_id"));
        post.setUserId(EntityMapper.getUUID(row, "user_id"));
        post.setUserNickname(EntityMapper.getString(row, "user_nickname"));
        post.setUserStage(EntityMapper.getString(row, "user_stage"));
        post.setAvatarUrl(EntityMapper.getString(row, "avatar_url"));
        post.setContent(EntityMapper.getString(row, "content"));
        Object isDeletedObj = row.get("is_deleted");
        if (isDeletedObj instanceof Boolean) {
            post.setIsDeleted((Boolean) isDeletedObj);
        } else if (isDeletedObj instanceof Number) {
            post.setIsDeleted(((Number) isDeletedObj).intValue() != 0);
        }
        post.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        post.setUpdatedAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
        return post;
    }

    private Map<String, Object> postToMap(GlPost post) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "post_id", post.getPostId());
        EntityMapper.putIfNotNull(data, "user_id", post.getUserId());
        EntityMapper.putIfNotNull(data, "user_nickname", post.getUserNickname());
        EntityMapper.putIfNotNull(data, "user_stage", post.getUserStage());
        EntityMapper.putIfNotNull(data, "avatar_url", post.getAvatarUrl());
        EntityMapper.putIfNotNull(data, "content", post.getContent());
        EntityMapper.putIfNotNull(data, "is_deleted", post.getIsDeleted());
        EntityMapper.putIfNotNull(data, "created_at", post.getCreatedAt());
        EntityMapper.putIfNotNull(data, "updated_at", post.getUpdatedAt());
        return data;
    }

    private PostWithUpvotesDto convertToPostWithUpvotesDto(GlPost post) {
        Integer upvotes = postLikeService.getLikeCount(post.getPostId());
        long commentCountLong = commentService.countByPostId(post.getPostId());
        return new PostWithUpvotesDto(
            post.getPostId(), post.getUserId(), post.getUserNickname(), post.getUserStage(), post.getAvatarUrl(),
            post.getContent(), post.getIsDeleted(), post.getCreatedAt(), post.getUpdatedAt(), upvotes, (int) commentCountLong
        );
    }

    private List<GlPost> getAllPosts(int page, int size, String sortBy, String sortDir) {
        String validSortBy = validateSortField(sortBy);
        String validSortDir = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";
        String sql = String.format("SELECT * FROM posts_gl WHERE is_deleted = ? ORDER BY %s %s", validSortBy, validSortDir);
        return d1Util.queryPage(sql, page + 1, size, false).stream().map(this::mapToPost).collect(Collectors.toList());
    }

    private String validateSortField(String sortBy) {
        String[] allowedFields = {"created_at", "updated_at", "user_nickname", "user_stage"};
        for (String field : allowedFields) {
            if (field.equalsIgnoreCase(sortBy)) {
                return field;
            }
        }
        return "created_at";
    }
}
