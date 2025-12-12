package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 视频场景分页结果（中文版）
 * 包含数据和总数，用于单次查询优化
 */
public class VideoScenarioCnPageResult {
    private List<VideoScenarioCnDto> content;
    private long totalElements;
    
    public VideoScenarioCnPageResult() {}
    
    public VideoScenarioCnPageResult(List<VideoScenarioCnDto> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<VideoScenarioCnDto> getContent() {
        return content;
    }
    
    public void setContent(List<VideoScenarioCnDto> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

