package com.example.cursorquitterweb.dto;

import java.util.UUID;

/**
 * 创建康复记录请求DTO
 */
public class CreateRecoverJourneyRequest {
    
    private UUID userId;
    private String fellContent;
    
    public CreateRecoverJourneyRequest() {}
    
    public CreateRecoverJourneyRequest(UUID userId, String fellContent) {
        this.userId = userId;
        this.fellContent = fellContent;
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
    
    @Override
    public String toString() {
        return "CreateRecoverJourneyRequest{" +
                "userId=" + userId +
                ", fellContent='" + fellContent + '\'' +
                '}';
    }
}
