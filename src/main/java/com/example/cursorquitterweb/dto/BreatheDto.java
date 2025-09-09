package com.example.cursorquitterweb.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 呼吸练习DTO
 * 用于前端展示的呼吸练习信息
 */
public class BreatheDto {
    
    private UUID id;
    private String title;
    private String time;
    private String audiourl;
    private OffsetDateTime createAt;
    private OffsetDateTime updateAt;
    
    public BreatheDto() {}
    
    public BreatheDto(UUID id, String title, String time, String audiourl, 
                      OffsetDateTime createAt, OffsetDateTime updateAt) {
        this.id = id;
        this.title = title;
        this.time = time;
        this.audiourl = audiourl;
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
    
    @Override
    public String toString() {
        return "BreatheDto{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", time='" + time + '\'' +
                ", audiourl='" + audiourl + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
