package com.example.cursorquitterweb.dto;

/**
 * 更新帖子请求DTO
 */
public class UpdatePostRequest {
    
    private String content;
    
    public UpdatePostRequest() {}
    
    public UpdatePostRequest(String content) {
        this.content = content;
    }
    
    // Getters and Setters
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    @Override
    public String toString() {
        return "UpdatePostRequest{" +
                ", content='" + content + '\'' +
                '}';
    }
}
