package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.ArticleCn;
import java.util.List;

/**
 * 文章分页结果（中文版）
 * 包含数据和总数，用于单次查询优化
 */
public class ArticleCnPageResult {
    private List<ArticleCn> content;
    private long totalElements;
    
    public ArticleCnPageResult() {}
    
    public ArticleCnPageResult(List<ArticleCn> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<ArticleCn> getContent() {
        return content;
    }
    
    public void setContent(List<ArticleCn> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

