package com.example.cursorquitterweb.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 康复日记响应DTO
 */
public class RecoveryJournalDto {
    
    private UUID id;
    
    private UUID userId;
    
    private String userNickname;
    
    private String userAvatarUrl;
    
    private String title;
    
    private String content;
    
    private OffsetDateTime createdAt;
    
    private OffsetDateTime updatedAt;
    
    public RecoveryJournalDto() {}
    
    public RecoveryJournalDto(UUID id, UUID userId, String userNickname, String userAvatarUrl, 
                             String title, String content, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userAvatarUrl = userAvatarUrl;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
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
    
    public String getUserAvatarUrl() {
        return userAvatarUrl;
    }
    
    public void setUserAvatarUrl(String userAvatarUrl) {
        this.userAvatarUrl = userAvatarUrl;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
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
    
    @Override
    public String toString() {
        return "RecoveryJournalDto{" +
                "id=" + id +
                ", userId=" + userId +
                ", userNickname='" + userNickname + '\'' +
                ", userAvatarUrl='" + userAvatarUrl + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
