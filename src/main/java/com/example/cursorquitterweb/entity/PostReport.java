package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 帖子举报实体类
 * 对应数据库表: public.post_reports
 */
@Entity
@Table(name = "post_reports", schema = "public")
public class PostReport {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "reported_post_id", nullable = false)
    private UUID reportedPostId;
    
    @Column(name = "report_reason", nullable = false, length = 255)
    private String reportReason;
    
    @Column(name = "report_notes", columnDefinition = "TEXT")
    private String reportNotes;
    
    @Column(name = "reporter_user_id", nullable = false)
    private UUID reporterUserId;
    
    @Column(name = "created_at", nullable = false)
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

