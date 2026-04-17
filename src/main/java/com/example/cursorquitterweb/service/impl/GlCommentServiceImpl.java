package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.CommentPageResult;
import com.example.cursorquitterweb.dto.CommentWithRepliesDTO;
import com.example.cursorquitterweb.dto.CommentWithRepliesPageResult;
import com.example.cursorquitterweb.entity.Comment;
import com.example.cursorquitterweb.entity.GlComment;
import com.example.cursorquitterweb.service.GlCommentService;
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
public class GlCommentServiceImpl implements GlCommentService {

    @Autowired
    private CloudflareD1Util d1Util;

    @Override
    public Optional<GlComment> findById(UUID commentId) {
        String sql = "SELECT * FROM comments_gl WHERE comment_id = ? AND is_deleted = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(commentId), false);
        return row != null ? Optional.of(mapToComment(row)) : Optional.empty();
    }

    @Override
    public GlComment createComment(String postId, String userId, String userNickname, String userStage, String avatarUrl, String content) {
        UUID postUuid = UUID.fromString(postId);
        UUID userUuid = UUID.fromString(userId);
        if (existsByContent(postUuid, content)) {
            throw new RuntimeException("评论内容已存在，无法重复创建");
        }
        GlComment comment = new GlComment(postUuid, userUuid, userNickname, userStage, avatarUrl, content);
        comment.setCommentLevel((short) 1);
        return saveComment(comment);
    }

    @Override
    public GlComment updateComment(UUID commentId, String content) {
        GlComment comment = findById(commentId).orElseThrow(() -> new RuntimeException("评论不存在或已被删除"));
        comment.setContent(content);
        comment.preUpdate();
        return saveComment(comment);
    }

    @Override
    public GlComment updateComment(UUID commentId, String content, String avatarUrl) {
        GlComment comment = findById(commentId).orElseThrow(() -> new RuntimeException("评论不存在或已被删除"));
        comment.setContent(content);
        if (avatarUrl != null) {
            comment.setAvatarUrl(avatarUrl);
        }
        comment.preUpdate();
        return saveComment(comment);
    }

    @Override
    public void deleteComment(UUID commentId) {
        d1Util.execute("UPDATE comments_gl SET is_deleted = ?, updated_at = ? WHERE comment_id = ?",
            true, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(commentId));
    }

    @Override
    public List<GlComment> findByPostId(UUID postId) {
        String sql = "SELECT * FROM comments_gl WHERE post_id = ? AND is_deleted = ? ORDER BY created_at ASC";
        return d1Util.queryList(sql, EntityMapper.uuidToString(postId), false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<GlComment> findByPostId(UUID postId, int page, int size) {
        String sql = "SELECT * FROM comments_gl WHERE post_id = ? AND is_deleted = ? ORDER BY created_at ASC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(postId), false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<GlComment> findByUserId(UUID userId) {
        String sql = "SELECT * FROM comments_gl WHERE user_id = ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, EntityMapper.uuidToString(userId), false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<GlComment> findByUserId(UUID userId, int page, int size) {
        String sql = "SELECT * FROM comments_gl WHERE user_id = ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(userId), false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<GlComment> findByUserNickname(String userNickname) {
        String sql = "SELECT * FROM comments_gl WHERE user_nickname = ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, userNickname, false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<GlComment> findByUserStage(String userStage) {
        String sql = "SELECT * FROM comments_gl WHERE user_stage = ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, userStage, false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<GlComment> searchByContent(String content) {
        String sql = "SELECT * FROM comments_gl WHERE LOWER(content) LIKE LOWER(?) AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, "%" + content + "%", false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<GlComment> getAllComments(int page, int size) {
        String sql = "SELECT * FROM comments_gl WHERE is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<GlComment> getAllComments() {
        String sql = "SELECT * FROM comments_gl WHERE is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public CommentPageResult getAllCommentsWithCount(int page, int size, String sortBy, String sortDir) {
        String validSortBy = validateSortField(sortBy);
        String validSortDir = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";
        String sql = String.format(
            "SELECT *, COUNT(*) OVER() as total_count FROM comments_gl WHERE is_deleted = ? ORDER BY %s %s LIMIT ? OFFSET ?",
            validSortBy, validSortDir
        );
        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, false, size, offset);
        long totalElements = 0;
        List<GlComment> comments = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }
            Map<String, Object> commentRow = new HashMap<>(row);
            commentRow.remove("total_count");
            comments.add(mapToComment(commentRow));
        }
        return new CommentPageResult(new ArrayList<Comment>(comments), totalElements);
    }

    @Override
    public List<GlComment> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM comments_gl WHERE created_at >= ? AND created_at <= ? AND is_deleted = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql,
            EntityMapper.offsetDateTimeToString(startTime),
            EntityMapper.offsetDateTimeToString(endTime),
            false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public long countByPostId(UUID postId) {
        return d1Util.queryLong("SELECT COUNT(*) as count FROM comments_gl WHERE post_id = ? AND is_deleted = ?",
            EntityMapper.uuidToString(postId), false);
    }

    @Override
    public Map<UUID, Long> countByPostIdsBatch(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new HashMap<>();
        }
        String placeholders = postIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> params = postIds.stream().map(EntityMapper::uuidToString).collect(Collectors.toList());
        params.add(false);
        String sql = "SELECT post_id, COUNT(*) as count FROM comments_gl WHERE post_id IN (" + placeholders + ") AND is_deleted = ? GROUP BY post_id";
        List<Map<String, Object>> rows = d1Util.queryList(sql, params.toArray());
        Map<UUID, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID postId = EntityMapper.getUUID(row, "post_id");
            Long count = EntityMapper.getLong(row, "count");
            if (postId != null) {
                result.put(postId, count != null ? count : 0L);
            }
        }
        for (UUID postId : postIds) {
            result.putIfAbsent(postId, 0L);
        }
        return result;
    }

    @Override
    public long countByUserId(UUID userId) {
        return d1Util.queryLong("SELECT COUNT(*) as count FROM comments_gl WHERE user_id = ? AND is_deleted = ?",
            EntityMapper.uuidToString(userId), false);
    }

    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return d1Util.queryLong("SELECT COUNT(*) as count FROM comments_gl WHERE created_at >= ? AND created_at <= ? AND is_deleted = ?",
            EntityMapper.offsetDateTimeToString(startTime), EntityMapper.offsetDateTimeToString(endTime), false);
    }

    @Override
    public void deleteCommentsByPostId(UUID postId) {
        d1Util.execute("UPDATE comments_gl SET is_deleted = ?, updated_at = ? WHERE post_id = ?",
            true, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(postId));
    }

    @Override
    public GlComment createReplyComment(String postId, String userId, String userNickname, String userStage,
                                        String avatarUrl, String content, String parentCommentId,
                                        String replyToUserId, String replyToUserNickname, String replyToCommentId) {
        UUID postUuid = UUID.fromString(postId);
        UUID userUuid = UUID.fromString(userId);
        UUID parentCommentUuid = parentCommentId != null ? UUID.fromString(parentCommentId) : null;
        UUID replyToUserUuid = replyToUserId != null ? UUID.fromString(replyToUserId) : null;
        UUID replyToCommentUuid = replyToCommentId != null ? UUID.fromString(replyToCommentId) : null;
        if (existsByContent(postUuid, content)) {
            throw new RuntimeException("回复内容已存在，无法重复创建");
        }
        UUID rootCommentUuid = null;
        if (parentCommentUuid != null) {
            GlComment parent = findById(parentCommentUuid).orElseThrow(() -> new RuntimeException("父评论不存在或已被删除"));
            rootCommentUuid = parent.getCommentLevel() == 1 ? parent.getCommentId() : parent.getRootCommentId();
        }
        GlComment comment = new GlComment(postUuid, userUuid, userNickname, userStage, avatarUrl, content,
            parentCommentUuid, replyToUserUuid, replyToUserNickname, replyToCommentUuid, rootCommentUuid);
        return saveComment(comment);
    }

    @Override
    public List<GlComment> findTopLevelCommentsByPostId(UUID postId) {
        String sql = "SELECT * FROM comments_gl WHERE post_id = ? AND comment_level = ? AND is_deleted = ? ORDER BY created_at ASC";
        return d1Util.queryList(sql, EntityMapper.uuidToString(postId), 1, false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<GlComment> findTopLevelCommentsByPostId(UUID postId, int page, int size) {
        String sql = "SELECT * FROM comments_gl WHERE post_id = ? AND comment_level = ? AND is_deleted = ? ORDER BY created_at ASC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(postId), 1, false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<GlComment> findRepliesByRootCommentId(UUID rootCommentId) {
        String sql = "SELECT * FROM comments_gl WHERE root_comment_id = ? AND comment_level = ? AND is_deleted = ? ORDER BY created_at ASC";
        return d1Util.queryList(sql, EntityMapper.uuidToString(rootCommentId), 2, false).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    @Override
    public List<CommentWithRepliesDTO> findCommentsWithRepliesByPostId(UUID postId) {
        List<GlComment> topLevelComments = findTopLevelCommentsByPostId(postId);
        if (topLevelComments.isEmpty()) {
            return new ArrayList<>();
        }
        List<UUID> rootCommentIds = topLevelComments.stream().map(GlComment::getCommentId).collect(Collectors.toList());
        List<GlComment> allReplies = findRepliesByRootCommentIds(rootCommentIds);
        Map<UUID, List<GlComment>> repliesMap = allReplies.stream().collect(Collectors.groupingBy(GlComment::getRootCommentId));
        return topLevelComments.stream()
            .map(comment -> new CommentWithRepliesDTO(comment, new ArrayList<Comment>(repliesMap.getOrDefault(comment.getCommentId(), new ArrayList<>()))))
            .collect(Collectors.toList());
    }

    @Override
    public List<CommentWithRepliesDTO> findCommentsWithRepliesByPostId(UUID postId, int page, int size) {
        List<GlComment> topLevelComments = findTopLevelCommentsByPostId(postId, page, size);
        if (topLevelComments.isEmpty()) {
            return new ArrayList<>();
        }
        List<UUID> rootCommentIds = topLevelComments.stream().map(GlComment::getCommentId).collect(Collectors.toList());
        List<GlComment> allReplies = findRepliesByRootCommentIds(rootCommentIds);
        Map<UUID, List<GlComment>> repliesMap = allReplies.stream().collect(Collectors.groupingBy(GlComment::getRootCommentId));
        return topLevelComments.stream()
            .map(comment -> new CommentWithRepliesDTO(comment, new ArrayList<Comment>(repliesMap.getOrDefault(comment.getCommentId(), new ArrayList<>()))))
            .collect(Collectors.toList());
    }

    @Override
    public CommentWithRepliesPageResult findCommentsWithRepliesByPostIdWithCount(UUID postId, int page, int size, String sortBy, String sortDir) {
        String validSortBy = validateCommentSortField(sortBy);
        String validSortDir = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";
        int offset = page * size;
        String postIdStr = EntityMapper.uuidToString(postId);
        String sql = String.format(
            "SELECT c.*, CASE WHEN c.comment_level = 1 THEN COUNT(*) OVER(PARTITION BY c.comment_level) ELSE NULL END as total_count, " +
            "CASE WHEN c.comment_level = 1 THEN 1 ELSE 0 END as is_top_level FROM comments_gl c WHERE c.post_id = ? AND c.is_deleted = ? " +
            "AND ((c.comment_level = 1 AND c.comment_id IN (SELECT comment_id FROM comments_gl WHERE post_id = ? AND comment_level = ? AND is_deleted = ? ORDER BY %s %s LIMIT ? OFFSET ?)) " +
            "OR (c.comment_level = 2 AND c.root_comment_id IN (SELECT comment_id FROM comments_gl WHERE post_id = ? AND comment_level = ? AND is_deleted = ? ORDER BY %s %s LIMIT ? OFFSET ?))) " +
            "ORDER BY is_top_level DESC, c.created_at ASC",
            validSortBy, validSortDir, validSortBy, validSortDir
        );
        List<Map<String, Object>> allRows = d1Util.queryList(sql,
            postIdStr, false,
            postIdStr, 1, false, size, offset,
            postIdStr, 1, false, size, offset
        );
        long totalElements = 0;
        List<GlComment> topLevelComments = new ArrayList<>();
        List<GlComment> allReplies = new ArrayList<>();
        for (Map<String, Object> row : allRows) {
            boolean isTop = row.get("is_top_level") != null && ((Number) row.get("is_top_level")).intValue() == 1;
            if (isTop) {
                if (totalElements == 0 && row.get("total_count") != null) {
                    totalElements = ((Number) row.get("total_count")).longValue();
                }
                Map<String, Object> commentRow = new HashMap<>(row);
                commentRow.remove("total_count");
                commentRow.remove("is_top_level");
                topLevelComments.add(mapToComment(commentRow));
            } else {
                Map<String, Object> replyRow = new HashMap<>(row);
                replyRow.remove("total_count");
                replyRow.remove("is_top_level");
                allReplies.add(mapToComment(replyRow));
            }
        }
        if (topLevelComments.isEmpty()) {
            return new CommentWithRepliesPageResult(new ArrayList<>(), totalElements);
        }
        Map<UUID, List<GlComment>> repliesMap = allReplies.stream().collect(Collectors.groupingBy(GlComment::getRootCommentId));
        List<CommentWithRepliesDTO> content = topLevelComments.stream()
            .map(comment -> new CommentWithRepliesDTO(comment, new ArrayList<Comment>(repliesMap.getOrDefault(comment.getCommentId(), new ArrayList<>()))))
            .collect(Collectors.toList());
        return new CommentWithRepliesPageResult(content, totalElements);
    }

    @Override
    public long countRepliesByRootCommentId(UUID rootCommentId) {
        return d1Util.queryLong("SELECT COUNT(*) as count FROM comments_gl WHERE root_comment_id = ? AND comment_level = ? AND is_deleted = ?",
            EntityMapper.uuidToString(rootCommentId), 2, false);
    }

    @Override
    public void deleteCommentAndReplies(UUID commentId) {
        GlComment comment = findById(commentId).orElse(null);
        if (comment == null) {
            return;
        }
        if (comment.getCommentLevel() == 1) {
            d1Util.execute("UPDATE comments_gl SET is_deleted = ?, updated_at = ? WHERE (comment_id = ? OR root_comment_id = ?)",
                true, EntityMapper.offsetDateTimeToString(OffsetDateTime.now()), EntityMapper.uuidToString(commentId), EntityMapper.uuidToString(commentId));
        } else {
            deleteComment(commentId);
        }
    }

    @Override
    public boolean existsByContent(UUID postId, String content) {
        return d1Util.queryLong("SELECT COUNT(*) as count FROM comments_gl WHERE post_id = ? AND content = ? AND is_deleted = ?",
            EntityMapper.uuidToString(postId), content, false) > 0;
    }

    private List<GlComment> findRepliesByRootCommentIds(List<UUID> rootCommentIds) {
        if (rootCommentIds == null || rootCommentIds.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM comments_gl WHERE root_comment_id IN (");
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
        return d1Util.queryList(sql.toString(), params).stream().map(this::mapToComment).collect(Collectors.toList());
    }

    private GlComment saveComment(GlComment comment) {
        if (comment.getCommentId() == null) {
            comment.setCommentId(UUID.randomUUID());
            comment.setCreatedAt(OffsetDateTime.now());
            comment.setUpdatedAt(OffsetDateTime.now());
            comment.setIsDeleted(false);
            if (comment.getCommentLevel() == null) {
                comment.setCommentLevel((short) 1);
            }
            d1Util.insert("comments_gl", commentToMap(comment));
        } else {
            d1Util.updateById("comments_gl", commentToMap(comment), "comment_id", EntityMapper.uuidToString(comment.getCommentId()));
        }
        return comment;
    }

    private GlComment mapToComment(Map<String, Object> row) {
        GlComment comment = new GlComment();
        comment.setCommentId(EntityMapper.getUUID(row, "comment_id"));
        comment.setPostId(EntityMapper.getUUID(row, "post_id"));
        comment.setUserId(EntityMapper.getUUID(row, "user_id"));
        comment.setUserNickname(EntityMapper.getString(row, "user_nickname"));
        comment.setUserStage(EntityMapper.getString(row, "user_stage"));
        comment.setAvatarUrl(EntityMapper.getString(row, "avatar_url"));
        comment.setContent(EntityMapper.getString(row, "content"));
        Object isDeletedObj = row.get("is_deleted");
        if (isDeletedObj instanceof Boolean) {
            comment.setIsDeleted((Boolean) isDeletedObj);
        } else if (isDeletedObj instanceof Number) {
            comment.setIsDeleted(((Number) isDeletedObj).intValue() != 0);
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

    private Map<String, Object> commentToMap(GlComment comment) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "comment_id", comment.getCommentId());
        EntityMapper.putIfNotNull(data, "post_id", comment.getPostId());
        EntityMapper.putIfNotNull(data, "user_id", comment.getUserId());
        EntityMapper.putIfNotNull(data, "user_nickname", comment.getUserNickname());
        EntityMapper.putIfNotNull(data, "user_stage", comment.getUserStage());
        EntityMapper.putIfNotNull(data, "avatar_url", comment.getAvatarUrl());
        EntityMapper.putIfNotNull(data, "content", comment.getContent());
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

    private String validateSortField(String sortBy) {
        String[] allowedFields = {"created_at", "updated_at", "user_nickname", "user_stage", "comment_level"};
        for (String field : allowedFields) {
            if (field.equalsIgnoreCase(sortBy)) {
                return field;
            }
        }
        return "created_at";
    }

    private String validateCommentSortField(String sortBy) {
        String[] allowedFields = {"created_at", "updated_at", "user_nickname", "user_stage"};
        for (String field : allowedFields) {
            if (field.equalsIgnoreCase(sortBy)) {
                return field;
            }
        }
        return "created_at";
    }
}
