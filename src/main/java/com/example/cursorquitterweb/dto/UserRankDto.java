package com.example.cursorquitterweb.dto;

/**
 * 用户排名DTO
 * 包含用户在排行榜中的排名和最佳成绩信息
 */
public class UserRankDto {
    
    private Long rank;
    private Integer bestRecord;
    
    public UserRankDto() {}
    
    public UserRankDto(Long rank, Integer bestRecord) {
        this.rank = rank;
        this.bestRecord = bestRecord;
    }
    
    // Getters and Setters
    public Long getRank() {
        return rank;
    }
    
    public void setRank(Long rank) {
        this.rank = rank;
    }
    
    public Integer getBestRecord() {
        return bestRecord;
    }
    
    public void setBestRecord(Integer bestRecord) {
        this.bestRecord = bestRecord;
    }
}
