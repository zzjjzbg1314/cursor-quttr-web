package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评论实体类
 * 对应数据库表: public.comments
 */
@Entity
@Table(name = "comments", schema = "public")
public class Comment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "comment_id", columnDefinition = "UUID")
    private UUID commentId;
    
    @Column(name = "post_id", nullable = false, columnDefinition = "UUID")
    private UUID postId;
    
    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;
    
    @Column(name = "user_nickname", nullable = false, length = 100)
    private String userNickname;
    
    @Column(name = "user_stage", nullable = false, length = 50)
    private String userStage;
    
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    
    public Comment() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        this.isDeleted = false;
    }
    
    public Comment(UUID postId, UUID userId, String userNickname, String userStage, String content) {
        this();
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.content = content;
    }
    
    // Getters and Setters
    public UUID getCommentId() {
        return commentId;
    }
    
    public void setCommentId(UUID commentId) {
        this.commentId = commentId;
    }
    
    public UUID getPostId() {
        return postId;
    }
    
    public void setPostId(UUID postId) {
        this.postId = postId;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getUserNickname() {
        return userNickname;
    }
    
    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }
    
    public String getUserStage() {
        return userStage;
    }
    
    public void setUserStage(String userStage) {
        this.userStage = userStage;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Boolean getIsDeleted() {
        return isDeleted;
    }
    
    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
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
        return "Comment{" +
                "commentId=" + commentId +
                ", postId=" + postId +
                ", userId=" + userId +
                ", userNickname='" + userNickname + '\'' +
                ", userStage='" + userStage + '\'' +
                ", content='" + content + '\'' +
                ", isDeleted=" + isDeleted +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
