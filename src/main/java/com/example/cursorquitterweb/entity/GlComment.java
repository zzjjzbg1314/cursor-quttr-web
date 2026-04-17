package com.example.cursorquitterweb.entity;

/**
 * 海外评论实体类
 * 对应数据库表: comments_gl
 */
public class GlComment extends Comment {
    public GlComment() {
        super();
    }

    public GlComment(java.util.UUID postId, java.util.UUID userId, String userNickname, String userStage, String content) {
        super(postId, userId, userNickname, userStage, content);
    }

    public GlComment(java.util.UUID postId, java.util.UUID userId, String userNickname, String userStage, String avatarUrl, String content) {
        super(postId, userId, userNickname, userStage, avatarUrl, content);
    }

    public GlComment(java.util.UUID postId, java.util.UUID userId, String userNickname, String userStage, String avatarUrl,
                     String content, java.util.UUID parentCommentId, java.util.UUID replyToUserId, String replyToUserNickname,
                     java.util.UUID replyToCommentId, java.util.UUID rootCommentId) {
        super(postId, userId, userNickname, userStage, avatarUrl, content, parentCommentId, replyToUserId, replyToUserNickname, replyToCommentId, rootCommentId);
    }
}
