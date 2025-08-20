package com.example.cursorquitterweb.dto;

/**
 * 创建评论请求DTO
 */
public class CreateCommentRequest {
    
    private String postId;  // 改为String类型，便于前端传递
    private String userId;  // 改为String类型，便于前端传递
    private String userNickname;
    private String userStage;
    private String avatarUrl;
    private String content;
    
    public CreateCommentRequest() {}
    
    public CreateCommentRequest(String postId, String userId, String userNickname, String userStage, String content) {
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.content = content;
    }
    
    public CreateCommentRequest(String postId, String userId, String userNickname, String userStage, String avatarUrl, String content) {
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.avatarUrl = avatarUrl;
        this.content = content;
    }
    
    // Getters and Setters
    public String getPostId() {
        return postId;
    }
    
    public void setPostId(String postId) {
        this.postId = postId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
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
        return "CreateCommentRequest{" +
                "postId='" + postId + '\'' +
                ", userId='" + userId + '\'' +
                ", userNickname='" + userNickname + '\'' +
                ", userStage='" + userStage + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
