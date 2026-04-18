package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.CommentReport;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评论举报响应DTO
 */
public class CommentReportDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("reported_comment_id")
    private UUID reportedCommentId;

    @JsonProperty("report_reason")
    private String reportReason;

    @JsonProperty("report_notes")
    private String reportNotes;

    @JsonProperty("reporter_user_id")
    private UUID reporterUserId;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    public CommentReportDto() {
    }

    public CommentReportDto(CommentReport commentReport) {
        this.id = commentReport.getId();
        this.reportedCommentId = commentReport.getReportedCommentId();
        this.reportReason = commentReport.getReportReason();
        this.reportNotes = commentReport.getReportNotes();
        this.reporterUserId = commentReport.getReporterUserId();
        this.createdAt = commentReport.getCreatedAt();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReportedCommentId() {
        return reportedCommentId;
    }

    public void setReportedCommentId(UUID reportedCommentId) {
        this.reportedCommentId = reportedCommentId;
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
}
