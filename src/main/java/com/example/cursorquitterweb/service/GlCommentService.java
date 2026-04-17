package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.CommentPageResult;
import com.example.cursorquitterweb.dto.CommentWithRepliesDTO;
import com.example.cursorquitterweb.dto.CommentWithRepliesPageResult;
import com.example.cursorquitterweb.entity.GlComment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GlCommentService {
    Optional<GlComment> findById(UUID commentId);
    GlComment createComment(String postId, String userId, String userNickname, String userStage, String avatarUrl, String content);
    GlComment updateComment(UUID commentId, String content);
    GlComment updateComment(UUID commentId, String content, String avatarUrl);
    void deleteComment(UUID commentId);
    List<GlComment> findByPostId(UUID postId);
    List<GlComment> findByPostId(UUID postId, int page, int size);
    List<GlComment> findByUserId(UUID userId);
    List<GlComment> findByUserId(UUID userId, int page, int size);
    List<GlComment> findByUserNickname(String userNickname);
    List<GlComment> findByUserStage(String userStage);
    List<GlComment> searchByContent(String content);
    List<GlComment> getAllComments(int page, int size);
    List<GlComment> getAllComments();
    CommentPageResult getAllCommentsWithCount(int page, int size, String sortBy, String sortDir);
    List<GlComment> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    long countByPostId(UUID postId);
    Map<UUID, Long> countByPostIdsBatch(List<UUID> postIds);
    long countByUserId(UUID userId);
    long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    void deleteCommentsByPostId(UUID postId);
    GlComment createReplyComment(String postId, String userId, String userNickname, String userStage,
                                 String avatarUrl, String content, String parentCommentId,
                                 String replyToUserId, String replyToUserNickname, String replyToCommentId);
    List<GlComment> findTopLevelCommentsByPostId(UUID postId);
    List<GlComment> findTopLevelCommentsByPostId(UUID postId, int page, int size);
    List<GlComment> findRepliesByRootCommentId(UUID rootCommentId);
    List<CommentWithRepliesDTO> findCommentsWithRepliesByPostId(UUID postId);
    List<CommentWithRepliesDTO> findCommentsWithRepliesByPostId(UUID postId, int page, int size);
    CommentWithRepliesPageResult findCommentsWithRepliesByPostIdWithCount(UUID postId, int page, int size, String sortBy, String sortDir);
    long countRepliesByRootCommentId(UUID rootCommentId);
    void deleteCommentAndReplies(UUID commentId);
    boolean existsByContent(UUID postId, String content);
}
