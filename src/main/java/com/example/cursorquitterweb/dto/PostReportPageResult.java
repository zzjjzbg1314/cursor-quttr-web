package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 帖子举报分页结果
 * 包含数据和总数，用于单次查询优化
 */
public class PostReportPageResult {
    private List<PostReportDto> content;
    private long totalElements;
    
    public PostReportPageResult() {}
    
    public PostReportPageResult(List<PostReportDto> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<PostReportDto> getContent() {
        return content;
    }
    
    public void setContent(List<PostReportDto> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

