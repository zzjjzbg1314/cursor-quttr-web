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
    
    // 新增字段支持回复功能
    private String parentCommentId;  // 父评论ID，为null表示直接评论帖子
    private String replyToUserId;  // 被回复的用户ID
    private String replyToUserNickname;  // 被回复的用户昵称
    private String replyToCommentId;  // 被回复的评论ID
    private String rootCommentId;  // 根评论ID
    
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
    
    public String getRootCommentId() {
        return rootCommentId;
    }
    
    public void setRootCommentId(String rootCommentId) {
        this.rootCommentId = rootCommentId;
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
                ", parentCommentId='" + parentCommentId + '\'' +
                ", replyToUserId='" + replyToUserId + '\'' +
                ", replyToUserNickname='" + replyToUserNickname + '\'' +
                ", replyToCommentId='" + replyToCommentId + '\'' +
                ", rootCommentId='" + rootCommentId + '\'' +
                '}';
    }
}
