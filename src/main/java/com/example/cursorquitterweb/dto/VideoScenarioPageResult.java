package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 视频场景分页结果
 * 包含数据和总数，用于单次查询优化
 */
public class VideoScenarioPageResult {
    private List<VideoScenarioDto> content;
    private long totalElements;
    
    public VideoScenarioPageResult() {}
    
    public VideoScenarioPageResult(List<VideoScenarioDto> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<VideoScenarioDto> getContent() {
        return content;
    }
    
    public void setContent(List<VideoScenarioDto> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

