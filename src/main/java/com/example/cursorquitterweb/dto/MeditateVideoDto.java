package com.example.cursorquitterweb.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 冥想视频DTO
 * 用于前端展示的冥想视频信息
 */
public class MeditateVideoDto {
    
    private UUID id;
    private String title;
    private String image;
    private String videoUrl;
    private String audioUrl;
    private List<String> meditateQuotes;
    private String color;
    private OffsetDateTime createAt;
    private OffsetDateTime updateAt;
    
    public MeditateVideoDto() {}
    
    public MeditateVideoDto(UUID id, String title, String image, String videoUrl, 
                           String audioUrl, List<String> meditateQuotes, String color,
                           OffsetDateTime createAt, OffsetDateTime updateAt) {
        this.id = id;
        this.title = title;
        this.image = image;
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.meditateQuotes = meditateQuotes;
        this.color = color;
        this.createAt = createAt;
        this.updateAt = updateAt;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getImage() {
        return image;
    }
    
    public void setImage(String image) {
        this.image = image;
    }
    
    public String getVideoUrl() {
        return videoUrl;
    }
    
    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
    
    public String getAudioUrl() {
        return audioUrl;
    }
    
    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }
    
    public List<String> getMeditateQuotes() {
        return meditateQuotes;
    }
    
    public void setMeditateQuotes(List<String> meditateQuotes) {
        this.meditateQuotes = meditateQuotes;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
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
        return "MeditateVideoDto{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", image='" + image + '\'' +
                ", videoUrl='" + videoUrl + '\'' +
                ", audioUrl='" + audioUrl + '\'' +
                ", meditateQuotes='" + meditateQuotes + '\'' +
                ", color='" + color + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
