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
    Comment createComment(String postId, String userId, String userNickname, String userStage, String avatarUrl, String content);
    
    /**
     * 更新评论内容
     */
    Comment updateComment(UUID commentId, String content);
    
    /**
     * 更新评论内容和头像
     */
    Comment updateComment(UUID commentId, String content, String avatarUrl);
    
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
    
    // ============= 新增：小红书风格的评论回复功能 =============
    
    /**
     * 创建回复评论
     * 
     * @param postId 帖子ID
     * @param userId 用户ID
     * @param userNickname 用户昵称
     * @param userStage 用户阶段
     * @param avatarUrl 用户头像
     * @param content 评论内容
     * @param parentCommentId 父评论ID（指向被回复的评论，NULL表示直接评论帖子）
     * @param replyToUserId 被回复的用户ID
     * @param replyToUserNickname 被回复的用户昵称
     * @param replyToCommentId 被回复的评论ID
     * @return 创建的评论对象
     */
    Comment createReplyComment(String postId, String userId, String userNickname, String userStage, 
                               String avatarUrl, String content, String parentCommentId, 
                               String replyToUserId, String replyToUserNickname, String replyToCommentId);
    
    /**
     * 获取帖子的所有一级评论（不包括回复）
     * comment_level=1: 直接评论帖子
     */
    List<Comment> findTopLevelCommentsByPostId(UUID postId);
    
    /**
     * 分页获取帖子的所有一级评论
     */
    Page<Comment> findTopLevelCommentsByPostId(UUID postId, Pageable pageable);
    
    /**
     * 获取某个评论下的所有回复
     * root_comment_id: 根评论ID，同一回复链的所有评论指向同一个根评论
     */
    List<Comment> findRepliesByRootCommentId(UUID rootCommentId);
    
    /**
     * 获取帖子的所有评论及其回复（分组返回）
     * 返回格式：一级评论列表，每个一级评论包含其所有回复
     */
    List<com.example.cursorquitterweb.dto.CommentWithRepliesDTO> findCommentsWithRepliesByPostId(UUID postId);
    
    /**
     * 分页获取帖子的所有评论及其回复
     */
    Page<com.example.cursorquitterweb.dto.CommentWithRepliesDTO> findCommentsWithRepliesByPostId(UUID postId, Pageable pageable);
    
    /**
     * 统计某个评论下的回复数量
     */
    long countRepliesByRootCommentId(UUID rootCommentId);
    
    /**
     * 删除评论及其所有回复（级联删除）
     * 删除一个根评论时，同时删除该评论下的所有回复
     */
    void deleteCommentAndReplies(UUID commentId);
}
