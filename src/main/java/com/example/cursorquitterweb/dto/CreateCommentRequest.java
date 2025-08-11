package com.example.cursorquitterweb.dto;

import java.util.UUID;

/**
 * 创建评论请求DTO
 */
public class CreateCommentRequest {
    
    private UUID postId;
    private UUID userId;
    private String userNickname;
    private String userStage;
    private String content;
    
    public CreateCommentRequest() {}
    
    public CreateCommentRequest(UUID postId, UUID userId, String userNickname, String userStage, String content) {
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.content = content;
    }
    
    // Getters and Setters
    public UUID getPostId() {
        return postId;
    }
    
    public void setPostId(UUID postId) {
        this.postId = postId;
    }
    
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
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    @Override
    public String toString() {
        return "CreateCommentRequest{" +
                "postId=" + postId +
                ", userId=" + userId +
                ", userNickname='" + userNickname + '\'' +
                ", userStage='" + userStage + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
