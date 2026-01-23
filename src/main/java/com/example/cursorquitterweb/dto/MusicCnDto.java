package com.example.cursorquitterweb.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 音乐DTO（中文版）
 * 用于前端展示的音乐信息
 */
public class MusicCnDto {
    
    private UUID id;
    private String title;
    private String subtitle;
    private String time;
    private String image;
    private String videourl;
    private String videourlLd;
    private String audiourl;
    private OffsetDateTime createAt;
    private OffsetDateTime updateAt;
    private String quotes;
    private String author;
    private String color;
    
    public MusicCnDto() {}
    
    public MusicCnDto(UUID id, String title, String subtitle, String time, String image, 
                    String videourl, String videourlLd, String audiourl, OffsetDateTime createAt, OffsetDateTime updateAt,
                    String quotes, String author, String color) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.time = time;
        this.image = image;
        this.videourl = videourl;
        this.videourlLd = videourlLd;
        this.audiourl = audiourl;
        this.createAt = createAt;
        this.updateAt = updateAt;
        this.quotes = quotes;
        this.author = author;
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
    
    public String getTime() {
        return time;
    }
    
    public void setTime(String time) {
        this.time = time;
    }
    
    public String getImage() {
        return image;
    }
    
    public void setImage(String image) {
        this.image = image;
    }
    
    public String getVideourl() {
        return videourl;
    }
    
    public void setVideourl(String videourl) {
        this.videourl = videourl;
    }
    
    public String getVideourlLd() {
        return videourlLd;
    }
    
    public void setVideourlLd(String videourlLd) {
        this.videourlLd = videourlLd;
    }
    
    public String getAudiourl() {
        return audiourl;
    }
    
    public void setAudiourl(String audiourl) {
        this.audiourl = audiourl;
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
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
    }
    
    @Override
    public String toString() {
        return "MusicCnDto{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", time='" + time + '\'' +
                ", image='" + image + '\'' +
                ", videourl='" + videourl + '\'' +
                ", videourlLd='" + videourlLd + '\'' +
                ", audiourl='" + audiourl + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                ", quotes='" + quotes + '\'' +
                ", author='" + author + '\'' +
                ", color='" + color + '\'' +
                '}';
    }
}

