package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 反馈实体类
 * 对应数据库表: feedback
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class Feedback {
    
    private Long id;
    
    private String feedbackType;
    
    private String content;
    
    private UUID userId;
    
    private OffsetDateTime createdAt;
    
    public Feedback() {
        this.createdAt = OffsetDateTime.now();
    }
    
    public Feedback(String feedbackType, String content, UUID userId) {
        this();
        this.feedbackType = feedbackType;
        this.content = content;
        this.userId = userId;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getFeedbackType() {
        return feedbackType;
    }
    
    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "Feedback{" +
                "id=" + id +
                ", feedbackType='" + feedbackType + '\'' +
                ", content='" + content + '\'' +
                ", userId=" + userId +
                ", createdAt=" + createdAt +
                '}';
    }
}

