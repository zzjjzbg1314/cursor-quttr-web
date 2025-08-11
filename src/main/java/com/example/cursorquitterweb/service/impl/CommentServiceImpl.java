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

/**
 * 评论服务实现类
 */
@Service
@Transactional
public class CommentServiceImpl implements CommentService {
    
    @Autowired
    private CommentRepository commentRepository;
    
    @Override
    public Optional<Comment> findById(Long commentId) {
        return commentRepository.findByCommentIdAndIsDeletedFalse(commentId);
    }
    
    @Override
    public Comment createComment(Long postId, Long userId, String userNickname, String userStage, String content) {
        Comment comment = new Comment(postId, userId, userNickname, userStage, content);
        return commentRepository.save(comment);
    }
    
    @Override
    public Comment updateComment(Long commentId, String content) {
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
    public void deleteComment(Long commentId) {
        commentRepository.softDeleteComment(commentId, OffsetDateTime.now());
    }
    
    @Override
    public List<Comment> findByPostId(Long postId) {
        return commentRepository.findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(postId);
    }
    
    @Override
    public Page<Comment> findByPostId(Long postId, Pageable pageable) {
        return commentRepository.findByPostIdAndIsDeletedFalse(pageable, postId);
    }
    
    @Override
    public List<Comment> findByUserId(Long userId) {
        return commentRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
    }
    
    @Override
    public Page<Comment> findByUserId(Long userId, Pageable pageable) {
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
    public long countByPostId(Long postId) {
        return commentRepository.countByPostIdAndIsDeletedFalse(postId);
    }
    
    @Override
    public long countByUserId(Long userId) {
        return commentRepository.countByUserIdAndIsDeletedFalse(userId);
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return commentRepository.countByCreatedAtBetweenAndIsDeletedFalse(startTime, endTime);
    }
    
    @Override
    public void deleteCommentsByPostId(Long postId) {
        commentRepository.softDeleteCommentsByPostId(postId, OffsetDateTime.now());
    }
}
