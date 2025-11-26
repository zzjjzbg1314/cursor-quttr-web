package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 康复记录实体类
 * 对应数据库表: recover_journey
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class RecoverJourney {
    
    private UUID id;
    
    private UUID userId;
    
    private String fellContent;
    
    private OffsetDateTime createAt;
    
    private OffsetDateTime updateAt;
    
    public RecoverJourney() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }
    
    public RecoverJourney(UUID userId) {
        this();
        this.userId = userId;
    }
    
    public RecoverJourney(UUID userId, String fellContent) {
        this();
        this.userId = userId;
        this.fellContent = fellContent;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getFellContent() {
        return fellContent;
    }
    
    public void setFellContent(String fellContent) {
        this.fellContent = fellContent;
    }
    
    public OffsetDateTime getCreateAt() {
        return createAt;
    }
    
    public void setCreateAt(OffsetDateTime createAt) {
        this.createAt = createAt;
    }
    
    public OffsetDateTime getUpdateAt() {
        return updateAt;
    }
    
    public void setUpdateAt(OffsetDateTime updateAt) {
        this.updateAt = updateAt;
    }
    
    /**
     * 更新前调用，设置更新时间
     */
    public void preUpdate() {
        this.updateAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "RecoverJourney{" +
                "id=" + id +
                ", userId=" + userId +
                ", fellContent='" + fellContent + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
