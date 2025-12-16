package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.Feedback;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 反馈响应DTO
 */
public class FeedbackDto {
    
    private Long id;
    
    private String feedbackType;
    
    private String content;
    
    private UUID userId;
    
    private OffsetDateTime createdAt;
    
    public FeedbackDto() {}
    
    public FeedbackDto(Feedback feedback) {
        this.id = feedback.getId();
        this.feedbackType = feedback.getFeedbackType();
        this.content = feedback.getContent();
        this.userId = feedback.getUserId();
        this.createdAt = feedback.getCreatedAt();
    }
    
    public FeedbackDto(Long id, String feedbackType, String content, UUID userId, OffsetDateTime createdAt) {
        this.id = id;
        this.feedbackType = feedbackType;
        this.content = content;
        this.userId = userId;
        this.createdAt = createdAt;
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
        return "FeedbackDto{" +
                "id=" + id +
                ", feedbackType='" + feedbackType + '\'' +
                ", content='" + content + '\'' +
                ", userId=" + userId +
                ", createdAt=" + createdAt +
                '}';
    }
}

