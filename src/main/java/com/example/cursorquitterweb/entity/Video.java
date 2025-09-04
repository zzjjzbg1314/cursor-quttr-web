package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 视频实体类
 * 对应数据库表: public.video
 */
@Entity
@Table(name = "video", schema = "public")
public class Video {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "playurl", nullable = false)
    private String playurl;
    
    @Column(name = "posturl", nullable = false)
    private String posturl;
    
    @Column(name = "create_at", nullable = false)
    private OffsetDateTime createAt;
    
    @Column(name = "update_at", nullable = false)
    private OffsetDateTime updateAt;
    
    public Video() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }
    
    public Video(String title, String playurl, String posturl) {
        this();
        this.title = title;
        this.playurl = playurl;
        this.posturl = posturl;
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
    
    public String getPlayurl() {
        return playurl;
    }
    
    public void setPlayurl(String playurl) {
        this.playurl = playurl;
    }
    
    public String getPosturl() {
        return posturl;
    }
    
    public void setPosturl(String posturl) {
        this.posturl = posturl;
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
        return "Video{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", playurl='" + playurl + '\'' +
                ", posturl='" + posturl + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
