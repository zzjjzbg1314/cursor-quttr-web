package com.example.cursorquitterweb.entity;

/**
 * 海外帖子点赞实体类
 * 对应数据库表: post_likes_gl
 */
public class GlPostLike extends PostLike {
    public GlPostLike() {
        super();
    }

    public GlPostLike(java.util.UUID postId) {
        super(postId);
    }

    public GlPostLike(java.util.UUID postId, Integer likeCount) {
        super(postId, likeCount);
    }
}
