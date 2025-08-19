package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户实体类
 * 对应数据库表: public.users
 */
@Entity
@Table(name = "users", schema = "public")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "nickname")
    private String nickname;
    
    @Column(name = "avatar_url")
    private String avatarUrl;
    
    @Column(name = "gender")
    private Short gender;
    
    @Column(name = "language")
    private String language = "zh_CN";
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "registration_time", nullable = false)
    private OffsetDateTime registrationTime;
    
    @Column(name = "challenge_reset_time", nullable = false)
    private OffsetDateTime challengeResetTime;
    
    @Column(name = "best_record", nullable = false)
    private Integer bestRecord = 1;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    
    public User() {
        this.registrationTime = OffsetDateTime.now();
        this.challengeResetTime = OffsetDateTime.now();
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
    
    public User(String nickname) {
        this();
        this.nickname = nickname;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    public Short getGender() {
        return gender;
    }
    
    public void setGender(Short gender) {
        this.gender = gender;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public OffsetDateTime getRegistrationTime() {
        return registrationTime;
    }
    
    public void setRegistrationTime(OffsetDateTime registrationTime) {
        this.registrationTime = registrationTime;
    }
    
    public OffsetDateTime getChallengeResetTime() {
        return challengeResetTime;
    }
    
    public void setChallengeResetTime(OffsetDateTime challengeResetTime) {
        this.challengeResetTime = challengeResetTime;
    }
    
    public Integer getBestRecord() {
        return bestRecord;
    }
    
    public void setBestRecord(Integer bestRecord) {
        this.bestRecord = bestRecord;
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
    
    /**
     * 初始化一个包含所有基础字段信息的用户
     * @return 初始化后的用户对象
     */
    public static User initUser() {
        User user = new User();
        user.setNickname("新用户");
        user.setAvatarUrl("https://example.com/default-avatar.jpg");
        user.setGender((short) 0); // 0: 未知, 1: 男, 2: 女
        user.setLanguage("zh_CN");
        user.setPhoneNumber(null); // 手机号字段不初始化，允许为空
        user.setBestRecord(1);
        user.setChallengeResetTime(OffsetDateTime.now());
        user.setRegistrationTime(OffsetDateTime.now());
        
        return user;
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", nickname='" + nickname + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", gender=" + gender +
                ", language='" + language + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", registrationTime=" + registrationTime +
                ", challengeResetTime=" + challengeResetTime +
                ", bestRecord=" + bestRecord +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
