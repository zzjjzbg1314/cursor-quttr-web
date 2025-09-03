package com.example.cursorquitterweb.dto;

import javax.validation.constraints.Size;
import java.util.UUID;

/**
 * 更新视频场景请求DTO
 */
public class UpdateVideoScenarioRequest {
    
    private UUID videoId;
    
    @Size(max = 50, message = "视频类型长度不能超过50个字符")
    private String type;
    
    @Size(max = 200, message = "视频标题长度不能超过200个字符")
    private String title;
    
    @Size(max = 500, message = "副标题长度不能超过500个字符")
    private String subtitle;
    
    @Size(max = 500, message = "图片URL长度不能超过500个字符")
    private String image;
    
    @Size(max = 500, message = "音频URL长度不能超过500个字符")
    private String audiourl;
    
    @Size(max = 500, message = "视频URL长度不能超过500个字符")
    private String videourl;
    
    @Size(max = 20, message = "颜色长度不能超过20个字符")
    private String color;
    
    @Size(max = 1000, message = "励志语录长度不能超过1000个字符")
    private String quotes;
    
    @Size(max = 100, message = "作者长度不能超过100个字符")
    private String author;
    
    public UpdateVideoScenarioRequest() {}
    
    public UpdateVideoScenarioRequest(UUID videoId, String type, String title, String subtitle, 
                                    String image, String audiourl, String videourl, String color, 
                                    String quotes, String author) {
        this.videoId = videoId;
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.image = image;
        this.audiourl = audiourl;
        this.videourl = videourl;
        this.color = color;
        this.quotes = quotes;
        this.author = author;
    }
    
    // Getters and Setters
    public UUID getVideoId() {
        return videoId;
    }
    
    public void setVideoId(UUID videoId) {
        this.videoId = videoId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getSubtitle() {
        return subtitle;
    }
    
    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }
    
    public String getImage() {
        return image;
    }
    
    public void setImage(String image) {
        this.image = image;
    }
    
    public String getAudiourl() {
        return audiourl;
    }
    
    public void setAudiourl(String audiourl) {
        this.audiourl = audiourl;
    }
    
    public String getVideourl() {
        return videourl;
    }
    
    public void setVideourl(String videourl) {
        this.videourl = videourl;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
    }
    
    public String getQuotes() {
        return quotes;
    }
    
    public void setQuotes(String quotes) {
        this.quotes = quotes;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    @Override
    public String toString() {
        return "UpdateVideoScenarioRequest{" +
                "videoId=" + videoId +
                ", type='" + type + '\'' +
                ", title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", image='" + image + '\'' +
                ", audiourl='" + audiourl + '\'' +
                ", videourl='" + videourl + '\'' +
                ", color='" + color + '\'' +
                ", quotes='" + quotes + '\'' +
                ", author='" + author + '\'' +
                '}';
    }
}
