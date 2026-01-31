package com.example.cursorquitterweb.dto;

/**
 * 戒色天数排行榜条目
 */
public class UserChallengeRankDto {

    private int rank;
    private String name;
    private long days;
    private String avatarUrl;

    public UserChallengeRankDto() {
    }

    public UserChallengeRankDto(String name, String avatarUrl, long days) {
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.days = days;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getDays() {
        return days;
    }

    public void setDays(long days) {
        this.days = days;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
