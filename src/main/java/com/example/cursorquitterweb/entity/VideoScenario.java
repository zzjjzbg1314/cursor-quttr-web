package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 视频场景实体类
 * 对应数据库表: public.video_scenario
 */
@Entity
@Table(name = "video_scenario", schema = "public")
public class VideoScenario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "videoId")
    private UUID videoId;
    
    @Column(name = "type")
    private String type;
    
    @Column(name = "title")
    private String title;
    
    @Column(name = "subtitle")
    private String subtitle;
    
    @Column(name = "image")
    private String image;
    
    @Column(name = "audiourl")
    private String audiourl;
    
    @Column(name = "videourl")
    private String videourl;
    
    @Column(name = "color")
    private String color;
    
    @Column(name = "quotes")
    private String quotes;
    
    @Column(name = "author")
    private String author;
    
    @Column(name = "createAt")
    private OffsetDateTime createAt;
    
    @Column(name = "updateAt")
    private OffsetDateTime updateAt;
    
    public VideoScenario() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }
    
    public VideoScenario(String type, String title, String subtitle, String image, 
                        String audiourl, String videourl, String color, String quotes, String author) {
        this();
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
    
    @PreUpdate
    public void preUpdate() {
        this.updateAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "VideoScenario{" +
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
