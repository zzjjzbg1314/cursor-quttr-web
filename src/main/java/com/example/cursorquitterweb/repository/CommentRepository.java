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
import java.util.UUID;

/**
 * 评论数据访问接口
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {
    
    /**
     * 根据评论ID查找评论（排除已删除的）
     */
    Optional<Comment> findByCommentIdAndIsDeletedFalse(UUID commentId);
    
    /**
     * 根据帖子ID查找所有评论（排除已删除的）
     */
    List<Comment> findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID postId);
    
    /**
     * 根据帖子ID分页查找评论（排除已删除的）
     */
    Page<Comment> findByPostIdAndIsDeletedFalse(Pageable pageable, UUID postId);
    
    /**
     * 根据用户ID查找用户的所有评论（排除已删除的）
     */
    List<Comment> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID userId);
    
    /**
     * 根据用户ID分页查找用户的评论（排除已删除的）
     */
    Page<Comment> findByUserIdAndIsDeletedFalse(Pageable pageable, UUID userId);
    
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
    long countByPostIdAndIsDeletedFalse(UUID postId);
    
    /**
     * 统计用户的评论数量（排除已删除的）
     */
    long countByUserIdAndIsDeletedFalse(UUID userId);
    
    /**
     * 统计指定时间范围内的评论数量（排除已删除的）
     */
    long countByCreatedAtBetweenAndIsDeletedFalse(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 软删除评论
     */
    @Modifying
    @Query("UPDATE Comment c SET c.isDeleted = true, c.updatedAt = :updatedAt WHERE c.commentId = :commentId")
    void softDeleteComment(@Param("commentId") UUID commentId, @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * 根据帖子ID软删除所有评论
     */
    @Modifying
    @Query("UPDATE Comment c SET c.isDeleted = true, c.updatedAt = :updatedAt WHERE c.postId = :postId")
    void softDeleteCommentsByPostId(@Param("postId") UUID postId, @Param("updatedAt") OffsetDateTime updatedAt);
    
    // ============= 新增：小红书风格的评论回复查询方法 =============
    
    /**
     * 获取帖子的所有一级评论（直接评论帖子的，comment_level=1）
     * 按创建时间升序排列
     */
    List<Comment> findByPostIdAndCommentLevelAndIsDeletedFalseOrderByCreatedAtAsc(UUID postId, Short commentLevel);
    
    /**
     * 分页获取帖子的所有一级评论（直接评论帖子的，comment_level=1）
     */
    Page<Comment> findByPostIdAndCommentLevelAndIsDeletedFalse(UUID postId, Short commentLevel, Pageable pageable);
    
    /**
     * 获取某个根评论下的所有回复（comment_level=2）
     * 按创建时间升序排列
     * 
     * root_comment_id: 根评论ID，同一回复链的所有评论指向同一个根评论
     */
    List<Comment> findByRootCommentIdAndCommentLevelAndIsDeletedFalseOrderByCreatedAtAsc(UUID rootCommentId, Short commentLevel);
    
    /**
     * 获取某个评论的直接回复（parent_comment_id指向该评论）
     * 按创建时间升序排列
     * 
     * parent_comment_id: 指向父评论，NULL表示直接评论帖子
     */
    List<Comment> findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID parentCommentId);
    
    /**
     * 统计某个根评论下的回复数量
     */
    long countByRootCommentIdAndCommentLevelAndIsDeletedFalse(UUID rootCommentId, Short commentLevel);
    
    /**
     * 统计某个评论的直接回复数量
     */
    long countByParentCommentIdAndIsDeletedFalse(UUID parentCommentId);
    
    /**
     * 批量查询多个根评论的回复
     * 用于优化性能，一次性获取多个一级评论的所有回复
     */
    @Query("SELECT c FROM Comment c WHERE c.rootCommentId IN :rootCommentIds AND c.commentLevel = 2 AND c.isDeleted = false ORDER BY c.rootCommentId, c.createdAt ASC")
    List<Comment> findRepliesByRootCommentIds(@Param("rootCommentIds") List<UUID> rootCommentIds);
    
    /**
     * 软删除某个评论及其所有回复（级联删除）
     * 删除一个根评论时，需要同时删除该评论下的所有回复
     */
    @Modifying
    @Query("UPDATE Comment c SET c.isDeleted = true, c.updatedAt = :updatedAt WHERE c.commentId = :commentId OR c.rootCommentId = :commentId")
    void softDeleteCommentAndReplies(@Param("commentId") UUID commentId, @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * 查询某个用户在某个帖子下的所有评论和回复
     */
    List<Comment> findByPostIdAndUserIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID postId, UUID userId);
    
    /**
     * 获取某条评论被回复的次数（有多少人回复了这条评论）
     * reply_to_comment_id: 被回复的评论ID（用于定位具体哪条评论）
     */
    long countByReplyToCommentIdAndIsDeletedFalse(UUID replyToCommentId);
}
