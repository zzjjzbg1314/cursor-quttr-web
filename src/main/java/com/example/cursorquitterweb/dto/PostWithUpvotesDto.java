package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.Post;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 包含点赞数的帖子DTO
 */
public class PostWithUpvotesDto {
    
    private UUID postId;
    private UUID userId;
    private String userNickname;
    private String userStage;
    
    private String avatarUrl;
    
    private String content;
    private String originalLanguage;
    private String contentZh;
    private String contentEn;
    private String contentJa;
    private String contentKo;
    private String contentDe;
    private String contentFr;
    private String contentPt;
    private String contentEs;
    private String translationStatus;
    private OffsetDateTime translatedAt;
    private String emojiCountry;
    private Boolean isDeleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer upvotes; // 点赞数
    private Integer commentCount; // 评论数
    
    public PostWithUpvotesDto() {}
    
    public PostWithUpvotesDto(UUID postId, UUID userId, String userNickname, String userStage, 
                             String content, Boolean isDeleted, 
                             OffsetDateTime createdAt, OffsetDateTime updatedAt, Integer upvotes, Integer commentCount) {
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.content = content;
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.upvotes = upvotes;
        this.commentCount = commentCount;
    }
    
    public PostWithUpvotesDto(UUID postId, UUID userId, String userNickname, String userStage, String avatarUrl,
                             String content, Boolean isDeleted, 
                             OffsetDateTime createdAt, OffsetDateTime updatedAt, Integer upvotes, Integer commentCount) {
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.avatarUrl = avatarUrl;
        this.content = content;
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.upvotes = upvotes;
        this.commentCount = commentCount;
    }
    
    // Getters and Setters
    public UUID getPostId() {
        return postId;
    }
    
    public void setPostId(UUID postId) {
        this.postId = postId;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getUserNickname() {
        return userNickname;
    }
    
    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }
    
    public String getUserStage() {
        return userStage;
    }
    
    public void setUserStage(String userStage) {
        this.userStage = userStage;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }

    public String getOriginalLanguage() {
        return originalLanguage;
    }

    public void setOriginalLanguage(String originalLanguage) {
        this.originalLanguage = originalLanguage;
    }

    public String getContentZh() {
        return contentZh;
    }

    public void setContentZh(String contentZh) {
        this.contentZh = contentZh;
    }

    public String getContentEn() {
        return contentEn;
    }

    public void setContentEn(String contentEn) {
        this.contentEn = contentEn;
    }

    public String getContentJa() {
        return contentJa;
    }

    public void setContentJa(String contentJa) {
        this.contentJa = contentJa;
    }

    public String getContentKo() {
        return contentKo;
    }

    public void setContentKo(String contentKo) {
        this.contentKo = contentKo;
    }

    public String getContentDe() {
        return contentDe;
    }

    public void setContentDe(String contentDe) {
        this.contentDe = contentDe;
    }

    public String getContentFr() {
        return contentFr;
    }

    public void setContentFr(String contentFr) {
        this.contentFr = contentFr;
    }

    public String getContentPt() {
        return contentPt;
    }

    public void setContentPt(String contentPt) {
        this.contentPt = contentPt;
    }

    public String getContentEs() {
        return contentEs;
    }

    public void setContentEs(String contentEs) {
        this.contentEs = contentEs;
    }

    public String getTranslationStatus() {
        return translationStatus;
    }

    public void setTranslationStatus(String translationStatus) {
        this.translationStatus = translationStatus;
    }

    public OffsetDateTime getTranslatedAt() {
        return translatedAt;
    }

    public void setTranslatedAt(OffsetDateTime translatedAt) {
        this.translatedAt = translatedAt;
    }

    public String getEmojiCountry() {
        return emojiCountry;
    }

    public void setEmojiCountry(String emojiCountry) {
        this.emojiCountry = emojiCountry;
    }
    
    public Boolean getIsDeleted() {
        return isDeleted;
    }
    
    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
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
    
    public Integer getUpvotes() {
        return upvotes;
    }
    
    public void setUpvotes(Integer upvotes) {
        this.upvotes = upvotes;
    }
    
    public Integer getCommentCount() {
        return commentCount;
    }
    
    public void setCommentCount(Integer commentCount) {
        this.commentCount = commentCount;
    }

    public PostWithUpvotesDto withTranslationsFrom(Post post) {
        if (post == null) {
            return this;
        }
        this.originalLanguage = post.getOriginalLanguage();
        this.contentZh = post.getContentZh();
        this.contentEn = post.getContentEn();
        this.contentJa = post.getContentJa();
        this.contentKo = post.getContentKo();
        this.contentDe = post.getContentDe();
        this.contentFr = post.getContentFr();
        this.contentPt = post.getContentPt();
        this.contentEs = post.getContentEs();
        this.translationStatus = post.getTranslationStatus();
        this.translatedAt = post.getTranslatedAt();
        this.emojiCountry = post.getEmojiCountry();
        return this;
    }
    
    @Override
    public String toString() {
        return "PostWithUpvotesDto{" +
                "postId=" + postId +
                ", userId=" + userId +
                ", userNickname='" + userNickname + '\'' +
                ", userStage='" + userStage + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", content='" + content + '\'' +
                ", originalLanguage='" + originalLanguage + '\'' +
                ", translationStatus='" + translationStatus + '\'' +
                ", translatedAt=" + translatedAt +
                ", emojiCountry='" + emojiCountry + '\'' +
                ", isDeleted=" + isDeleted +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", upvotes=" + upvotes +
                ", commentCount=" + commentCount +
                '}';
    }
}
