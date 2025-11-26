package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 帖子点赞实体类
 * 对应数据库表: post_likes
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class PostLike {
    
    private UUID postId;
    
    private Integer likeCount = 0;
    
    private OffsetDateTime updatedAt;
    
    public PostLike() {
        this.likeCount = 0;
        this.updatedAt = OffsetDateTime.now();
    }
    
    public PostLike(UUID postId) {
        this();
        this.postId = postId;
    }
    
    public PostLike(UUID postId, Integer likeCount) {
        this(postId);
        this.likeCount = likeCount;
    }
    
    // Getters and Setters
    public UUID getPostId() {
        return postId;
    }
    
    public void setPostId(UUID postId) {
        this.postId = postId;
    }
    
    public Integer getLikeCount() {
        return likeCount;
    }
    
    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }
    
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * 更新前调用，设置更新时间
     */
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "PostLike{" +
                "postId=" + postId +
                ", likeCount=" + likeCount +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
