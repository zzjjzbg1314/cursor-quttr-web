package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.Comment;
import java.util.List;

/**
 * 评论分页结果
 * 包含数据和总数，用于单次查询优化
 */
public class CommentPageResult {
    private List<Comment> content;
    private long totalElements;
    
    public CommentPageResult() {}
    
    public CommentPageResult(List<Comment> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<Comment> getContent() {
        return content;
    }
    
    public void setContent(List<Comment> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

