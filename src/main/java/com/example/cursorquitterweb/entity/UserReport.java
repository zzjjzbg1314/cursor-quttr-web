package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户举报实体类
 * 对应数据库表: public.user_reports
 */
@Entity
@Table(name = "user_reports", schema = "public")
public class UserReport {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "reported_user_id", nullable = false)
    private UUID reportedUserId;
    
    @Column(name = "reporter_user_id", nullable = false)
    private UUID reporterUserId;
    
    @Column(name = "reason", nullable = false, length = 255)
    private String reason;
    
    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    
    public UserReport() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
    
    public UserReport(UUID reportedUserId, UUID reporterUserId, String reason, String remark) {
        this();
        this.reportedUserId = reportedUserId;
        this.reporterUserId = reporterUserId;
        this.reason = reason;
        this.remark = remark;
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
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "UserReport{" +
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

