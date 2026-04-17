package com.example.cursorquitterweb.entity;

/**
 * 海外帖子实体类
 * 对应数据库表: posts_gl
 */
public class GlPost extends Post {
    public GlPost() {
        super();
    }

    public GlPost(java.util.UUID userId, String userNickname, String userStage, String content) {
        super(userId, userNickname, userStage, content);
    }

    public GlPost(java.util.UUID userId, String userNickname, String userStage, String avatarUrl, String content) {
        super(userId, userNickname, userStage, avatarUrl, content);
    }
}
