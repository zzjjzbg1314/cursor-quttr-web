package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.PostLike;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 帖子点赞服务接口
 */
public interface PostLikeService {
    
    /**
     * 根据帖子ID查找点赞信息
     */
    Optional<PostLike> findByPostId(UUID postId);
    
    /**
     * 创建帖子点赞记录
     */
    PostLike createPostLike(UUID postId);
    
    /**
     * 点赞帖子
     */
    PostLike likePost(UUID postId);
    
    /**
     * 取消点赞帖子
     */
    PostLike unlikePost(UUID postId);
    
    /**
     * 设置帖子点赞数
     */
    PostLike setLikeCount(UUID postId, Integer likeCount);
    
    /**
     * 重置帖子点赞数
     */
    PostLike resetLikeCount(UUID postId);
    
    /**
     * 获取帖子点赞数
     */
    Integer getLikeCount(UUID postId);
    
    /**
     * 根据点赞数范围查找帖子
     */
    List<PostLike> findByLikeCountRange(Integer minCount, Integer maxCount);
    
    /**
     * 查找点赞数大于指定值的帖子
     */
    List<PostLike> findByLikeCountGreaterThan(Integer minCount);
    
    /**
     * 查找点赞数小于指定值的帖子
     */
    List<PostLike> findByLikeCountLessThan(Integer maxCount);
    
    /**
     * 根据更新时间范围查找帖子点赞信息
     */
    List<PostLike> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计总点赞数
     */
    Long getTotalLikeCount();
    
    /**
     * 统计平均点赞数
     */
    Double getAverageLikeCount();
    
    /**
     * 获取点赞数最多的前N个帖子
     */
    List<PostLike> getTopLikedPosts();
    
    /**
     * 获取点赞数最多的前N个帖子
     */
    List<PostLike> getTopLikedPosts(int limit);
    
    /**
     * 批量创建帖子点赞记录
     */
    List<PostLike> createPostLikesForPosts(List<UUID> postIds);
    
    /**
     * 删除帖子点赞记录（当帖子被删除时调用）
     */
    void deletePostLike(UUID postId);
}
