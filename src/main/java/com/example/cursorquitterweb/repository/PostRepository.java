package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.Post;
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
 * 帖子数据访问接口
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    
    /**
     * 根据帖子ID查找帖子（排除已删除的）
     */
    Optional<Post> findByPostIdAndIsDeletedFalse(Long postId);
    
    /**
     * 根据用户ID查找用户的所有帖子（排除已删除的）
     */
    List<Post> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId);
    
    /**
     * 根据用户ID分页查找用户的所有帖子（排除已删除的）
     */
    Page<Post> findByUserIdAndIsDeletedFalse(Pageable pageable, Long userId);
    
    /**
     * 根据用户昵称查找帖子（排除已删除的）
     */
    List<Post> findByUserNicknameAndIsDeletedFalseOrderByCreatedAtDesc(String userNickname);
    
    /**
     * 根据用户阶段查找帖子（排除已删除的）
     */
    List<Post> findByUserStageAndIsDeletedFalseOrderByCreatedAtDesc(String userStage);
    
    /**
     * 根据标题模糊查询帖子（排除已删除的）
     */
    List<Post> findByTitleContainingIgnoreCaseAndIsDeletedFalseOrderByCreatedAtDesc(String title);
    
    /**
     * 根据内容模糊查询帖子（排除已删除的）
     */
    List<Post> findByContentContainingIgnoreCaseAndIsDeletedFalseOrderByCreatedAtDesc(String content);
    
    /**
     * 查找所有未删除的帖子，按创建时间倒序排列
     */
    List<Post> findByIsDeletedFalseOrderByCreatedAtDesc();
    
    /**
     * 分页查找所有未删除的帖子，按创建时间倒序排列
     */
    Page<Post> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * 根据创建时间范围查找帖子（排除已删除的）
     */
    List<Post> findByCreatedAtBetweenAndIsDeletedFalseOrderByCreatedAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据点赞数排序查找热门帖子（排除已删除的）
     */
    List<Post> findTop10ByIsDeletedFalseOrderByLikeCountDesc();
    
    /**
     * 根据评论数排序查找热门帖子（排除已删除的）
     */
    List<Post> findTop10ByIsDeletedFalseOrderByCommentCountDesc();
    
    /**
     * 统计用户的帖子数量（排除已删除的）
     */
    long countByUserIdAndIsDeletedFalse(Long userId);
    
    /**
     * 统计指定时间范围内的帖子数量（排除已删除的）
     */
    long countByCreatedAtBetweenAndIsDeletedFalse(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 软删除帖子
     */
    @Modifying
    @Query("UPDATE Post p SET p.isDeleted = true, p.updatedAt = :updatedAt WHERE p.postId = :postId")
    void softDeletePost(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * 增加点赞数
     */
    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1, p.updatedAt = :updatedAt WHERE p.postId = :postId")
    void incrementLikeCount(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * 减少点赞数
     */
    @Modifying
    @Query("UPDATE Post p SET p.likeCount = GREATEST(p.likeCount - 1, 0), p.updatedAt = :updatedAt WHERE p.postId = :postId")
    void decrementLikeCount(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * 增加评论数
     */
    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1, p.updatedAt = :updatedAt WHERE p.postId = :postId")
    void incrementCommentCount(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * 减少评论数
     */
    @Modifying
    @Query("UPDATE Post p SET p.commentCount = GREATEST(p.commentCount - 1, 0), p.updatedAt = :updatedAt WHERE p.postId = :postId")
    void decrementCommentCount(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);
}
