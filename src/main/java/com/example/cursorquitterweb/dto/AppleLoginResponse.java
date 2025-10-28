package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Apple 登录响应DTO
 * 包含完整的 User 对象信息
 */
public class AppleLoginResponse {
    
    // User 对象的所有字段
    private UUID id;
    private String nickname;
    private String avatarUrl;
    private Short gender;
    private String language;
    private String phoneNumber;
    private OffsetDateTime registrationTime;
    private OffsetDateTime challengeResetTime;
    private Integer bestRecord;
    private String quitReason;
    private Integer age;
    private Integer restartCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    
    // 额外的登录信息
    private Boolean isNewUser;
    
    public AppleLoginResponse() {
    }
    
    /**
     * 从 User 对象构建响应
     */
    public AppleLoginResponse(User user, Boolean isNewUser) {
        this.id = user.getId();
        this.nickname = user.getNickname();
        this.avatarUrl = user.getAvatarUrl();
        this.gender = user.getGender();
        this.language = user.getLanguage();
        this.phoneNumber = user.getPhoneNumber();
        this.registrationTime = user.getRegistrationTime();
        this.challengeResetTime = user.getChallengeResetTime();
        this.bestRecord = user.getBestRecord();
        this.quitReason = user.getQuitReason();
        this.age = user.getAge();
        this.restartCount = user.getRestartCount();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
        this.isNewUser = isNewUser;
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
    
    public String getQuitReason() {
        return quitReason;
    }
    
    public void setQuitReason(String quitReason) {
        this.quitReason = quitReason;
    }
    
    public Integer getAge() {
        return age;
    }
    
    public void setAge(Integer age) {
        this.age = age;
    }
    
    public Integer getRestartCount() {
        return restartCount;
    }
    
    public void setRestartCount(Integer restartCount) {
        this.restartCount = restartCount;
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
    
    public Boolean getIsNewUser() {
        return isNewUser;
    }
    
    public void setIsNewUser(Boolean isNewUser) {
        this.isNewUser = isNewUser;
    }
    
    @Override
    public String toString() {
        return "AppleLoginResponse{" +
                "id=" + id +
                ", nickname='" + nickname + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", gender=" + gender +
                ", language='" + language + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", registrationTime=" + registrationTime +
                ", challengeResetTime=" + challengeResetTime +
                ", bestRecord=" + bestRecord +
                ", quitReason='" + quitReason + '\'' +
                ", age=" + age +
                ", restartCount=" + restartCount +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", isNewUser=" + isNewUser +
                '}';
    }
}

