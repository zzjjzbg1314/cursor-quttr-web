package com.example.cursorquitterweb.dto;

/**
 * 用户排行榜DTO
 * 只包含排行榜显示需要的字段
 */
public class UserLeaderboardDto {
    
    private String nickname;
    private Integer bestRecord;
    
    public UserLeaderboardDto() {}
    
    public UserLeaderboardDto(String nickname, Integer bestRecord) {
        this.nickname = nickname;
        this.bestRecord = bestRecord;
    }
    
    // Getters and Setters
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public Integer getBestRecord() {
        return bestRecord;
    }
    
    public void setBestRecord(Integer bestRecord) {
        this.bestRecord = bestRecord;
    }
}
