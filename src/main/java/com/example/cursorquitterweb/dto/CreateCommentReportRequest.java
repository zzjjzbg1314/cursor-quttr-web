package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 创建评论举报请求DTO
 */
public class CreateCommentReportRequest {

    @NotNull(message = "被举报的评论ID不能为空")
    @JsonProperty("reported_comment_id")
    private UUID reportedCommentId;

    @NotBlank(message = "举报原因不能为空")
    @JsonProperty("report_reason")
    private String reportReason;

    @JsonProperty("report_notes")
    private String reportNotes;

    @NotNull(message = "举报人ID不能为空")
    @JsonProperty("reporter_user_id")
    private UUID reporterUserId;

    public CreateCommentReportRequest() {
    }

    public CreateCommentReportRequest(UUID reportedCommentId, String reportReason, String reportNotes, UUID reporterUserId) {
        this.reportedCommentId = reportedCommentId;
        this.reportReason = reportReason;
        this.reportNotes = reportNotes;
        this.reporterUserId = reporterUserId;
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
}
