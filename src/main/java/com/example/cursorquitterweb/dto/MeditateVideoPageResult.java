package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 冥想视频分页结果
 * 包含数据和总数，用于单次查询优化
 */
public class MeditateVideoPageResult {
    private List<MeditateVideoDto> content;
    private long totalElements;
    
    public MeditateVideoPageResult() {}
    
    public MeditateVideoPageResult(List<MeditateVideoDto> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<MeditateVideoDto> getContent() {
        return content;
    }
    
    public void setContent(List<MeditateVideoDto> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

