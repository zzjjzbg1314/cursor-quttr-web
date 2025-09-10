package com.example.cursorquitterweb.dto;

/**
 * 用户排行榜DTO
 * 只包含排行榜显示需要的字段
 */
public class UserLeaderboardDto {
    
    private String nickname;
    private String avatarUrl;
    private Integer bestRecord;
    
    public UserLeaderboardDto() {}
    
    public UserLeaderboardDto(String nickname, String avatarUrl, Integer bestRecord) {
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.bestRecord = bestRecord;
    }
    
    // Getters and Setters
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
    
    public Integer getBestRecord() {
        return bestRecord;
    }
    
    public void setBestRecord(Integer bestRecord) {
        this.bestRecord = bestRecord;
    }
}
