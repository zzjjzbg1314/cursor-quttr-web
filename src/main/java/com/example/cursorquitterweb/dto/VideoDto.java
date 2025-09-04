package com.example.cursorquitterweb.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 视频DTO
 * 用于前端展示的视频信息
 */
public class VideoDto {
    
    private UUID id;
    private String title;
    private String playurl;
    private String posturl;
    private OffsetDateTime createAt;
    private OffsetDateTime updateAt;
    
    public VideoDto() {}
    
    public VideoDto(UUID id, String title, String playurl, String posturl, 
                   OffsetDateTime createAt, OffsetDateTime updateAt) {
        this.id = id;
        this.title = title;
        this.playurl = playurl;
        this.posturl = posturl;
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
    
    @Override
    public String toString() {
        return "VideoDto{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", playurl='" + playurl + '\'' +
                ", posturl='" + posturl + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
