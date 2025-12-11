package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 视频分页结果
 * 包含数据和总数，用于单次查询优化
 */
public class VideoPageResult {
    private List<VideoDto> content;
    private long totalElements;
    
    public VideoPageResult() {}
    
    public VideoPageResult(List<VideoDto> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<VideoDto> getContent() {
        return content;
    }
    
    public void setContent(List<VideoDto> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

