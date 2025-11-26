package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.Post;
import com.example.cursorquitterweb.dto.PostWithUpvotesDto;
import com.example.cursorquitterweb.service.PostService;
import com.example.cursorquitterweb.service.PostLikeService;
import com.example.cursorquitterweb.service.CommentService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 帖子服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class PostServiceImpl implements PostService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Autowired
    private PostLikeService postLikeService;
    
    @Autowired
    private CommentService commentService;
    
    @Override
    public Optional<Post> findById(UUID postId) {
        String sql = "SELECT * FROM posts WHERE post_id = ? AND is_deleted = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(postId), false);
        return row != null ? Optional.of(mapToPost(row)) : Optional.empty();
    }
    
    @Override
    public Optional<PostWithUpvotesDto> findByIdWithUpvotes(UUID postId) {
        Optional<Post> postOpt = findById(postId);
        if (postOpt.isPresent()) {
            PostWithUpvotesDto postWithUpvotes = convertToPostWithUpvotesDto(postOpt.get());
            return Optional.of(postWithUpvotes);
        }
        return Optional.empty();
    }
    
    @Override
    public Post createPost(UUID userId, String userNickname, String userStage, String content) {
        Post post = new Post(userId, userNickname, userStage, content);
        return savePost(post);
    }
    
    @Override
    public Post createPost(UUID userId, String userNickname, String userStage, String avatarUrl, String content) {
        Post post = new Post(userId, userNickname, userStage, avatarUrl, content);
        return savePost(post);
    }
    
    @Override
    public Post updatePost(UUID postId, String content) {
        Optional<Post> optionalPost = findById(postId);
        if (optionalPost.isPresent()) {
            Post post = optionalPost.get();
            post.setContent(content);
            post.preUpdate();
            return savePost(post);
        }
        throw new RuntimeException("帖子不存在或已被删除");
    }
    
    @Override
    public void deletePost(UUID postId) {
        String sql = "UPDATE posts SET is_deleted = ?, updated_at = ? WHERE post_id = ?";
        d1Util.execute(sql, true, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(postId));
    }
    
    @Override
    public List<Post> findByUserId(UUID userId) {
        String sql = "SELECT * FROM posts WHERE user_id = ? AND is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(userId), false);
        return rows.stream().map(this::mapToPost).collect(Collectors.toList());
    }
    
    @Override
    public List<Post> findByUserId(UUID userId, int page, int size) {
        String sql = "SELECT * FROM posts WHERE user_id = ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(userId), false).stream()
            .map(this::mapToPost)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<PostWithUpvotesDto> findByUserIdWithUpvotes(UUID userId, int page, int size) {
        List<Post> posts = findByUserId(userId, page, size);
        return posts.stream()
                .map(this::convertToPostWithUpvotesDto)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Post> findByUserNickname(String userNickname) {
        String sql = "SELECT * FROM posts WHERE user_nickname = ? AND is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, userNickname, false);
        return rows.stream().map(this::mapToPost).collect(Collectors.toList());
    }
    
    @Override
    public List<Post> findByUserStage(String userStage) {
        String sql = "SELECT * FROM posts WHERE user_stage = ? AND is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, userStage, false);
        return rows.stream().map(this::mapToPost).collect(Collectors.toList());
    }
    
    @Override
    public List<Post> searchByContent(String content) {
        String sql = "SELECT * FROM posts WHERE LOWER(content) LIKE LOWER(?) AND is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + content + "%", false);
        return rows.stream().map(this::mapToPost).collect(Collectors.toList());
    }
    
    @Override
    public List<Post> getAllPosts(int page, int size) {
        String sql = "SELECT * FROM posts WHERE is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, false).stream()
            .map(this::mapToPost)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Post> getAllPosts() {
        String sql = "SELECT * FROM posts WHERE is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, false);
        return rows.stream().map(this::mapToPost).collect(Collectors.toList());
    }
    
    @Override
    public List<PostWithUpvotesDto> getAllPostsWithUpvotes(int page, int size) {
        List<Post> posts = getAllPosts(page, size);
        return posts.stream()
                .map(this::convertToPostWithUpvotesDto)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<PostWithUpvotesDto> getAllPostsWithUpvotes() {
        List<Post> posts = getAllPosts();
        return posts.stream()
                .map(this::convertToPostWithUpvotesDto)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Post> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM posts WHERE created_at >= ? AND created_at <= ? AND is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime), 
            false);
        return rows.stream().map(this::mapToPost).collect(Collectors.toList());
    }
    
    @Override
    public long countByUserId(UUID userId) {
        String sql = "SELECT COUNT(*) as count FROM posts WHERE user_id = ? AND is_deleted = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(userId), false);
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM posts WHERE created_at >= ? AND created_at <= ? AND is_deleted = ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime), 
            false);
    }
    
    /**
     * 保存帖子
     */
    private Post savePost(Post post) {
        if (post.getPostId() == null) {
            // 插入新记录
            post.setPostId(UUID.randomUUID());
            post.setCreatedAt(OffsetDateTime.now());
            post.setUpdatedAt(OffsetDateTime.now());
            post.setIsDeleted(false);
            Map<String, Object> data = postToMap(post);
            d1Util.insert("posts", data);
            return post;
        } else {
            // 更新记录
            Map<String, Object> data = postToMap(post);
            d1Util.updateById("posts", data, "post_id", EntityMapper.uuidToString(post.getPostId()));
            return post;
        }
    }
    
    /**
     * 将 Map 转换为 Post 实体
     */
    private Post mapToPost(Map<String, Object> row) {
        Post post = new Post();
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
        } else {
            post.setIsDeleted(false);
        }
        post.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        post.setUpdatedAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
        return post;
    }
    
    /**
     * 将 Post 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> postToMap(Post post) {
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
    
    /**
     * 将Post实体转换为PostWithUpvotesDto
     */
    private PostWithUpvotesDto convertToPostWithUpvotesDto(Post post) {
        // 获取点赞数，如果查不到默认为0
        Integer upvotes = postLikeService.getLikeCount(post.getPostId());
        
        // 获取评论数，如果查不到默认为0
        long commentCountLong = commentService.countByPostId(post.getPostId());
        Integer commentCount = (int) commentCountLong;
        
        return new PostWithUpvotesDto(
                post.getPostId(),
                post.getUserId(),
                post.getUserNickname(),
                post.getUserStage(),
                post.getAvatarUrl(),
                post.getContent(),
                post.getIsDeleted(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                upvotes,
                commentCount
        );
    }
}
