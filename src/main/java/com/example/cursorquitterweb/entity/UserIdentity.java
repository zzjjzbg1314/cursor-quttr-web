package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户身份实体类
 * 对应数据库表: user_identities
 * 用于存储用户的多种身份认证信息（微信、手机号、邮箱等）
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class UserIdentity {
    
    private Long id;
    
    private UUID userId;
    
    private String identityType;
    
    private String identityId;
    
    /**
     * JSONB 字段，存储身份相关的额外数据
     * 在 Java 中使用 String 类型存储 JSON 字符串
     * 需要手动进行 JSON 序列化和反序列化
     */
    private String identityData;
    
    private OffsetDateTime createdAt;
    
    public UserIdentity() {
        this.createdAt = OffsetDateTime.now();
    }
    
    public UserIdentity(UUID userId, String identityType, String identityId) {
        this();
        this.userId = userId;
        this.identityType = identityType;
        this.identityId = identityId;
    }
    
    // Getters and Setters
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
        return "UserIdentity{" +
                "id=" + id +
                ", userId=" + userId +
                ", identityType='" + identityType + '\'' +
                ", identityId='" + identityId + '\'' +
                ", identityData='" + identityData + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
    
    /**
     * 身份类型常量
     */
    public static class IdentityType {
        public static final String WECHAT = "wechat";
        public static final String PHONE = "phone";
        public static final String EMAIL = "email";
        public static final String APPLE = "apple";
        public static final String QQ = "qq";
    }
}

