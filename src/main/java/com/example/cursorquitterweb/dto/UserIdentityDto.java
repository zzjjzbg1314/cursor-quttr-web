package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.UserIdentity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户身份响应DTO
 */
public class UserIdentityDto {
    
    private Long id;
    private UUID userId;
    private String identityType;
    private String identityId;
    private String identityData;
    private OffsetDateTime createdAt;
    
    public UserIdentityDto() {
    }
    
    public UserIdentityDto(Long id, UUID userId, String identityType, String identityId, 
                          String identityData, OffsetDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.identityType = identityType;
        this.identityId = identityId;
        this.identityData = identityData;
        this.createdAt = createdAt;
    }
    
    /**
     * 从实体转换为DTO
     */
    public static UserIdentityDto fromEntity(UserIdentity entity) {
        if (entity == null) {
            return null;
        }
        return new UserIdentityDto(
            entity.getId(),
            entity.getUserId(),
            entity.getIdentityType(),
            entity.getIdentityId(),
            entity.getIdentityData(),
            entity.getCreatedAt()
        );
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getIdentityType() {
        return identityType;
    }
    
    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }
    
    public String getIdentityId() {
        return identityId;
    }
    
    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }
    
    public String getIdentityData() {
        return identityData;
    }
    
    public void setIdentityData(String identityData) {
        this.identityData = identityData;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "UserIdentityDto{" +
                "id=" + id +
                ", userId=" + userId +
                ", identityType='" + identityType + '\'' +
                ", identityId='" + identityId + '\'' +
                ", identityData='" + identityData + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

