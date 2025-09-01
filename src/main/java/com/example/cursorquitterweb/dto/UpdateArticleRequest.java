package com.example.cursorquitterweb.dto;

/**
 * 更新文章请求DTO
 */
public class UpdateArticleRequest {
    
    private String type;
    
    private String postImg;
    
    private String color;
    
    private String title;
    
    private String content;
    
    public UpdateArticleRequest() {}
    
    public UpdateArticleRequest(String type, String postImg, String color, String title) {
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
    }
    
    public UpdateArticleRequest(String type, String postImg, String color, String title, String content) {
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
        this.content = content;
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
    
    @Override
    public String toString() {
        return "UpdateArticleRequest{" +
                "type='" + type + '\'' +
                ", postImg='" + postImg + '\'' +
                ", color='" + color + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
