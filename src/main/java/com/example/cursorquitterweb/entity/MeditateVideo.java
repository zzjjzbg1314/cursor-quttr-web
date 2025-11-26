package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 冥想视频实体类
 * 对应数据库表: meditate_video
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 * 注意：meditateQuotes 字段需要手动处理关联表 meditate_video_quotes
 */
public class MeditateVideo {
    
    private UUID id;
    
    private String title;
    
    private String subtitle;
    
    private String image;
    
    private String videoUrl;
    
    private String audioUrl;
    
    /**
     * 冥想语录列表
     * 注意：需要手动处理关联表 meditate_video_quotes
     */
    private List<String> meditateQuotes;
    
    private String color;
    
    private OffsetDateTime createAt;
    
    private OffsetDateTime updateAt;
    
    public MeditateVideo() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }
    
    public MeditateVideo(String title, String subtitle, String image, String videoUrl, String audioUrl, 
                        List<String> meditateQuotes, String color) {
        this();
        this.title = title;
        this.subtitle = subtitle;
        this.image = image;
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.meditateQuotes = meditateQuotes;
        this.color = color;
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
    
    /**
     * 更新前调用，设置更新时间
     */
    public void preUpdate() {
        this.updateAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "MeditateVideo{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", image='" + image + '\'' +
                ", videoUrl='" + videoUrl + '\'' +
                ", audioUrl='" + audioUrl + '\'' +
                ", meditateQuotes=" + meditateQuotes +
                ", color='" + color + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
