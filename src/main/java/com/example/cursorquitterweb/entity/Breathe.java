package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 呼吸练习实体类
 * 对应数据库表: public.breathe
 */
@Entity
@Table(name = "breathe", schema = "public")
public class Breathe {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "time")
    private String time;
    
    @Column(name = "audiourl")
    private String audiourl;
    
    @Column(name = "createAt", nullable = false)
    private OffsetDateTime createAt;
    
    @Column(name = "updateAt", nullable = false)
    private OffsetDateTime updateAt;
    
    public Breathe() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }
    
    public Breathe(String title, String time, String audiourl) {
        this();
        this.title = title;
        this.time = time;
        this.audiourl = audiourl;
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
    
    public String getTime() {
        return time;
    }
    
    public void setTime(String time) {
        this.time = time;
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
    
    @PreUpdate
    public void preUpdate() {
        this.updateAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "Breathe{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", time='" + time + '\'' +
                ", audiourl='" + audiourl + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
