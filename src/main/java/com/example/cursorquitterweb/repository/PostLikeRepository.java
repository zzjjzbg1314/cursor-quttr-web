package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.PostLike;
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
 * 帖子点赞数据访问接口
 */
@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {
    
    /**
     * 根据帖子ID查找点赞信息
     */
    Optional<PostLike> findByPostId(UUID postId);
    
    /**
     * 根据点赞数范围查找帖子
     */
    List<PostLike> findByLikeCountBetweenOrderByLikeCountDesc(Integer minCount, Integer maxCount);
    
    /**
     * 查找点赞数大于指定值的帖子
     */
    List<PostLike> findByLikeCountGreaterThanOrderByLikeCountDesc(Integer minCount);
    
    /**
     * 查找点赞数小于指定值的帖子
     */
    List<PostLike> findByLikeCountLessThanOrderByLikeCountDesc(Integer maxCount);
    
    /**
     * 根据更新时间范围查找帖子点赞信息
     */
    List<PostLike> findByUpdatedAtBetweenOrderByUpdatedAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计总点赞数
     */
    @Query("SELECT SUM(p.likeCount) FROM PostLike p")
    Long sumTotalLikeCount();
    
    /**
     * 统计平均点赞数
     */
    @Query("SELECT AVG(p.likeCount) FROM PostLike p")
    Double getAverageLikeCount();
    
    /**
     * 获取点赞数最多的前N个帖子
     */
    List<PostLike> findTop10ByOrderByLikeCountDesc();
    
    /**
     * 增加点赞数
     */
    @Modifying
    @Query("UPDATE PostLike p SET p.likeCount = p.likeCount + 1, p.updatedAt = :updatedAt WHERE p.postId = :postId")
    void incrementLikeCount(@Param("postId") UUID postId, @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * 减少点赞数
     */
    @Modifying
    @Query("UPDATE PostLike p SET p.likeCount = GREATEST(p.likeCount - 1, 0), p.updatedAt = :updatedAt WHERE p.postId = :postId")
    void decrementLikeCount(@Param("postId") UUID postId, @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * 设置点赞数
     */
    @Modifying
    @Query("UPDATE PostLike p SET p.likeCount = :likeCount, p.updatedAt = :updatedAt WHERE p.postId = :postId")
    void setLikeCount(@Param("postId") UUID postId, @Param("likeCount") Integer likeCount, @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * 重置点赞数
     */
    @Modifying
    @Query("UPDATE PostLike p SET p.likeCount = 0, p.updatedAt = :updatedAt WHERE p.postId = :postId")
    void resetLikeCount(@Param("postId") UUID postId, @Param("updatedAt") OffsetDateTime updatedAt);
}
