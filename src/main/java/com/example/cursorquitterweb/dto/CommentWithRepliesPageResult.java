package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 评论及回复分页结果
 * 包含数据和总数，用于单次查询优化
 */
public class CommentWithRepliesPageResult {
    private List<CommentWithRepliesDTO> content;
    private long totalElements;
    
    public CommentWithRepliesPageResult() {}
    
    public CommentWithRepliesPageResult(List<CommentWithRepliesDTO> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<CommentWithRepliesDTO> getContent() {
        return content;
    }
    
    public void setContent(List<CommentWithRepliesDTO> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

