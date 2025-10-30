package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 创建用户举报请求DTO
 */
public class CreateUserReportRequest {
    
    @NotNull(message = "被举报的用户ID不能为空")
    @JsonProperty("reported_user_id")
    private UUID reportedUserId;
    
    @NotNull(message = "举报人ID不能为空")
    @JsonProperty("reporter_user_id")
    private UUID reporterUserId;
    
    @NotBlank(message = "举报原因不能为空")
    @JsonProperty("reason")
    private String reason;
    
    @JsonProperty("remark")
    private String remark;
    
    public CreateUserReportRequest() {
    }
    
    public CreateUserReportRequest(UUID reportedUserId, UUID reporterUserId, String reason, String remark) {
        this.reportedUserId = reportedUserId;
        this.reporterUserId = reporterUserId;
        this.reason = reason;
        this.remark = remark;
    }
    
    // Getters and Setters
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
    
    @Override
    public String toString() {
        return "CreateUserReportRequest{" +
                "reportedUserId=" + reportedUserId +
                ", reporterUserId=" + reporterUserId +
                ", reason='" + reason + '\'' +
                ", remark='" + remark + '\'' +
                '}';
    }
}

