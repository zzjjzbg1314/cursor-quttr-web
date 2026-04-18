package com.example.cursorquitterweb.dto;

/**
 * 国内社区数据同步到海外社区的手动触发请求
 */
public class CommunityCnToGlSyncRequest {

    /**
     * 起始日期，格式：yyyy-MM-dd。为空时默认 2026-04-01。
     */
    private String startDate;

    /**
     * 是否强制执行。默认 false，用于防止误触发重复同步。
     */
    private Boolean force;

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }
}
