package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 创建帖子举报请求DTO
 */
public class CreatePostReportRequest {
    
    @NotNull(message = "被举报的帖子ID不能为空")
    @JsonProperty("reported_post_id")
    private UUID reportedPostId;
    
    @NotBlank(message = "举报原因不能为空")
    @JsonProperty("report_reason")
    private String reportReason;
    
    @JsonProperty("report_notes")
    private String reportNotes;
    
    @NotNull(message = "举报人ID不能为空")
    @JsonProperty("reporter_user_id")
    private UUID reporterUserId;
    
    public CreatePostReportRequest() {
    }
    
    public CreatePostReportRequest(UUID reportedPostId, String reportReason, String reportNotes, UUID reporterUserId) {
        this.reportedPostId = reportedPostId;
        this.reportReason = reportReason;
        this.reportNotes = reportNotes;
        this.reporterUserId = reporterUserId;
    }
    
    // Getters and Setters
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
    
    @Override
    public String toString() {
        return "CreatePostReportRequest{" +
                "reportedPostId=" + reportedPostId +
                ", reportReason='" + reportReason + '\'' +
                ", reportNotes='" + reportNotes + '\'' +
                ", reporterUserId=" + reporterUserId +
                '}';
    }
}

