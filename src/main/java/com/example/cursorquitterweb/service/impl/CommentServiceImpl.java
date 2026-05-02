package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.CommentPageResult;
import com.example.cursorquitterweb.dto.CommentWithRepliesDTO;
import com.example.cursorquitterweb.dto.CommentWithRepliesPageResult;
import com.example.cursorquitterweb.entity.Comment;
import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.service.CommentService;
import com.example.cursorquitterweb.service.CommunityContentTranslationService;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评论服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class CommentServiceImpl implements CommentService {
    
    @Autowired
    private CloudflareD1Util d1Util;

    @Autowired
    private CommunityContentTranslationService communityContentTranslationService;

    @Autowired
    private UserService userService;
    
    @Override
    public Optional<Comment> findById(UUID commentId) {
        String sql = "SELECT * FROM comments WHERE comment_id = ? AND is_deleted = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(commentId), false);
        return row != null ? Optional.of(mapToComment(row)) : Optional.empty();
    }
    
    @Override
    public Comment createComment(String postId, String userId, String userNickname, String userStage, String avatarUrl, String content) {
        return createComment(postId, userId, userNickname, userStage, avatarUrl, content, null, null);
    }

    @Override
    public Comment createComment(String postId, String userId, String userNickname, String userStage, String avatarUrl,
                                 String content, String originalLanguage, String emojiCountry) {
        try {
            // 将String类型的ID转换为UUID类型
            UUID postUuid = UUID.fromString(postId);
            UUID userUuid = UUID.fromString(userId);
            
            // 只拦截同一用户短时间内的重复提交，允许不同用户发表相同短评论。
            if (existsRecentDuplicateByUser(postUuid, userUuid, content)) {
                throw new RuntimeException("评论内容已存在，无法重复创建");
            }
            
            // 创建一级评论（直接评论帖子）
            Comment comment = new Comment(postUuid, userUuid, userNickname, userStage, avatarUrl, content);
            // 确保comment_level为1（一级评论）
            comment.setCommentLevel((short) 1);
            String normalizedLanguage = communityContentTranslationService.normalizeOriginalLanguage(originalLanguage, content);
            comment.setOriginalLanguage(normalizedLanguage);
            comment.setEmojiCountry(resolveEmojiCountry(emojiCountry, userUuid, normalizedLanguage));
            setOriginalLanguageContent(comment);
            Comment savedComment = saveComment(comment);
            communityContentTranslationService.translateCommentAsync(savedComment.getCommentId(), savedComment.getContent(), savedComment.getOriginalLanguage());
            return savedComment;
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的UUID格式: " + e.getMessage());
        }
    }
    
    @Override
    public Comment updateComment(UUID commentId, String content) {
        Optional<Comment> optionalComment = findById(commentId);
        if (optionalComment.isPresent()) {
            Comment comment = optionalComment.get();
            comment.setContent(content);
            resetTranslation(comment);
            comment.preUpdate();
            Comment savedComment = saveComment(comment);
            communityContentTranslationService.translateCommentAsync(savedComment.getCommentId(), savedComment.getContent(), savedComment.getOriginalLanguage());
            return savedComment;
        }
        throw new RuntimeException("评论不存在或已被删除");
    }
    
    @Override
    public Comment updateComment(UUID commentId, String content, String avatarUrl) {
        Optional<Comment> optionalComment = findById(commentId);
        if (optionalComment.isPresent()) {
            Comment comment = optionalComment.get();
            comment.setContent(content);
            if (avatarUrl != null) {
                comment.setAvatarUrl(avatarUrl);
            }
            resetTranslation(comment);
            comment.preUpdate();
            Comment savedComment = saveComment(comment);
            communityContentTranslationService.translateCommentAsync(savedComment.getCommentId(), savedComment.getContent(), savedComment.getOriginalLanguage());
            return savedComment;
        }
        throw new RuntimeException("评论不存在或已被删除");
    }
    
    @Override
    public void deleteComment(UUID commentId) {
        String sql = "UPDATE comments SET is_deleted = ?, updated_at = ? WHERE comment_id = ?";
        d1Util.execute(sql, true, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(commentId));
    }
    
    @Override
    public List<Comment> findByPostId(UUID postId) {
        String sql = "SELECT * FROM comments WHERE post_id = ? AND is_deleted = ? ORDER BY created_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(postId), false);
        return rows.stream().map(this::mapToComment).collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> findByPostId(UUID postId, int page, int size) {
        String sql = "SELECT * FROM comments WHERE post_id = ? AND is_deleted = ? ORDER BY created_at ASC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(postId), false).stream()
            .map(this::mapToComment)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> findByUserId(UUID userId) {
        String sql = "SELECT * FROM comments WHERE user_id = ? AND is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(userId), false);
        return rows.stream().map(this::mapToComment).collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> findByUserId(UUID userId, int page, int size) {
        String sql = "SELECT * FROM comments WHERE user_id = ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(userId), false).stream()
            .map(this::mapToComment)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> findByUserNickname(String userNickname) {
        String sql = "SELECT * FROM comments WHERE user_nickname = ? AND is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, userNickname, false);
        return rows.stream().map(this::mapToComment).collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> findByUserStage(String userStage) {
        String sql = "SELECT * FROM comments WHERE user_stage = ? AND is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, userStage, false);
        return rows.stream().map(this::mapToComment).collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> searchByContent(String content) {
        String sql = "SELECT * FROM comments WHERE LOWER(content) LIKE LOWER(?) AND is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + content + "%", false);
        return rows.stream().map(this::mapToComment).collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> getAllComments(int page, int size) {
        String sql = "SELECT * FROM comments WHERE is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, false).stream()
            .map(this::mapToComment)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> getAllComments() {
        String sql = "SELECT * FROM comments WHERE is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, false);
        return rows.stream().map(this::mapToComment).collect(Collectors.toList());
    }
    
    @Override
    public CommentPageResult getAllCommentsWithCount(int page, int size, String sortBy, String sortDir) {
        // 使用窗口函数在单次查询中同时获取数据和总数
        String validSortBy = validateSortField(sortBy);
        String validSortDir = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";
        
        // 使用窗口函数 COUNT(*) OVER() 在单次查询中获取总数
        String sql = String.format(
            "SELECT *, COUNT(*) OVER() as total_count FROM comments WHERE is_deleted = ? ORDER BY %s %s LIMIT ? OFFSET ?",
            validSortBy, validSortDir
        );
        
        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, false, size, offset);
        
        long totalElements = 0;
        List<Comment> comments = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            // 从第一行获取总数（所有行的 total_count 都相同）
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }
            // 移除 total_count 字段，避免影响 Comment 映射
            Map<String, Object> commentRow = new HashMap<>(row);
            commentRow.remove("total_count");
            comments.add(mapToComment(commentRow));
        }
        
        return new CommentPageResult(comments, totalElements);
    }
    
    /**
     * 验证排序字段，防止SQL注入
     */
    private String validateSortField(String sortBy) {
        // 允许的排序字段列表
        String[] allowedFields = {"created_at", "updated_at", "user_nickname", "user_stage", "comment_level"};
        for (String field : allowedFields) {
            if (field.equalsIgnoreCase(sortBy)) {
                return field;
            }
        }
        // 默认返回 created_at
        return "created_at";
    }
    
    @Override
    public List<Comment> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM comments WHERE created_at >= ? AND created_at <= ? AND is_deleted = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime), 
            false);
        return rows.stream().map(this::mapToComment).collect(Collectors.toList());
    }
    
    @Override
    public long countByPostId(UUID postId) {
        String sql = "SELECT COUNT(*) as count FROM comments WHERE post_id = ? AND is_deleted = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(postId), false);
    }
    
    @Override
    public Map<UUID, Long> countByPostIdsBatch(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new HashMap<>();
        }
        
        // 构建 IN 子句的占位符
        String placeholders = postIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));
        
        // 构建参数列表
        List<Object> params = new ArrayList<>();
        for (UUID postId : postIds) {
            params.add(EntityMapper.uuidToString(postId));
        }
        params.add(false); // is_deleted = false
        
        String sql = "SELECT post_id, COUNT(*) as count FROM comments WHERE post_id IN (" + placeholders + ") AND is_deleted = ? GROUP BY post_id";
        List<Map<String, Object>> rows = d1Util.queryList(sql, params.toArray());
        
        // 构建结果 Map
        Map<UUID, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID postId = EntityMapper.getUUID(row, "post_id");
            Long count = EntityMapper.getLong(row, "count");
            if (postId != null) {
                result.put(postId, count != null ? count : 0L);
            }
        }
        
        // 对于没有评论的帖子，默认返回 0
        for (UUID postId : postIds) {
            result.putIfAbsent(postId, 0L);
        }
        
        return result;
    }
    
    @Override
    public long countByUserId(UUID userId) {
        String sql = "SELECT COUNT(*) as count FROM comments WHERE user_id = ? AND is_deleted = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(userId), false);
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM comments WHERE created_at >= ? AND created_at <= ? AND is_deleted = ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime), 
            false);
    }
    
    @Override
    public void deleteCommentsByPostId(UUID postId) {
        String sql = "UPDATE comments SET is_deleted = ?, updated_at = ? WHERE post_id = ?";
        d1Util.execute(sql, true, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(postId));
    }
    
    // ============= 新增：小红书风格的评论回复功能实现 =============
    
    @Override
    public Comment createReplyComment(String postId, String userId, String userNickname, String userStage, 
                                     String avatarUrl, String content, String parentCommentId, 
                                     String replyToUserId, String replyToUserNickname, String replyToCommentId) {
        return createReplyComment(postId, userId, userNickname, userStage, avatarUrl, content,
            parentCommentId, replyToUserId, replyToUserNickname, replyToCommentId, null, null);
    }

    @Override
    public Comment createReplyComment(String postId, String userId, String userNickname, String userStage,
                                     String avatarUrl, String content, String parentCommentId,
                                     String replyToUserId, String replyToUserNickname, String replyToCommentId,
                                     String originalLanguage, String emojiCountry) {
        try {
            // 转换字符串ID为UUID
            UUID postUuid = UUID.fromString(postId);
            UUID userUuid = UUID.fromString(userId);
            UUID parentCommentUuid = parentCommentId != null ? UUID.fromString(parentCommentId) : null;
            UUID replyToUserUuid = replyToUserId != null ? UUID.fromString(replyToUserId) : null;
            UUID replyToCommentUuid = replyToCommentId != null ? UUID.fromString(replyToCommentId) : null;
            
            // 只拦截同一用户短时间内的重复提交，允许不同用户发表相同短回复。
            if (existsRecentDuplicateByUser(postUuid, userUuid, content)) {
                throw new RuntimeException("回复内容已存在，无法重复创建");
            }
            
            // 查找父评论以确定root_comment_id
            UUID rootCommentUuid = null;
            if (parentCommentUuid != null) {
                Optional<Comment> parentComment = findById(parentCommentUuid);
                if (parentComment.isPresent()) {
                    Comment parent = parentComment.get();
                    // 如果父评论是一级评论，则它就是根评论；否则使用父评论的根评论ID
                    rootCommentUuid = parent.getCommentLevel() == 1 ? parent.getCommentId() : parent.getRootCommentId();
                } else {
                    throw new RuntimeException("父评论不存在或已被删除");
                }
            }
            
            // 创建回复评论
            Comment comment = new Comment(postUuid, userUuid, userNickname, userStage, avatarUrl, content,
                    parentCommentUuid, replyToUserUuid, replyToUserNickname, replyToCommentUuid, rootCommentUuid);
            String normalizedLanguage = communityContentTranslationService.normalizeOriginalLanguage(originalLanguage, content);
            comment.setOriginalLanguage(normalizedLanguage);
            comment.setEmojiCountry(resolveEmojiCountry(emojiCountry, userUuid, normalizedLanguage));
            setOriginalLanguageContent(comment);
            
            Comment savedComment = saveComment(comment);
            communityContentTranslationService.translateCommentAsync(savedComment.getCommentId(), savedComment.getContent(), savedComment.getOriginalLanguage());
            return savedComment;
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的UUID格式: " + e.getMessage());
        }
    }
    
    @Override
    public List<Comment> findTopLevelCommentsByPostId(UUID postId) {
        String sql = "SELECT * FROM comments WHERE post_id = ? AND comment_level = ? AND is_deleted = ? ORDER BY created_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(postId), 1, false);
        return rows.stream().map(this::mapToComment).collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> findTopLevelCommentsByPostId(UUID postId, int page, int size) {
        String sql = "SELECT * FROM comments WHERE post_id = ? AND comment_level = ? AND is_deleted = ? ORDER BY created_at ASC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(postId), 1, false).stream()
            .map(this::mapToComment)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Comment> findRepliesByRootCommentId(UUID rootCommentId) {
        String sql = "SELECT * FROM comments WHERE root_comment_id = ? AND comment_level = ? AND is_deleted = ? ORDER BY created_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(rootCommentId), 2, false);
        return rows.stream().map(this::mapToComment).collect(Collectors.toList());
    }
    
    @Override
    public List<CommentWithRepliesDTO> findCommentsWithRepliesByPostId(UUID postId) {
        // 1. 获取所有一级评论
        List<Comment> topLevelComments = findTopLevelCommentsByPostId(postId);
        
        if (topLevelComments.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 2. 获取所有一级评论的ID列表
        List<UUID> rootCommentIds = topLevelComments.stream()
                .map(Comment::getCommentId)
                .collect(Collectors.toList());
        
        // 3. 批量查询所有回复
        List<Comment> allReplies = findRepliesByRootCommentIds(rootCommentIds);
        
        // 4. 将回复按根评论ID分组
        Map<UUID, List<Comment>> repliesMap = allReplies.stream()
                .collect(Collectors.groupingBy(Comment::getRootCommentId));
        
        // 5. 组装结果
        return topLevelComments.stream()
                .map(comment -> {
                    List<Comment> replies = repliesMap.getOrDefault(comment.getCommentId(), new ArrayList<>());
                    return new CommentWithRepliesDTO(comment, replies);
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public List<CommentWithRepliesDTO> findCommentsWithRepliesByPostId(UUID postId, int page, int size) {
        // 1. 分页获取一级评论
        List<Comment> topLevelComments = findTopLevelCommentsByPostId(postId, page, size);
        
        if (topLevelComments.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 2. 获取当前页的一级评论ID列表
        List<UUID> rootCommentIds = topLevelComments.stream()
                .map(Comment::getCommentId)
                .collect(Collectors.toList());
        
        // 3. 批量查询这些评论的回复
        List<Comment> allReplies = findRepliesByRootCommentIds(rootCommentIds);
        
        // 4. 将回复按根评论ID分组
        Map<UUID, List<Comment>> repliesMap = allReplies.stream()
                .collect(Collectors.groupingBy(Comment::getRootCommentId));
        
        // 5. 组装结果
        return topLevelComments.stream()
                .map(comment -> {
                    List<Comment> replies = repliesMap.getOrDefault(comment.getCommentId(), new ArrayList<>());
                    return new CommentWithRepliesDTO(comment, replies);
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public CommentWithRepliesPageResult findCommentsWithRepliesByPostIdWithCount(UUID postId, int page, int size, String sortBy, String sortDir) {
        // 使用窗口函数和子查询在单次查询中同时获取一级评论、回复和总数
        String validSortBy = validateCommentSortField(sortBy);
        String validSortDir = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";
        int offset = page * size;
        String postIdStr = EntityMapper.uuidToString(postId);
        
        // 构建单次查询：使用子查询获取当前页的一级评论ID，然后使用 UNION ALL 一次性获取一级评论和回复
        // 使用窗口函数获取总数，使用 UNION ALL 合并一级评论和回复
        String sql = String.format(
            "SELECT c.*, " +
            "       CASE WHEN c.comment_level = 1 THEN COUNT(*) OVER(PARTITION BY c.comment_level) ELSE NULL END as total_count, " +
            "       CASE WHEN c.comment_level = 1 THEN 1 ELSE 0 END as is_top_level " +
            "FROM comments c " +
            "WHERE c.post_id = ? AND c.is_deleted = ? " +
            "  AND (" +
            "    (c.comment_level = 1 AND c.comment_id IN (" +
            "      SELECT comment_id FROM comments " +
            "      WHERE post_id = ? AND comment_level = ? AND is_deleted = ? " +
            "      ORDER BY %s %s LIMIT ? OFFSET ?" +
            "    )) OR " +
            "    (c.comment_level = 2 AND c.root_comment_id IN (" +
            "      SELECT comment_id FROM comments " +
            "      WHERE post_id = ? AND comment_level = ? AND is_deleted = ? " +
            "      ORDER BY %s %s LIMIT ? OFFSET ?" +
            "    ))" +
            "  ) " +
            "ORDER BY is_top_level DESC, c.created_at ASC",
            validSortBy, validSortDir, validSortBy, validSortDir
        );
        
        List<Map<String, Object>> allRows = d1Util.queryList(sql, 
            postIdStr, false,  // WHERE c.post_id = ? AND c.is_deleted = ?
            postIdStr, 1, false, size, offset,  // 子查询1：一级评论
            postIdStr, 1, false, size, offset   // 子查询2：回复的根评论
        );
        
        long totalElements = 0;
        List<Comment> topLevelComments = new ArrayList<>();
        List<Comment> allReplies = new ArrayList<>();
        
        for (Map<String, Object> row : allRows) {
            Object isTopLevel = row.get("is_top_level");
            boolean isTop = isTopLevel != null && ((Number) isTopLevel).intValue() == 1;
            
            if (isTop) {
                // 这是一级评论
                if (totalElements == 0 && row.get("total_count") != null) {
                    totalElements = ((Number) row.get("total_count")).longValue();
                }
                Map<String, Object> commentRow = new HashMap<>(row);
                commentRow.remove("total_count");
                commentRow.remove("is_top_level");
                topLevelComments.add(mapToComment(commentRow));
            } else {
                // 这是回复
                Map<String, Object> replyRow = new HashMap<>(row);
                replyRow.remove("total_count");
                replyRow.remove("is_top_level");
                allReplies.add(mapToComment(replyRow));
            }
        }
        
        if (topLevelComments.isEmpty()) {
            return new CommentWithRepliesPageResult(new ArrayList<>(), totalElements);
        }
        
        // 将回复按根评论ID分组
        Map<UUID, List<Comment>> repliesMap = allReplies.stream()
                .collect(Collectors.groupingBy(Comment::getRootCommentId));
        
        // 组装结果
        List<CommentWithRepliesDTO> content = topLevelComments.stream()
                .map(comment -> {
                    List<Comment> replies = repliesMap.getOrDefault(comment.getCommentId(), new ArrayList<>());
                    return new CommentWithRepliesDTO(comment, replies);
                })
                .collect(Collectors.toList());
        
        return new CommentWithRepliesPageResult(content, totalElements);
    }
    
    /**
     * 验证评论排序字段，防止SQL注入
     */
    private String validateCommentSortField(String sortBy) {
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
    public long countRepliesByRootCommentId(UUID rootCommentId) {
        String sql = "SELECT COUNT(*) as count FROM comments WHERE root_comment_id = ? AND comment_level = ? AND is_deleted = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(rootCommentId), 2, false);
    }
    
    @Override
    public void deleteCommentAndReplies(UUID commentId) {
        // 先查找评论，确定是根评论还是回复
        Optional<Comment> commentOpt = findById(commentId);
        if (!commentOpt.isPresent()) {
            return;
        }
        
        Comment comment = commentOpt.get();
        if (comment.getCommentLevel() == 1) {
            // 如果是根评论，删除它及其所有回复
            String sql = "UPDATE comments SET is_deleted = ?, updated_at = ? WHERE (comment_id = ? OR root_comment_id = ?)";
            d1Util.execute(sql, true, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), 
                EntityMapper.uuidToString(commentId), EntityMapper.uuidToString(commentId));
        } else {
            // 如果是回复，只删除该回复
            deleteComment(commentId);
        }
    }
    
    @Override
    public boolean existsByContent(UUID postId, String content) {
        String sql = "SELECT COUNT(*) as count FROM comments WHERE post_id = ? AND content = ? AND is_deleted = ?";
        long count = d1Util.queryLong(sql, EntityMapper.uuidToString(postId), content, false);
        return count > 0;
    }

    private boolean existsRecentDuplicateByUser(UUID postId, UUID userId, String content) {
        String sql = "SELECT COUNT(*) as count FROM comments " +
            "WHERE post_id = ? AND user_id = ? AND content = ? AND is_deleted = ? AND created_at >= ?";
        long count = d1Util.queryLong(
            sql,
            EntityMapper.uuidToString(postId),
            EntityMapper.uuidToString(userId),
            content,
            false,
            EntityMapper.offsetDateTimeToString(OffsetDateTime.now().minusSeconds(10))
        );
        return count > 0;
    }
    
    /**
     * 批量查询回复（根据多个根评论ID）
     */
    private List<Comment> findRepliesByRootCommentIds(List<UUID> rootCommentIds) {
        if (rootCommentIds == null || rootCommentIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 构建 IN 查询
        StringBuilder sql = new StringBuilder("SELECT * FROM comments WHERE root_comment_id IN (");
        Object[] params = new Object[rootCommentIds.size() + 2];
        for (int i = 0; i < rootCommentIds.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params[i] = EntityMapper.uuidToString(rootCommentIds.get(i));
        }
        sql.append(") AND comment_level = ? AND is_deleted = ? ORDER BY created_at ASC");
        params[rootCommentIds.size()] = 2;
        params[rootCommentIds.size() + 1] = false;
        
        List<Map<String, Object>> rows = d1Util.queryList(sql.toString(), params);
        return rows.stream().map(this::mapToComment).collect(Collectors.toList());
    }
    
    /**
     * 保存评论
     */
    private Comment saveComment(Comment comment) {
        if (comment.getCommentId() == null) {
            // 插入新记录
            comment.setCommentId(UUID.randomUUID());
            comment.setCreatedAt(OffsetDateTime.now());
            comment.setUpdatedAt(OffsetDateTime.now());
            comment.setIsDeleted(false);
            if (comment.getCommentLevel() == null) {
                comment.setCommentLevel((short) 1);
            }
            Map<String, Object> data = commentToMap(comment);
            d1Util.insert("comments", data);
            return comment;
        } else {
            // 更新记录
            Map<String, Object> data = commentToMap(comment);
            d1Util.updateById("comments", data, "comment_id", EntityMapper.uuidToString(comment.getCommentId()));
            return comment;
        }
    }
    
    /**
     * 将 Map 转换为 Comment 实体
     */
    private Comment mapToComment(Map<String, Object> row) {
        Comment comment = new Comment();
        comment.setCommentId(EntityMapper.getUUID(row, "comment_id"));
        comment.setPostId(EntityMapper.getUUID(row, "post_id"));
        comment.setUserId(EntityMapper.getUUID(row, "user_id"));
        comment.setUserNickname(EntityMapper.getString(row, "user_nickname"));
        comment.setUserStage(EntityMapper.getString(row, "user_stage"));
        comment.setAvatarUrl(EntityMapper.getString(row, "avatar_url"));
        comment.setContent(EntityMapper.getString(row, "content"));
        applyTranslationFields(comment, row);
        Object isDeletedObj = row.get("is_deleted");
        if (isDeletedObj instanceof Boolean) {
            comment.setIsDeleted((Boolean) isDeletedObj);
        } else if (isDeletedObj instanceof Number) {
            comment.setIsDeleted(((Number) isDeletedObj).intValue() != 0);
        } else {
            comment.setIsDeleted(false);
        }
        comment.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        comment.setUpdatedAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
        comment.setParentCommentId(EntityMapper.getUUID(row, "parent_comment_id"));
        comment.setReplyToUserId(EntityMapper.getUUID(row, "reply_to_user_id"));
        comment.setReplyToUserNickname(EntityMapper.getString(row, "reply_to_user_nickname"));
        comment.setReplyToCommentId(EntityMapper.getUUID(row, "reply_to_comment_id"));
        Object commentLevelObj = row.get("comment_level");
        if (commentLevelObj instanceof Number) {
            comment.setCommentLevel(((Number) commentLevelObj).shortValue());
        } else {
            comment.setCommentLevel((short) 1);
        }
        comment.setRootCommentId(EntityMapper.getUUID(row, "root_comment_id"));
        return comment;
    }
    
    /**
     * 将 Comment 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> commentToMap(Comment comment) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "comment_id", comment.getCommentId());
        EntityMapper.putIfNotNull(data, "post_id", comment.getPostId());
        EntityMapper.putIfNotNull(data, "user_id", comment.getUserId());
        EntityMapper.putIfNotNull(data, "user_nickname", comment.getUserNickname());
        EntityMapper.putIfNotNull(data, "user_stage", comment.getUserStage());
        EntityMapper.putIfNotNull(data, "avatar_url", comment.getAvatarUrl());
        EntityMapper.putIfNotNull(data, "content", comment.getContent());
        EntityMapper.putIfNotNull(data, "original_language", comment.getOriginalLanguage());
        data.put("content_zh", comment.getContentZh());
        data.put("content_en", comment.getContentEn());
        data.put("content_ja", comment.getContentJa());
        data.put("content_ko", comment.getContentKo());
        data.put("content_de", comment.getContentDe());
        data.put("content_fr", comment.getContentFr());
        data.put("content_pt", comment.getContentPt());
        data.put("content_es", comment.getContentEs());
        EntityMapper.putIfNotNull(data, "translation_status", comment.getTranslationStatus());
        if (comment.getTranslatedAt() != null) {
            EntityMapper.putIfNotNull(data, "translated_at", comment.getTranslatedAt());
        } else {
            data.put("translated_at", null);
        }
        EntityMapper.putIfNotNull(data, "emojiCountry", comment.getEmojiCountry());
        EntityMapper.putIfNotNull(data, "is_deleted", comment.getIsDeleted());
        EntityMapper.putIfNotNull(data, "created_at", comment.getCreatedAt());
        EntityMapper.putIfNotNull(data, "updated_at", comment.getUpdatedAt());
        EntityMapper.putIfNotNull(data, "parent_comment_id", comment.getParentCommentId());
        EntityMapper.putIfNotNull(data, "reply_to_user_id", comment.getReplyToUserId());
        EntityMapper.putIfNotNull(data, "reply_to_user_nickname", comment.getReplyToUserNickname());
        EntityMapper.putIfNotNull(data, "reply_to_comment_id", comment.getReplyToCommentId());
        EntityMapper.putIfNotNull(data, "comment_level", comment.getCommentLevel());
        EntityMapper.putIfNotNull(data, "root_comment_id", comment.getRootCommentId());
        return data;
    }

    private void applyTranslationFields(Comment comment, Map<String, Object> row) {
        comment.setOriginalLanguage(EntityMapper.getString(row, "original_language"));
        comment.setContentZh(EntityMapper.getString(row, "content_zh"));
        comment.setContentEn(EntityMapper.getString(row, "content_en"));
        comment.setContentJa(EntityMapper.getString(row, "content_ja"));
        comment.setContentKo(EntityMapper.getString(row, "content_ko"));
        comment.setContentDe(EntityMapper.getString(row, "content_de"));
        comment.setContentFr(EntityMapper.getString(row, "content_fr"));
        comment.setContentPt(EntityMapper.getString(row, "content_pt"));
        comment.setContentEs(EntityMapper.getString(row, "content_es"));
        comment.setTranslationStatus(EntityMapper.getString(row, "translation_status"));
        comment.setTranslatedAt(EntityMapper.getOffsetDateTime(row, "translated_at"));
        comment.setEmojiCountry(EntityMapper.getString(row, "emojiCountry"));
    }

    private void resetTranslation(Comment comment) {
        comment.setOriginalLanguage(communityContentTranslationService.normalizeOriginalLanguage(null, comment.getContent()));
        comment.setTranslationStatus("pending");
        comment.setTranslatedAt(null);
        clearTranslatedContent(comment);
        setOriginalLanguageContent(comment);
    }

    private void setOriginalLanguageContent(Comment comment) {
        if (comment.getOriginalLanguage() == null || comment.getContent() == null) {
            return;
        }
        switch (comment.getOriginalLanguage()) {
            case "zh":
                comment.setContentZh(comment.getContent());
                break;
            case "en":
                comment.setContentEn(comment.getContent());
                break;
            case "ja":
                comment.setContentJa(comment.getContent());
                break;
            case "ko":
                comment.setContentKo(comment.getContent());
                break;
            case "de":
                comment.setContentDe(comment.getContent());
                break;
            case "fr":
                comment.setContentFr(comment.getContent());
                break;
            case "pt":
                comment.setContentPt(comment.getContent());
                break;
            case "es":
                comment.setContentEs(comment.getContent());
                break;
            default:
                break;
        }
    }

    private void clearTranslatedContent(Comment comment) {
        comment.setContentZh(null);
        comment.setContentEn(null);
        comment.setContentJa(null);
        comment.setContentKo(null);
        comment.setContentDe(null);
        comment.setContentFr(null);
        comment.setContentPt(null);
        comment.setContentEs(null);
    }

    private String resolveEmojiCountry(String requestEmojiCountry, UUID userId, String originalLanguage) {
        if (requestEmojiCountry != null && !requestEmojiCountry.trim().isEmpty()) {
            return requestEmojiCountry.trim();
        }
        if (userId != null) {
            Optional<User> userOpt = userService.findById(userId);
            if (userOpt.isPresent()) {
                String userEmojiCountry = userOpt.get().getEmojiCountry();
                if (userEmojiCountry != null && !userEmojiCountry.trim().isEmpty()) {
                    return userEmojiCountry.trim();
                }
            }
        }
        return defaultEmojiCountryByLanguage(originalLanguage);
    }

    private String defaultEmojiCountryByLanguage(String originalLanguage) {
        if (originalLanguage == null) {
            return null;
        }
        switch (originalLanguage) {
            case "zh":
                return "\uD83C\uDDE8\uD83C\uDDF3";
            case "en":
                return "\uD83C\uDDFA\uD83C\uDDF8";
            case "ja":
                return "\uD83C\uDDEF\uD83C\uDDF5";
            case "ko":
                return "\uD83C\uDDF0\uD83C\uDDF7";
            case "de":
                return "\uD83C\uDDE9\uD83C\uDDEA";
            case "fr":
                return "\uD83C\uDDEB\uD83C\uDDF7";
            case "pt":
                return "\uD83C\uDDF5\uD83C\uDDF9";
            case "es":
                return "\uD83C\uDDEA\uD83C\uDDF8";
            default:
                return null;
        }
    }
}
