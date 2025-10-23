package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.CommentWithRepliesDTO;
import com.example.cursorquitterweb.entity.Comment;
import com.example.cursorquitterweb.repository.CommentRepository;
import com.example.cursorquitterweb.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评论服务实现类
 */
@Service
@Transactional
public class CommentServiceImpl implements CommentService {
    
    @Autowired
    private CommentRepository commentRepository;
    
    @Override
    public Optional<Comment> findById(UUID commentId) {
        return commentRepository.findByCommentIdAndIsDeletedFalse(commentId);
    }
    
    @Override
    public Comment createComment(String postId, String userId, String userNickname, String userStage, String avatarUrl, String content) {
        try {
            // 将String类型的ID转换为UUID类型
            UUID postUuid = UUID.fromString(postId);
            UUID userUuid = UUID.fromString(userId);
            
            // 创建一级评论（直接评论帖子）
            Comment comment = new Comment(postUuid, userUuid, userNickname, userStage, avatarUrl, content);
            // 确保comment_level为1（一级评论）
            comment.setCommentLevel((short) 1);
            return commentRepository.save(comment);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的UUID格式: " + e.getMessage());
        }
    }
    
    @Override
    public Comment updateComment(UUID commentId, String content) {
        Optional<Comment> optionalComment = commentRepository.findByCommentIdAndIsDeletedFalse(commentId);
        if (optionalComment.isPresent()) {
            Comment comment = optionalComment.get();
            comment.setContent(content);
            comment.setUpdatedAt(OffsetDateTime.now());
            return commentRepository.save(comment);
        }
        throw new RuntimeException("评论不存在或已被删除");
    }
    
    @Override
    public Comment updateComment(UUID commentId, String content, String avatarUrl) {
        Optional<Comment> optionalComment = commentRepository.findByCommentIdAndIsDeletedFalse(commentId);
        if (optionalComment.isPresent()) {
            Comment comment = optionalComment.get();
            comment.setContent(content);
            if (avatarUrl != null) {
                comment.setAvatarUrl(avatarUrl);
            }
            comment.setUpdatedAt(OffsetDateTime.now());
            return commentRepository.save(comment);
        }
        throw new RuntimeException("评论不存在或已被删除");
    }
    
    @Override
    public void deleteComment(UUID commentId) {
        commentRepository.softDeleteComment(commentId, OffsetDateTime.now());
    }
    
    @Override
    public List<Comment> findByPostId(UUID postId) {
        return commentRepository.findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(postId);
    }
    
    @Override
    public Page<Comment> findByPostId(UUID postId, Pageable pageable) {
        return commentRepository.findByPostIdAndIsDeletedFalse(pageable, postId);
    }
    
    @Override
    public List<Comment> findByUserId(UUID userId) {
        return commentRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
    }
    
    @Override
    public Page<Comment> findByUserId(UUID userId, Pageable pageable) {
        return commentRepository.findByUserIdAndIsDeletedFalse(pageable, userId);
    }
    
    @Override
    public List<Comment> findByUserNickname(String userNickname) {
        return commentRepository.findByUserNicknameAndIsDeletedFalseOrderByCreatedAtDesc(userNickname);
    }
    
    @Override
    public List<Comment> findByUserStage(String userStage) {
        return commentRepository.findByUserStageAndIsDeletedFalseOrderByCreatedAtDesc(userStage);
    }
    
    @Override
    public List<Comment> searchByContent(String content) {
        return commentRepository.findByContentContainingIgnoreCaseAndIsDeletedFalseOrderByCreatedAtDesc(content);
    }
    
    @Override
    public Page<Comment> getAllComments(Pageable pageable) {
        return commentRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
    }
    
    @Override
    public List<Comment> getAllComments() {
        return commentRepository.findByIsDeletedFalseOrderByCreatedAtDesc();
    }
    
    @Override
    public List<Comment> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return commentRepository.findByCreatedAtBetweenAndIsDeletedFalseOrderByCreatedAtDesc(startTime, endTime);
    }
    
    @Override
    public long countByPostId(UUID postId) {
        return commentRepository.countByPostIdAndIsDeletedFalse(postId);
    }
    
    @Override
    public long countByUserId(UUID userId) {
        return commentRepository.countByUserIdAndIsDeletedFalse(userId);
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return commentRepository.countByCreatedAtBetweenAndIsDeletedFalse(startTime, endTime);
    }
    
    @Override
    public void deleteCommentsByPostId(UUID postId) {
        commentRepository.softDeleteCommentsByPostId(postId, OffsetDateTime.now());
    }
    
    // ============= 新增：小红书风格的评论回复功能实现 =============
    
    @Override
    public Comment createReplyComment(String postId, String userId, String userNickname, String userStage, 
                                     String avatarUrl, String content, String parentCommentId, 
                                     String replyToUserId, String replyToUserNickname, String replyToCommentId) {
        try {
            // 转换字符串ID为UUID
            UUID postUuid = UUID.fromString(postId);
            UUID userUuid = UUID.fromString(userId);
            UUID parentCommentUuid = parentCommentId != null ? UUID.fromString(parentCommentId) : null;
            UUID replyToUserUuid = replyToUserId != null ? UUID.fromString(replyToUserId) : null;
            UUID replyToCommentUuid = replyToCommentId != null ? UUID.fromString(replyToCommentId) : null;
            
            // 查找父评论以确定root_comment_id
            UUID rootCommentUuid = null;
            if (parentCommentUuid != null) {
                Optional<Comment> parentComment = commentRepository.findByCommentIdAndIsDeletedFalse(parentCommentUuid);
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
            
            return commentRepository.save(comment);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的UUID格式: " + e.getMessage());
        }
    }
    
    @Override
    public List<Comment> findTopLevelCommentsByPostId(UUID postId) {
        return commentRepository.findByPostIdAndCommentLevelAndIsDeletedFalseOrderByCreatedAtAsc(postId, (short) 1);
    }
    
    @Override
    public Page<Comment> findTopLevelCommentsByPostId(UUID postId, Pageable pageable) {
        return commentRepository.findByPostIdAndCommentLevelAndIsDeletedFalse(postId, (short) 1, pageable);
    }
    
    @Override
    public List<Comment> findRepliesByRootCommentId(UUID rootCommentId) {
        return commentRepository.findByRootCommentIdAndCommentLevelAndIsDeletedFalseOrderByCreatedAtAsc(rootCommentId, (short) 2);
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
        List<Comment> allReplies = commentRepository.findRepliesByRootCommentIds(rootCommentIds);
        
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
    public Page<CommentWithRepliesDTO> findCommentsWithRepliesByPostId(UUID postId, Pageable pageable) {
        // 1. 分页获取一级评论
        Page<Comment> topLevelCommentsPage = findTopLevelCommentsByPostId(postId, pageable);
        
        if (topLevelCommentsPage.isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
        
        // 2. 获取当前页的一级评论ID列表
        List<UUID> rootCommentIds = topLevelCommentsPage.getContent().stream()
                .map(Comment::getCommentId)
                .collect(Collectors.toList());
        
        // 3. 批量查询这些评论的回复
        List<Comment> allReplies = commentRepository.findRepliesByRootCommentIds(rootCommentIds);
        
        // 4. 将回复按根评论ID分组
        Map<UUID, List<Comment>> repliesMap = allReplies.stream()
                .collect(Collectors.groupingBy(Comment::getRootCommentId));
        
        // 5. 组装结果
        List<CommentWithRepliesDTO> content = topLevelCommentsPage.getContent().stream()
                .map(comment -> {
                    List<Comment> replies = repliesMap.getOrDefault(comment.getCommentId(), new ArrayList<>());
                    return new CommentWithRepliesDTO(comment, replies);
                })
                .collect(Collectors.toList());
        
        return new PageImpl<>(content, pageable, topLevelCommentsPage.getTotalElements());
    }
    
    @Override
    public long countRepliesByRootCommentId(UUID rootCommentId) {
        return commentRepository.countByRootCommentIdAndCommentLevelAndIsDeletedFalse(rootCommentId, (short) 2);
    }
    
    @Override
    public void deleteCommentAndReplies(UUID commentId) {
        commentRepository.softDeleteCommentAndReplies(commentId, OffsetDateTime.now());
    }
}
