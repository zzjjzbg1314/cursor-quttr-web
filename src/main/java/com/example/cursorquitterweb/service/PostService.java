package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.Post;
import com.example.cursorquitterweb.dto.PostPageResult;
import com.example.cursorquitterweb.dto.PostWithUpvotesDto;

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
     * 根据ID查找帖子详情（包含点赞数和评论数）
     */
    Optional<PostWithUpvotesDto> findByIdWithUpvotes(UUID postId);
    
    /**
     * 创建新帖子
     */
    Post createPost(UUID userId, String userNickname, String userStage, String content);
    
    /**
     * 创建新帖子（包含头像URL）
     */
    Post createPost(UUID userId, String userNickname, String userStage, String avatarUrl, String content);
    
    /**
     * 更新帖子信息
     */
    Post updatePost(UUID postId, String content);
    
    /**
     * 删除帖子（软删除）
     */
    void deletePost(UUID postId);
    
    /**
     * 根据用户ID查找用户的所有帖子
     */
    List<Post> findByUserId(UUID userId);
    
    /**
     * 根据用户ID分页查找用户的帖子（已移除 Spring Data Page，返回 List）
     */
    List<Post> findByUserId(UUID userId, int page, int size);
    
    /**
     * 根据用户ID分页查找用户的帖子（包含点赞数和评论数，已移除 Spring Data Page，返回 List）
     */
    List<PostWithUpvotesDto> findByUserIdWithUpvotes(UUID userId, int page, int size);
    
    /**
     * 根据用户昵称查找帖子
     */
    List<Post> findByUserNickname(String userNickname);
    
    /**
     * 根据用户阶段查找帖子
     */
    List<Post> findByUserStage(String userStage);
    
    /**
     * 根据内容搜索帖子
     */
    List<Post> searchByContent(String content);
    
    /**
     * 获取所有帖子（分页，已移除 Spring Data Page，返回 List）
     */
    List<Post> getAllPosts(int page, int size);
    
    /**
     * 获取所有帖子
     */
    List<Post> getAllPosts();
    
    /**
     * 获取所有帖子（分页，包含点赞数，已移除 Spring Data Page，返回 List）
     */
    List<PostWithUpvotesDto> getAllPostsWithUpvotes(int page, int size);
    
    /**
     * 获取所有帖子（分页，包含点赞数，支持排序）
     */
    List<PostWithUpvotesDto> getAllPostsWithUpvotes(int page, int size, String sortBy, String sortDir);
    
    /**
     * 获取所有帖子（分页，包含点赞数，支持排序，返回总数）
     * 使用窗口函数在单次查询中同时获取数据和总数
     */
    PostPageResult getAllPostsWithUpvotesAndCount(int page, int size, String sortBy, String sortDir);
    
    /**
     * 获取所有帖子（包含点赞数）
     */
    List<PostWithUpvotesDto> getAllPostsWithUpvotes();
    
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
    
    /**
     * 统计所有未删除的帖子数量
     */
    long count();
}
