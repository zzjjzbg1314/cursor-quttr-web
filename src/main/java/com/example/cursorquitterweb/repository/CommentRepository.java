package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 评论数据访问接口
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    
    /**
     * 根据评论ID查找评论（排除已删除的）
     */
    Optional<Comment> findByCommentIdAndIsDeletedFalse(Long commentId);
    
    /**
     * 根据帖子ID查找所有评论（排除已删除的）
     */
    List<Comment> findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(Long postId);
    
    /**
     * 根据帖子ID分页查找评论（排除已删除的）
     */
    Page<Comment> findByPostIdAndIsDeletedFalse(Pageable pageable, Long postId);
    
    /**
     * 根据用户ID查找用户的所有评论（排除已删除的）
     */
    List<Comment> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId);
    
    /**
     * 根据用户ID分页查找用户的评论（排除已删除的）
     */
    Page<Comment> findByUserIdAndIsDeletedFalse(Pageable pageable, Long userId);
    
    /**
     * 根据用户昵称查找评论（排除已删除的）
     */
    List<Comment> findByUserNicknameAndIsDeletedFalseOrderByCreatedAtDesc(String userNickname);
    
    /**
     * 根据用户阶段查找评论（排除已删除的）
     */
    List<Comment> findByUserStageAndIsDeletedFalseOrderByCreatedAtDesc(String userStage);
    
    /**
     * 根据内容模糊查询评论（排除已删除的）
     */
    List<Comment> findByContentContainingIgnoreCaseAndIsDeletedFalseOrderByCreatedAtDesc(String content);
    
    /**
     * 查找所有未删除的评论，按创建时间倒序排列
     */
    List<Comment> findByIsDeletedFalseOrderByCreatedAtDesc();
    
    /**
     * 分页查找所有未删除的评论，按创建时间倒序排列
     */
    Page<Comment> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * 根据创建时间范围查找评论（排除已删除的）
     */
    List<Comment> findByCreatedAtBetweenAndIsDeletedFalseOrderByCreatedAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计帖子的评论数量（排除已删除的）
     */
    long countByPostIdAndIsDeletedFalse(Long postId);
    
    /**
     * 统计用户的评论数量（排除已删除的）
     */
    long countByUserIdAndIsDeletedFalse(Long userId);
    
    /**
     * 统计指定时间范围内的评论数量（排除已删除的）
     */
    long countByCreatedAtBetweenAndIsDeletedFalse(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 软删除评论
     */
    @Modifying
    @Query("UPDATE Comment c SET c.isDeleted = true, c.updatedAt = :updatedAt WHERE c.commentId = :commentId")
    void softDeleteComment(@Param("commentId") Long commentId, @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * 根据帖子ID软删除所有评论
     */
    @Modifying
    @Query("UPDATE Comment c SET c.isDeleted = true, c.updatedAt = :updatedAt WHERE c.postId = :postId")
    void softDeleteCommentsByPostId(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);
}
