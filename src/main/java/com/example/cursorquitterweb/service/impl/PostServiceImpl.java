package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.Post;
import com.example.cursorquitterweb.dto.PostPageResult;
import com.example.cursorquitterweb.dto.PostWithUpvotesDto;
import com.example.cursorquitterweb.service.PostService;
import com.example.cursorquitterweb.service.PostLikeService;
import com.example.cursorquitterweb.service.CommentService;
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
    
    /**
     * 获取所有帖子（分页，支持排序）
     */
    private List<Post> getAllPosts(int page, int size, String sortBy, String sortDir) {
        // 验证排序字段，防止SQL注入
        String validSortBy = validateSortField(sortBy);
        String validSortDir = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";
        
        String sql = String.format("SELECT * FROM posts WHERE is_deleted = ? ORDER BY %s %s", validSortBy, validSortDir);
        return d1Util.queryPage(sql, page + 1, size, false).stream()
            .map(this::mapToPost)
            .collect(Collectors.toList());
    }
    
    /**
     * 验证排序字段，防止SQL注入
     */
    private String validateSortField(String sortBy) {
        // 允许的排序字段列表
        String[] allowedFields = {"created_at", "updated_at", "user_nickname", "user_stage"};
        for (String field : allowedFields) {
            if (field.equalsIgnoreCase(sortBy)) {
                return field;
            }
        }
        // 默认返回 created_at
        return "created_at";
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
    public List<PostWithUpvotesDto> getAllPostsWithUpvotes(int page, int size, String sortBy, String sortDir) {
        List<Post> posts = getAllPosts(page, size, sortBy, sortDir);
        return posts.stream()
                .map(this::convertToPostWithUpvotesDto)
                .collect(Collectors.toList());
    }
    
    @Override
    public PostPageResult getAllPostsWithUpvotesAndCount(int page, int size, String sortBy, String sortDir) {
        // 使用窗口函数在单次查询中同时获取数据和总数
        String validSortBy = validateSortField(sortBy);
        String validSortDir = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";
        
        // 使用窗口函数 COUNT(*) OVER() 在单次查询中获取总数
        String sql = String.format(
            "SELECT *, COUNT(*) OVER() as total_count FROM posts WHERE is_deleted = ? ORDER BY %s %s LIMIT ? OFFSET ?",
            validSortBy, validSortDir
        );
        
        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, false, size, offset);
        
        long totalElements = 0;
        List<Post> posts = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            // 从第一行获取总数（所有行的 total_count 都相同）
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }
            // 移除 total_count 字段，避免影响 Post 映射
            Map<String, Object> postRow = new HashMap<>(row);
            postRow.remove("total_count");
            posts.add(mapToPost(postRow));
        }
        
        // 优化：使用批量查询获取所有帖子的点赞数和评论数，避免 N+1 查询问题
        List<PostWithUpvotesDto> content;
        if (posts.isEmpty()) {
            content = new ArrayList<>();
        } else {
            // 收集所有帖子ID
            List<UUID> postIds = posts.stream()
                    .map(Post::getPostId)
                    .collect(Collectors.toList());
            
            // 批量查询点赞数和评论数
            Map<UUID, Integer> likeCountsMap = postLikeService.getLikeCountsBatch(postIds);
            Map<UUID, Long> commentCountsMap = commentService.countByPostIdsBatch(postIds);
            
            // 批量转换为 DTO
            content = posts.stream()
                    .map(post -> convertToPostWithUpvotesDto(post, likeCountsMap, commentCountsMap))
                    .collect(Collectors.toList());
        }
        
        return new PostPageResult(content, totalElements);
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
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) as count FROM posts WHERE is_deleted = ?";
        return d1Util.queryLong(sql, false);
    }
    
    @Override
    public boolean existsByContent(String content) {
        String sql = "SELECT COUNT(*) as count FROM posts WHERE content = ? AND is_deleted = ?";
        long count = d1Util.queryLong(sql, content, false);
        return count > 0;
    }
    
    /**
     * 保存帖子
     */
    private Post savePost(Post post) {
        if (post.getPostId() == null) {
            // 插入新记录前，再次检查内容是否重复（避免竞态条件）
            if (post.getContent() != null && existsByContent(post.getContent())) {
                throw new RuntimeException("帖子内容已存在，无法重复创建");
            }
            
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
     * 将Post实体转换为PostWithUpvotesDto（使用单个查询，性能较低）
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
    
    /**
     * 将Post实体转换为PostWithUpvotesDto（使用批量查询结果，性能优化）
     * @param post 帖子实体
     * @param likeCountsMap 点赞数Map（key为postId，value为点赞数）
     * @param commentCountsMap 评论数Map（key为postId，value为评论数）
     */
    private PostWithUpvotesDto convertToPostWithUpvotesDto(Post post, Map<UUID, Integer> likeCountsMap, Map<UUID, Long> commentCountsMap) {
        // 从Map中获取点赞数，如果查不到默认为0
        Integer upvotes = likeCountsMap.getOrDefault(post.getPostId(), 0);
        
        // 从Map中获取评论数，如果查不到默认为0
        Long commentCountLong = commentCountsMap.getOrDefault(post.getPostId(), 0L);
        Integer commentCount = commentCountLong.intValue();
        
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
