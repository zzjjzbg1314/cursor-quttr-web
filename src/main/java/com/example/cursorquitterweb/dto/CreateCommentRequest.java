package com.example.cursorquitterweb.dto;

/**
 * 创建一级评论请求DTO
 * 用于直接评论帖子
 */
public class CreateCommentRequest {
    
    private String postId;  // 帖子ID
    private String userId;  // 用户ID
    private String userNickname;  // 用户昵称
    private String userStage;  // 用户阶段
    private String avatarUrl;  // 用户头像
    private String content;  // 评论内容
    
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
