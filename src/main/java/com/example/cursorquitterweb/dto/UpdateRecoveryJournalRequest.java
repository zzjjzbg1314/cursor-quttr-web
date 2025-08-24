package com.example.cursorquitterweb.dto;

/**
 * 更新康复日记请求DTO
 */
public class UpdateRecoveryJournalRequest {
    
    private String title;
    
    private String content;
    
    public UpdateRecoveryJournalRequest() {}
    
    public UpdateRecoveryJournalRequest(String title, String content) {
        this.title = title;
        this.content = content;
    }
    
    // Getters and Setters
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
        return "UpdateRecoveryJournalRequest{" +
                "title='" + title + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
