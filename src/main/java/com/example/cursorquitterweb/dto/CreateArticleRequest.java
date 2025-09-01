package com.example.cursorquitterweb.dto;

/**
 * 创建文章请求DTO
 */
public class CreateArticleRequest {
    
    private String type;
    
    private String postImg;
    
    private String color;
    
    private String title;
    
    private String content;
    
    private String status;
    
    public CreateArticleRequest() {}
    
    public CreateArticleRequest(String type, String postImg, String color, String title) {
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
        this.status = "active";
    }
    
    public CreateArticleRequest(String type, String postImg, String color, String title, String content) {
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
        this.content = content;
        this.status = "active";
    }
    
    public CreateArticleRequest(String type, String postImg, String color, String title, String content, String status) {
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
        this.content = content;
        this.status = status;
    }
    
    // Getters and Setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getPostImg() {
        return postImg;
    }
    
    public void setPostImg(String postImg) {
        this.postImg = postImg;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "CreateArticleRequest{" +
                "type='" + type + '\'' +
                ", postImg='" + postImg + '\'' +
                ", color='" + color + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
