package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.UserReport;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户举报响应DTO
 */
public class UserReportDto {
    
    @JsonProperty("id")
    private UUID id;
    
    @JsonProperty("reported_user_id")
    private UUID reportedUserId;
    
    @JsonProperty("reporter_user_id")
    private UUID reporterUserId;
    
    @JsonProperty("reason")
    private String reason;
    
    @JsonProperty("remark")
    private String remark;
    
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;
    
    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;
    
    public UserReportDto() {
    }
    
    public UserReportDto(UserReport userReport) {
        this.id = userReport.getId();
        this.reportedUserId = userReport.getReportedUserId();
        this.reporterUserId = userReport.getReporterUserId();
        this.reason = userReport.getReason();
        this.remark = userReport.getRemark();
        this.createdAt = userReport.getCreatedAt();
        this.updatedAt = userReport.getUpdatedAt();
    }
    
    public UserReportDto(UUID id, UUID reportedUserId, UUID reporterUserId, String reason, 
                        String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.reportedUserId = reportedUserId;
        this.reporterUserId = reporterUserId;
        this.reason = reason;
        this.remark = remark;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getReportedUserId() {
        return reportedUserId;
    }
    
    public void setReportedUserId(UUID reportedUserId) {
        this.reportedUserId = reportedUserId;
    }
    
    public UUID getReporterUserId() {
        return reporterUserId;
    }
    
    public void setReporterUserId(UUID reporterUserId) {
        this.reporterUserId = reporterUserId;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public String toString() {
        return "UserReportDto{" +
                "id=" + id +
                ", reportedUserId=" + reportedUserId +
                ", reporterUserId=" + reporterUserId +
                ", reason='" + reason + '\'' +
                ", remark='" + remark + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

