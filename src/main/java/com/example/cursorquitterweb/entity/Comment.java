package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评论实体类
 * 对应数据库表: public.comments
 */
@Entity
@Table(name = "comments", schema = "public")
public class Comment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "comment_id", columnDefinition = "UUID")
    private UUID commentId;
    
    @Column(name = "post_id", nullable = false, columnDefinition = "UUID")
    private UUID postId;
    
    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;
    
    @Column(name = "user_nickname", nullable = false, length = 100)
    private String userNickname;
    
    @Column(name = "user_stage", nullable = false, length = 50)
    private String userStage;
    
    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;
    
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    
    // 新增字段支持回复功能
    @Column(name = "parent_comment_id", columnDefinition = "UUID")
    private UUID parentCommentId;  // 父评论ID，NULL表示直接评论帖子
    
    @Column(name = "reply_to_user_id", columnDefinition = "UUID")
    private UUID replyToUserId;  // 被回复的用户ID
    
    @Column(name = "reply_to_user_nickname", length = 100)
    private String replyToUserNickname;  // 被回复的用户昵称
    
    @Column(name = "reply_to_comment_id", columnDefinition = "UUID")
    private UUID replyToCommentId;  // 被回复的评论ID
    
    @Column(name = "comment_level", nullable = false)
    private Short commentLevel = 1;  // 评论层级：1=直接评论，2=回复评论
    
    @Column(name = "root_comment_id", columnDefinition = "UUID")
    private UUID rootCommentId;  // 根评论ID（该回复链最顶层的评论）
    
    public Comment() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        this.isDeleted = false;
        this.commentLevel = 1;
    }
    
    public Comment(UUID postId, UUID userId, String userNickname, String userStage, String content) {
        this();
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.content = content;
    }
    
    public Comment(UUID postId, UUID userId, String userNickname, String userStage, String avatarUrl, String content) {
        this();
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.avatarUrl = avatarUrl;
        this.content = content;
    }
    
    // 新增构造函数：创建回复评论
    public Comment(UUID postId, UUID userId, String userNickname, String userStage, String avatarUrl, 
                   String content, UUID parentCommentId, UUID replyToUserId, String replyToUserNickname, 
                   UUID replyToCommentId, UUID rootCommentId) {
        this();
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.userStage = userStage;
        this.avatarUrl = avatarUrl;
        this.content = content;
        this.parentCommentId = parentCommentId;
        this.replyToUserId = replyToUserId;
        this.replyToUserNickname = replyToUserNickname;
        this.replyToCommentId = replyToCommentId;
        this.rootCommentId = rootCommentId;
        this.commentLevel = 2;  // 回复评论的层级为2
    }
    
    // Getters and Setters
    public UUID getCommentId() {
        return commentId;
    }
    
    public void setCommentId(UUID commentId) {
        this.commentId = commentId;
    }
    
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
    
    public UUID getParentCommentId() {
        return parentCommentId;
    }
    
    public void setParentCommentId(UUID parentCommentId) {
        this.parentCommentId = parentCommentId;
    }
    
    public UUID getReplyToUserId() {
        return replyToUserId;
    }
    
    public void setReplyToUserId(UUID replyToUserId) {
        this.replyToUserId = replyToUserId;
    }
    
    public String getReplyToUserNickname() {
        return replyToUserNickname;
    }
    
    public void setReplyToUserNickname(String replyToUserNickname) {
        this.replyToUserNickname = replyToUserNickname;
    }
    
    public UUID getReplyToCommentId() {
        return replyToCommentId;
    }
    
    public void setReplyToCommentId(UUID replyToCommentId) {
        this.replyToCommentId = replyToCommentId;
    }
    
    public Short getCommentLevel() {
        return commentLevel;
    }
    
    public void setCommentLevel(Short commentLevel) {
        this.commentLevel = commentLevel;
    }
    
    public UUID getRootCommentId() {
        return rootCommentId;
    }
    
    public void setRootCommentId(UUID rootCommentId) {
        this.rootCommentId = rootCommentId;
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "Comment{" +
                "commentId=" + commentId +
                ", postId=" + postId +
                ", userId=" + userId +
                ", userNickname='" + userNickname + '\'' +
                ", userStage='" + userStage + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", content='" + content + '\'' +
                ", isDeleted=" + isDeleted +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", parentCommentId=" + parentCommentId +
                ", replyToUserId=" + replyToUserId +
                ", replyToUserNickname='" + replyToUserNickname + '\'' +
                ", replyToCommentId=" + replyToCommentId +
                ", commentLevel=" + commentLevel +
                ", rootCommentId=" + rootCommentId +
                '}';
    }
}
