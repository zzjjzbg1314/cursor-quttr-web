package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 康复记录实体类
 * 对应数据库表: public.recover_journey
 */
@Entity
@Table(name = "recover_journey", schema = "public")
public class RecoverJourney {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "fell_content", columnDefinition = "TEXT")
    private String fellContent;
    
    @Column(name = "create_at", nullable = false)
    private OffsetDateTime createAt;
    
    @Column(name = "update_at", nullable = false)
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
    
    @PreUpdate
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
