package com.example.cursorquitterweb.dto;

/**
 * 创建回复评论请求DTO
 */
public class CreateReplyRequest {
    
    private String postId;  // 帖子ID
    private String userId;  // 用户ID
    private String userNickname;  // 用户昵称
    private String userStage;  // 用户阶段
    private String avatarUrl;  // 用户头像
    private String content;  // 回复内容
    
    // 回复相关字段
    private String parentCommentId;  // 父评论ID（必需）
    private String replyToUserId;  // 被回复的用户ID
    private String replyToUserNickname;  // 被回复的用户昵称
    private String replyToCommentId;  // 被回复的评论ID（必需）
    
    public CreateReplyRequest() {}
    
    public CreateReplyRequest(String postId, String userId, String userNickname, String userStage, 
                             String avatarUrl, String content, String parentCommentId, 
                             String replyToUserId, String replyToUserNickname, String replyToCommentId) {
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.avatarUrl = avatarUrl;
        this.content = content;
        this.parentCommentId = parentCommentId;
        this.replyToUserId = replyToUserId;
        this.replyToUserNickname = replyToUserNickname;
        this.replyToCommentId = replyToCommentId;
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
    
    public String getParentCommentId() {
        return parentCommentId;
    }
    
    public void setParentCommentId(String parentCommentId) {
        this.parentCommentId = parentCommentId;
    }
    
    public String getReplyToUserId() {
        return replyToUserId;
    }
    
    public void setReplyToUserId(String replyToUserId) {
        this.replyToUserId = replyToUserId;
    }
    
    public String getReplyToUserNickname() {
        return replyToUserNickname;
    }
    
    public void setReplyToUserNickname(String replyToUserNickname) {
        this.replyToUserNickname = replyToUserNickname;
    }
    
    public String getReplyToCommentId() {
        return replyToCommentId;
    }
    
    public void setReplyToCommentId(String replyToCommentId) {
        this.replyToCommentId = replyToCommentId;
    }
    
    @Override
    public String toString() {
        return "CreateReplyRequest{" +
                "postId='" + postId + '\'' +
                ", userId='" + userId + '\'' +
                ", userNickname='" + userNickname + '\'' +
                ", userStage='" + userStage + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", content='" + content + '\'' +
                ", parentCommentId='" + parentCommentId + '\'' +
                ", replyToUserId='" + replyToUserId + '\'' +
                ", replyToUserNickname='" + replyToUserNickname + '\'' +
                ", replyToCommentId='" + replyToCommentId + '\'' +
                '}';
    }
}

