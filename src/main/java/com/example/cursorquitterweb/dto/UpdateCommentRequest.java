package com.example.cursorquitterweb.dto;

/**
 * 更新评论请求DTO
 */
public class UpdateCommentRequest {
    
    private String content;
    private String avatarUrl;
    
    public UpdateCommentRequest() {}
    
    public UpdateCommentRequest(String content) {
        this.content = content;
    }
    
    public UpdateCommentRequest(String content, String avatarUrl) {
        this.content = content;
        this.avatarUrl = avatarUrl;
    }
    
    // Getters and Setters
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    @Override
    public String toString() {
        return "UpdateCommentRequest{" +
                "content='" + content + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                '}';
    }
}
