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
    
    @Column(name = "wechat_openid", nullable = false, unique = true)
    private String wechatOpenid;
    
    @Column(name = "wechat_unionid")
    private String wechatUnionid;
    
    @Column(name = "nickname")
    private String nickname;
    
    @Column(name = "avatar_url")
    private String avatarUrl;
    
    @Column(name = "gender")
    private Short gender;
    
    @Column(name = "country")
    private String country;
    
    @Column(name = "province")
    private String province;
    
    @Column(name = "city")
    private String city;
    
    @Column(name = "language")
    private String language = "zh_CN";
    
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
    
    public User(String wechatOpenid) {
        this();
        this.wechatOpenid = wechatOpenid;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getWechatOpenid() {
        return wechatOpenid;
    }
    
    public void setWechatOpenid(String wechatOpenid) {
        this.wechatOpenid = wechatOpenid;
    }
    
    public String getWechatUnionid() {
        return wechatUnionid;
    }
    
    public void setWechatUnionid(String wechatUnionid) {
        this.wechatUnionid = wechatUnionid;
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
    
    public String getCountry() {
        return country;
    }
    
    public void setCountry(String country) {
        this.country = country;
    }
    
    public String getProvince() {
        return province;
    }
    
    public void setProvince(String province) {
        this.province = province;
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
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
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", wechatOpenid='" + wechatOpenid + '\'' +
                ", wechatUnionid='" + wechatUnionid + '\'' +
                ", nickname='" + nickname + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", gender=" + gender +
                ", country='" + country + '\'' +
                ", province='" + province + '\'' +
                ", city='" + city + '\'' +
                ", language='" + language + '\'' +
                ", registrationTime=" + registrationTime +
                ", challengeResetTime=" + challengeResetTime +
                ", bestRecord=" + bestRecord +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
