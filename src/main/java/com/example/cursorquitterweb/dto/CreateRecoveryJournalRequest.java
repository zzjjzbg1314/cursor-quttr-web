package com.example.cursorquitterweb.dto;

/**
 * 创建康复日记请求DTO
 */
public class CreateRecoveryJournalRequest {
    
    private String userId;
    
    private String title;
    
    private String content;
    
    public CreateRecoveryJournalRequest() {}
    
    public CreateRecoveryJournalRequest(String userId, String title, String content) {
        this.userId = userId;
        this.title = title;
        this.content = content;
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
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
    
    @Override
    public String toString() {
        return "CreateRecoveryJournalRequest{" +
                "userId='" + userId + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
