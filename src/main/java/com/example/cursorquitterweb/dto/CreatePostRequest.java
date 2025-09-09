package com.example.cursorquitterweb.dto;

import java.util.UUID;

/**
 * 创建帖子请求DTO
 */
public class CreatePostRequest {
    
    private UUID userId;
    
    private String userNickname;
    
    private String userStage;
    
    private String avatarUrl;
    
    
    private String content;
    
    public CreatePostRequest() {}
    
    public CreatePostRequest(UUID userId, String userNickname, String userStage, String content) {
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.content = content;
    }
    
    public CreatePostRequest(UUID userId, String userNickname, String userStage, String avatarUrl, String content) {
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.avatarUrl = avatarUrl;
        this.content = content;
    }
    
    // Getters and Setters
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
    
    public String getUserStage() {
        return userStage;
    }
    
    public void setUserStage(String userStage) {
        this.userStage = userStage;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    @Override
    public String toString() {
        return "CreatePostRequest{" +
                "userId=" + userId +
                ", userNickname='" + userNickname + '\'' +
                ", userStage='" + userStage + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
