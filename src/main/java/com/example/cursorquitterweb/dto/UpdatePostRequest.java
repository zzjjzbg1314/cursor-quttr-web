package com.example.cursorquitterweb.dto;

/**
 * 更新帖子请求DTO
 */
public class UpdatePostRequest {
    
    private String title;
    private String content;
    
    public UpdatePostRequest() {}
    
    public UpdatePostRequest(String title, String content) {
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
        return "UpdatePostRequest{" +
                "title='" + title + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
