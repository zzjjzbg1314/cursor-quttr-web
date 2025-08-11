package com.example.cursorquitterweb.dto;

/**
 * 创建帖子请求DTO
 */
public class CreatePostRequest {
    
    private Long userId;
    
    private String userNickname;
    
    private String userStage;
    
    private String title;
    
    private String content;
    
    public CreatePostRequest() {}
    
    public CreatePostRequest(Long userId, String userNickname, String userStage, String title, String content) {
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.title = title;
        this.content = content;
    }
    
    // Getters and Setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
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
        return "CreatePostRequest{" +
                "userId=" + userId +
                ", userNickname='" + userNickname + '\'' +
                ", userStage='" + userStage + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
