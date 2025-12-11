package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.Article;
import java.util.List;

/**
 * 文章分页结果
 * 包含数据和总数，用于单次查询优化
 */
public class ArticlePageResult {
    private List<Article> content;
    private long totalElements;
    
    public ArticlePageResult() {}
    
    public ArticlePageResult(List<Article> content, long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }
    
    public List<Article> getContent() {
        return content;
    }
    
    public void setContent(List<Article> content) {
        this.content = content;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

