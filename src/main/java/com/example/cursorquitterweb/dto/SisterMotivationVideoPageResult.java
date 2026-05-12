package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 姐姐激励视频分页结果
 */
public class SisterMotivationVideoPageResult {

    private List<SisterMotivationVideoDto> content;
    private long totalElements;

    public SisterMotivationVideoPageResult() {}

    public SisterMotivationVideoPageResult(List<SisterMotivationVideoDto> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }

    public List<SisterMotivationVideoDto> getContent() {
        return content;
    }

    public void setContent(List<SisterMotivationVideoDto> content) {
        this.content = content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}
