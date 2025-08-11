package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 评论服务接口
 */
public interface CommentService {
    
    /**
     * 根据ID查找评论
     */
    Optional<Comment> findById(UUID commentId);
    
    /**
     * 创建新评论
     */
    Comment createComment(UUID postId, UUID userId, String userNickname, String userStage, String content);
    
    /**
     * 更新评论内容
     */
    Comment updateComment(UUID commentId, String content);
    
    /**
     * 删除评论（软删除）
     */
    void deleteComment(UUID commentId);
    
    /**
     * 根据帖子ID查找所有评论
     */
    List<Comment> findByPostId(UUID postId);
    
    /**
     * 根据帖子ID分页查找评论
     */
    Page<Comment> findByPostId(UUID postId, Pageable pageable);
    
    /**
     * 根据用户ID查找用户的所有评论
     */
    List<Comment> findByUserId(UUID userId);
    
    /**
     * 根据用户ID分页查找用户的评论
     */
    Page<Comment> findByUserId(UUID userId, Pageable pageable);
    
    /**
     * 根据用户昵称查找评论
     */
    List<Comment> findByUserNickname(String userNickname);
    
    /**
     * 根据用户阶段查找评论
     */
    List<Comment> findByUserStage(String userStage);
    
    /**
     * 根据内容搜索评论
     */
    List<Comment> searchByContent(String content);
    
    /**
     * 获取所有评论（分页）
     */
    Page<Comment> getAllComments(Pageable pageable);
    
    /**
     * 获取所有评论
     */
    List<Comment> getAllComments();
    
    /**
     * 根据时间范围查找评论
     */
    List<Comment> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计帖子的评论数量
     */
    long countByPostId(UUID postId);
    
    /**
     * 统计用户的评论数量
     */
    long countByUserId(UUID userId);
    
    /**
     * 统计指定时间范围内的评论数量
     */
    long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据帖子ID删除所有评论（当帖子被删除时调用）
     */
    void deleteCommentsByPostId(UUID postId);
}
