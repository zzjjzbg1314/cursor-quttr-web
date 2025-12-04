package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 帖子分页结果
 * 包含数据和总数，用于单次查询优化
 */
public class PostPageResult {
    private List<PostWithUpvotesDto> content;
    private long totalElements;
    
    public PostPageResult() {}
    
    public PostPageResult(List<PostWithUpvotesDto> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<PostWithUpvotesDto> getContent() {
        return content;
    }
    
    public void setContent(List<PostWithUpvotesDto> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

