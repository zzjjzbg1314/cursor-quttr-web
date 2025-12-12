package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 视频分页结果（中文版）
 * 包含数据和总数，用于单次查询优化
 */
public class VideoCnPageResult {
    private List<VideoCnDto> content;
    private long totalElements;
    
    public VideoCnPageResult() {}
    
    public VideoCnPageResult(List<VideoCnDto> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<VideoCnDto> getContent() {
        return content;
    }
    
    public void setContent(List<VideoCnDto> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

