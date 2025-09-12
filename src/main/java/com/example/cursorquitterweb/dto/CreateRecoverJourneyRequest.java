package com.example.cursorquitterweb.dto;

/**
 * 创建康复记录请求DTO
 */
public class CreateRecoverJourneyRequest {
    
    private String userId;
    private String fellContent;
    
    public CreateRecoverJourneyRequest() {}
    
    public CreateRecoverJourneyRequest(String userId, String fellContent) {
        this.userId = userId;
        this.fellContent = fellContent;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
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
