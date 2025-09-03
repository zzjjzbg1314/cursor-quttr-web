package com.example.cursorquitterweb.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 视频场景DTO
 * 用于前端展示的视频场景信息
 */
public class VideoScenarioDto {
    
    private UUID videoId;
    private String type;
    private String title;
    private String subtitle;
    private String image;
    private String audiourl;
    private String videourl;
    private String color;
    private String quotes;
    private String author;
    private OffsetDateTime createAt;
    private OffsetDateTime updateAt;
    
    public VideoScenarioDto() {}
    
    public VideoScenarioDto(UUID videoId, String type, String title, String subtitle, 
                           String image, String audiourl, String videourl, String color, 
                           String quotes, String author, OffsetDateTime createAt, OffsetDateTime updateAt) {
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
        this.createAt = createAt;
        this.updateAt = updateAt;
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
    
    public OffsetDateTime getCreateAt() {
        return createAt;
    }
    
    public void setCreateAt(OffsetDateTime createAt) {
        this.createAt = createAt;
    }
    
    public OffsetDateTime getUpdateAt() {
        return updateAt;
    }
    
    public void setUpdateAt(OffsetDateTime updateAt) {
        this.updateAt = updateAt;
    }
    
    @Override
    public String toString() {
        return "VideoScenarioDto{" +
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
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
