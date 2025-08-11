package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 帖子点赞实体类
 * 对应数据库表: public.post_likes
 */
@Entity
@Table(name = "post_likes", schema = "public")
public class PostLike {
    
    @Id
    @Column(name = "post_id")
    private UUID postId;
    
    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;
    
    @Column(name = "updated_at", nullable = false)
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
    
    @PreUpdate
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
