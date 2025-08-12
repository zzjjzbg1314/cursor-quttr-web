package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.Comment;
import com.example.cursorquitterweb.repository.CommentRepository;
import com.example.cursorquitterweb.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public Comment createComment(String postId, String userId, String userNickname, String userStage, String content) {
        try {
            // 将String类型的ID转换为UUID类型
            UUID postUuid = UUID.fromString(postId);
            UUID userUuid = UUID.fromString(userId);
            
            Comment comment = new Comment(postUuid, userUuid, userNickname, userStage, content);
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
}
