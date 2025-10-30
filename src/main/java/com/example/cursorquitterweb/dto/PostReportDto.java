package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.PostReport;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 帖子举报响应DTO
 */
public class PostReportDto {
    
    @JsonProperty("id")
    private UUID id;
    
    @JsonProperty("reported_post_id")
    private UUID reportedPostId;
    
    @JsonProperty("report_reason")
    private String reportReason;
    
    @JsonProperty("report_notes")
    private String reportNotes;
    
    @JsonProperty("reporter_user_id")
    private UUID reporterUserId;
    
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;
    
    public PostReportDto() {
    }
    
    public PostReportDto(PostReport postReport) {
        this.id = postReport.getId();
        this.reportedPostId = postReport.getReportedPostId();
        this.reportReason = postReport.getReportReason();
        this.reportNotes = postReport.getReportNotes();
        this.reporterUserId = postReport.getReporterUserId();
        this.createdAt = postReport.getCreatedAt();
    }
    
    public PostReportDto(UUID id, UUID reportedPostId, String reportReason, String reportNotes, 
                        UUID reporterUserId, OffsetDateTime createdAt) {
        this.id = id;
        this.reportedPostId = reportedPostId;
        this.reportReason = reportReason;
        this.reportNotes = reportNotes;
        this.reporterUserId = reporterUserId;
        this.createdAt = createdAt;
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
        return "PostReportDto{" +
                "id=" + id +
                ", reportedPostId=" + reportedPostId +
                ", reportReason='" + reportReason + '\'' +
                ", reportNotes='" + reportNotes + '\'' +
                ", reporterUserId=" + reporterUserId +
                ", createdAt=" + createdAt +
                '}';
    }
}

