package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 冥想视频实体类
 * 对应数据库表: public.meditate_video
 */
@Entity
@Table(name = "meditate_video", schema = "public")
public class MeditateVideo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "image")
    private String image;
    
    @Column(name = "videoUrl")
    private String videoUrl;
    
    @Column(name = "audioUrl")
    private String audioUrl;
    
    @ElementCollection
    @CollectionTable(name = "meditate_video_quotes", joinColumns = @JoinColumn(name = "meditate_video_id"))
    @Column(name = "quote")
    private List<String> meditateQuotes;
    
    @Column(name = "color")
    private String color;
    
    @Column(name = "createAt", nullable = false)
    private OffsetDateTime createAt;
    
    @Column(name = "updateAt", nullable = false)
    private OffsetDateTime updateAt;
    
    public MeditateVideo() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }
    
    public MeditateVideo(String title, String image, String videoUrl, String audioUrl, 
                        List<String> meditateQuotes, String color) {
        this();
        this.title = title;
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
    
    @PreUpdate
    public void preUpdate() {
        this.updateAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "MeditateVideo{" +
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
