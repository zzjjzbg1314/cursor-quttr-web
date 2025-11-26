package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 帖子举报实体类
 * 对应数据库表: post_reports
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class PostReport {
    
    private UUID id;
    
    private UUID reportedPostId;
    
    private String reportReason;
    
    private String reportNotes;
    
    private UUID reporterUserId;
    
    private OffsetDateTime createdAt;
    
    public PostReport() {
        this.createdAt = OffsetDateTime.now();
    }
    
    public PostReport(UUID reportedPostId, String reportReason, String reportNotes, UUID reporterUserId) {
        this();
        this.reportedPostId = reportedPostId;
        this.reportReason = reportReason;
        this.reportNotes = reportNotes;
        this.reporterUserId = reporterUserId;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getReportedPostId() {
        return reportedPostId;
    }
    
    public void setReportedPostId(UUID reportedPostId) {
        this.reportedPostId = reportedPostId;
    }
    
    public String getReportReason() {
        return reportReason;
    }
    
    public void setReportReason(String reportReason) {
        this.reportReason = reportReason;
    }
    
    public String getReportNotes() {
        return reportNotes;
    }
    
    public void setReportNotes(String reportNotes) {
        this.reportNotes = reportNotes;
    }
    
    public UUID getReporterUserId() {
        return reporterUserId;
    }
    
    public void setReporterUserId(UUID reporterUserId) {
        this.reporterUserId = reporterUserId;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "PostReport{" +
                "id=" + id +
                ", reportedPostId=" + reportedPostId +
                ", reportReason='" + reportReason + '\'' +
                ", reportNotes='" + reportNotes + '\'' +
                ", reporterUserId=" + reporterUserId +
                ", createdAt=" + createdAt +
                '}';
    }
}

