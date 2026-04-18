package com.example.cursorquitterweb.dto;

import java.time.OffsetDateTime;

/**
 * 国内社区数据同步到海外社区的结果
 */
public class CommunityCnToGlSyncResult {

    private String startDate;
    private Boolean force;
    private Integer sourcePostCount;
    private Integer sourceCommentCount;
    private Integer sourceLikeCount;
    private Integer syncedPostCount;
    private Integer syncedCommentCount;
    private Integer syncedLikeCount;
    private Integer translatedPostCount;
    private Integer translatedCommentCount;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private Long durationMs;

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

    public Integer getSourcePostCount() {
        return sourcePostCount;
    }

    public void setSourcePostCount(Integer sourcePostCount) {
        this.sourcePostCount = sourcePostCount;
    }

    public Integer getSourceCommentCount() {
        return sourceCommentCount;
    }

    public void setSourceCommentCount(Integer sourceCommentCount) {
        this.sourceCommentCount = sourceCommentCount;
    }

    public Integer getSourceLikeCount() {
        return sourceLikeCount;
    }

    public void setSourceLikeCount(Integer sourceLikeCount) {
        this.sourceLikeCount = sourceLikeCount;
    }

    public Integer getSyncedPostCount() {
        return syncedPostCount;
    }

    public void setSyncedPostCount(Integer syncedPostCount) {
        this.syncedPostCount = syncedPostCount;
    }

    public Integer getSyncedCommentCount() {
        return syncedCommentCount;
    }

    public void setSyncedCommentCount(Integer syncedCommentCount) {
        this.syncedCommentCount = syncedCommentCount;
    }

    public Integer getSyncedLikeCount() {
        return syncedLikeCount;
    }

    public void setSyncedLikeCount(Integer syncedLikeCount) {
        this.syncedLikeCount = syncedLikeCount;
    }

    public Integer getTranslatedPostCount() {
        return translatedPostCount;
    }

    public void setTranslatedPostCount(Integer translatedPostCount) {
        this.translatedPostCount = translatedPostCount;
    }

    public Integer getTranslatedCommentCount() {
        return translatedCommentCount;
    }

    public void setTranslatedCommentCount(Integer translatedCommentCount) {
        this.translatedCommentCount = translatedCommentCount;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}
