package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 用户复发历史响应DTO
 */
public class UserRelapseHistoryResponse {

    private Long relapseCount;
    private Integer bestRecord;
    private Long rank;
    private Long totalUsers;
    private List<RelapseHistoryItem> relapseHistory;

    public UserRelapseHistoryResponse() {}

    public UserRelapseHistoryResponse(Long relapseCount, Integer bestRecord, Long rank, Long totalUsers,
                                      List<RelapseHistoryItem> relapseHistory) {
        this.relapseCount = relapseCount;
        this.bestRecord = bestRecord;
        this.rank = rank;
        this.totalUsers = totalUsers;
        this.relapseHistory = relapseHistory;
    }

    public Long getRelapseCount() {
        return relapseCount;
    }

    public void setRelapseCount(Long relapseCount) {
        this.relapseCount = relapseCount;
    }

    public Integer getBestRecord() {
        return bestRecord;
    }

    public void setBestRecord(Integer bestRecord) {
        this.bestRecord = bestRecord;
    }

    public Long getRank() {
        return rank;
    }

    public void setRank(Long rank) {
        this.rank = rank;
    }

    public Long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(Long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public List<RelapseHistoryItem> getRelapseHistory() {
        return relapseHistory;
    }

    public void setRelapseHistory(List<RelapseHistoryItem> relapseHistory) {
        this.relapseHistory = relapseHistory;
    }

    /**
     * 单条复发历史
     */
    public static class RelapseHistoryItem {
        private String challengeStartDate;
        private String relapseDate;
        private Integer durationDays;

        public RelapseHistoryItem() {}

        public RelapseHistoryItem(String challengeStartDate, String relapseDate, Integer durationDays) {
            this.challengeStartDate = challengeStartDate;
            this.relapseDate = relapseDate;
            this.durationDays = durationDays;
        }

        public String getChallengeStartDate() {
            return challengeStartDate;
        }

        public void setChallengeStartDate(String challengeStartDate) {
            this.challengeStartDate = challengeStartDate;
        }

        public String getRelapseDate() {
            return relapseDate;
        }

        public void setRelapseDate(String relapseDate) {
            this.relapseDate = relapseDate;
        }

        public Integer getDurationDays() {
            return durationDays;
        }

        public void setDurationDays(Integer durationDays) {
            this.durationDays = durationDays;
        }
    }
}
