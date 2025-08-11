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
import java.util.UUID;

/**
 * 帖子数据访问接口
 */
@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {
    
    /**
     * 根据帖子ID查找帖子（排除已删除的）
     */
    Optional<Post> findByPostIdAndIsDeletedFalse(UUID postId);
    
    /**
     * 根据用户ID查找用户的所有帖子（排除已删除的）
     */
    List<Post> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID userId);
    
    /**
     * 根据用户ID分页查找用户的所有帖子（排除已删除的）
     */
    Page<Post> findByUserIdAndIsDeletedFalse(Pageable pageable, UUID userId);
    
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
     * 统计用户的帖子数量（排除已删除的）
     */
    long countByUserIdAndIsDeletedFalse(UUID userId);
    
    /**
     * 统计指定时间范围内的帖子数量（排除已删除的）
     */
    long countByCreatedAtBetweenAndIsDeletedFalse(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 软删除帖子
     */
    @Modifying
    @Query("UPDATE Post p SET p.isDeleted = true, p.updatedAt = :updatedAt WHERE p.postId = :postId")
    void softDeletePost(@Param("postId") UUID postId, @Param("updatedAt") OffsetDateTime updatedAt);
}
