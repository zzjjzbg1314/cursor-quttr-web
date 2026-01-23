package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 音乐实体类
 * 对应数据库表: music
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class Music {
    
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
    
    public Music() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }
    
    public Music(String title, String subtitle, String time, String image, 
                 String videourl, String videourlLd, String audiourl, String quotes, String author, String color) {
        this();
        this.title = title;
        this.subtitle = subtitle;
        this.time = time;
        this.image = image;
        this.videourl = videourl;
        this.videourlLd = videourlLd;
        this.audiourl = audiourl;
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
    
    /**
     * 更新前调用，设置更新时间
     */
    public void preUpdate() {
        this.updateAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "Music{" +
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
