package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 用户举报分页结果
 * 包含数据和总数，用于单次查询优化
 */
public class UserReportPageResult {
    private List<UserReportDto> content;
    private long totalElements;
    
    public UserReportPageResult() {}
    
    public UserReportPageResult(List<UserReportDto> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<UserReportDto> getContent() {
        return content;
    }
    
    public void setContent(List<UserReportDto> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

