package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 冥想视频分页结果（中文版）
 * 包含数据和总数，用于单次查询优化
 */
public class MeditateVideoCnPageResult {
    private List<MeditateVideoCnDto> content;
    private long totalElements;
    
    public MeditateVideoCnPageResult() {}
    
    public MeditateVideoCnPageResult(List<MeditateVideoCnDto> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<MeditateVideoCnDto> getContent() {
        return content;
    }
    
    public void setContent(List<MeditateVideoCnDto> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

