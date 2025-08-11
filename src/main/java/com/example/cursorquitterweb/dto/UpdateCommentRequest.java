package com.example.cursorquitterweb.dto;

/**
 * 更新评论请求DTO
 */
public class UpdateCommentRequest {
    
    private String content;
    
    public UpdateCommentRequest() {}
    
    public UpdateCommentRequest(String content) {
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
        return "UpdateCommentRequest{" +
                "content='" + content + '\'' +
                '}';
    }
}
