package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.Comment;
import java.util.ArrayList;
import java.util.List;

/**
 * 带回复的评论DTO
 * 用于返回一级评论及其所有回复
 */
public class CommentWithRepliesDTO {
    
    private Comment comment;  // 一级评论（根评论）
    private List<Comment> replies;  // 该评论的所有回复
    private long replyCount;  // 回复数量
    
    public CommentWithRepliesDTO() {
        this.replies = new ArrayList<>();
    }
    
    public CommentWithRepliesDTO(Comment comment) {
        this.comment = comment;
        this.replies = new ArrayList<>();
        this.replyCount = 0;
    }
    
    public CommentWithRepliesDTO(Comment comment, List<Comment> replies) {
        this.comment = comment;
        this.replies = replies != null ? replies : new ArrayList<>();
        this.replyCount = this.replies.size();
    }
    
    // Getters and Setters
    public Comment getComment() {
        return comment;
    }
    
    public void setComment(Comment comment) {
        this.comment = comment;
    }
    
    public List<Comment> getReplies() {
        return replies;
    }
    
    public void setReplies(List<Comment> replies) {
        this.replies = replies;
        this.replyCount = replies != null ? replies.size() : 0;
    }
    
    public void addReply(Comment reply) {
        if (this.replies == null) {
            this.replies = new ArrayList<>();
        }
        this.replies.add(reply);
        this.replyCount = this.replies.size();
    }
    
    public long getReplyCount() {
        return replyCount;
    }
    
    public void setReplyCount(long replyCount) {
        this.replyCount = replyCount;
    }
    
    @Override
    public String toString() {
        return "CommentWithRepliesDTO{" +
                "comment=" + comment +
                ", replyCount=" + replyCount +
                ", replies=" + replies +
                '}';
    }
}

