package com.example.cursorquitterweb.dto;

/**
 * 更新文章请求DTO
 */
public class UpdateArticleRequest {
    
    private String type;
    
    private String postImg;
    
    private String color;
    
    private String title;
    
    public UpdateArticleRequest() {}
    
    public UpdateArticleRequest(String type, String postImg, String color, String title) {
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
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
    
    @Override
    public String toString() {
        return "UpdateArticleRequest{" +
                "type='" + type + '\'' +
                ", postImg='" + postImg + '\'' +
                ", color='" + color + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}
