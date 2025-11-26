package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 帖子实体类
 * 对应数据库表: posts
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class Post {
    
    private UUID postId;
    
    private UUID userId;
    
    private String userNickname;
    
    private String userStage;
    
    private String avatarUrl;
    
    private String content;
    
    private Boolean isDeleted = false;
    
    private OffsetDateTime createdAt;
    
    private OffsetDateTime updatedAt;
    
    public Post() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        this.isDeleted = false;
    }
    
    public Post(UUID userId, String userNickname, String userStage, String content) {
        this();
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.content = content;
    }
    
    public Post(UUID userId, String userNickname, String userStage, String avatarUrl, String content) {
        this();
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.avatarUrl = avatarUrl;
        this.content = content;
    }
    
    // Getters and Setters
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
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
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
    
    /**
     * 更新前调用，设置更新时间
     */
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "Post{" +
                "postId=" + postId +
                ", userId=" + userId +
                ", userNickname='" + userNickname + '\'' +
                ", userStage='" + userStage + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", content='" + content + '\'' +
                ", isDeleted=" + isDeleted +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
