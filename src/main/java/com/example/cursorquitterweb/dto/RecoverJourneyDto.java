package com.example.cursorquitterweb.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 康复记录响应DTO
 */
public class RecoverJourneyDto {
    
    private UUID id;
    private UUID userId;
    private String fellContent;
    private OffsetDateTime createAt;
    private OffsetDateTime updateAt;
    
    public RecoverJourneyDto() {}
    
    public RecoverJourneyDto(UUID id, UUID userId, String fellContent, OffsetDateTime createAt, OffsetDateTime updateAt) {
        this.id = id;
        this.userId = userId;
        this.fellContent = fellContent;
        this.createAt = createAt;
        this.updateAt = updateAt;
    }
    
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
    
    public String getFellContent() {
        return fellContent;
    }
    
    public void setFellContent(String fellContent) {
        this.fellContent = fellContent;
    }
    
    public OffsetDateTime getCreateAt() {
        return createAt;
    }
    
    public void setCreateAt(OffsetDateTime createAt) {
        this.createAt = createAt;
    }
    
    public OffsetDateTime getUpdateAt() {
        return updateAt;
    }
    
    public void setUpdateAt(OffsetDateTime updateAt) {
        this.updateAt = updateAt;
    }
    
    @Override
    public String toString() {
        return "RecoverJourneyDto{" +
                "id=" + id +
                ", userId=" + userId +
                ", fellContent='" + fellContent + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
