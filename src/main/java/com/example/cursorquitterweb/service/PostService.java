package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 帖子服务接口
 */
public interface PostService {
    
    /**
     * 根据ID查找帖子
     */
    Optional<Post> findById(UUID postId);
    
    /**
     * 创建新帖子
     */
    Post createPost(UUID userId, String userNickname, String userStage, String title, String content);
    
    /**
     * 更新帖子信息
     */
    Post updatePost(UUID postId, String title, String content);
    
    /**
     * 删除帖子（软删除）
     */
    void deletePost(UUID postId);
    
    /**
     * 根据用户ID查找用户的所有帖子
     */
    List<Post> findByUserId(UUID userId);
    
    /**
     * 根据用户ID分页查找用户的帖子
     */
    Page<Post> findByUserId(UUID userId, Pageable pageable);
    
    /**
     * 根据用户昵称查找帖子
     */
    List<Post> findByUserNickname(String userNickname);
    
    /**
     * 根据用户阶段查找帖子
     */
    List<Post> findByUserStage(String userStage);
    
    /**
     * 根据标题搜索帖子
     */
    List<Post> searchByTitle(String title);
    
    /**
     * 根据内容搜索帖子
     */
    List<Post> searchByContent(String content);
    
    /**
     * 获取所有帖子（分页）
     */
    Page<Post> getAllPosts(Pageable pageable);
    
    /**
     * 获取所有帖子
     */
    List<Post> getAllPosts();
    
    /**
     * 根据时间范围查找帖子
     */
    List<Post> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计用户的帖子数量
     */
    long countByUserId(UUID userId);
    
    /**
     * 统计指定时间范围内的帖子数量
     */
    long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
}
