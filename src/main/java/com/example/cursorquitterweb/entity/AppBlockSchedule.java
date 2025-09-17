package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 应用屏蔽计划实体类
 * 对应数据库表: public.app_block_schedule
 */
@Entity
@Table(name = "app_block_schedule", schema = "public")
public class AppBlockSchedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "title")
    private String title;
    
    @Column(name = "subtitle")
    private String subtitle;
    
    @Column(name = "days")
    private String days;
    
    @Column(name = "time")
    private String time;
    
    @Column(name = "reason")
    private String reason;
    
    @Column(name = "image")
    private String image;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    public AppBlockSchedule() {
        this.createdAt = OffsetDateTime.now();
    }
    
    public AppBlockSchedule(String title, String subtitle, String days, String time, String reason, String image) {
        this();
        this.title = title;
        this.subtitle = subtitle;
        this.days = days;
        this.time = time;
        this.reason = reason;
        this.image = image;
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
    
    public String getDays() {
        return days;
    }
    
    public void setDays(String days) {
        this.days = days;
    }
    
    public String getTime() {
        return time;
    }
    
    public void setTime(String time) {
        this.time = time;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getImage() {
        return image;
    }
    
    public void setImage(String image) {
        this.image = image;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "AppBlockSchedule{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", days='" + days + '\'' +
                ", time='" + time + '\'' +
                ", reason='" + reason + '\'' +
                ", image='" + image + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
