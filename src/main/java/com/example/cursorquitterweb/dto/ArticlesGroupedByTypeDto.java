package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.Article;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 按type分组的文章DTO
 */
public class ArticlesGroupedByTypeDto {
    
    private Map<String, List<ArticleSummaryDto>> articlesByType;
    private long totalArticles;
    private int totalTypes;
    
    public ArticlesGroupedByTypeDto() {}
    
    public ArticlesGroupedByTypeDto(Map<String, List<Article>> articlesByType) {
        this.articlesByType = articlesByType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(ArticleSummaryDto::new)
                                .collect(Collectors.toList())
                ));
        this.totalArticles = articlesByType.values().stream()
                .mapToLong(List::size)
                .sum();
        this.totalTypes = articlesByType.size();
    }
    
    // Getters and Setters
    public Map<String, List<ArticleSummaryDto>> getArticlesByType() {
        return articlesByType;
    }
    
    public void setArticlesByType(Map<String, List<ArticleSummaryDto>> articlesByType) {
        this.articlesByType = articlesByType;
    }
    
    public long getTotalArticles() {
        return totalArticles;
    }
    
    public void setTotalArticles(long totalArticles) {
        this.totalArticles = totalArticles;
    }
    
    public int getTotalTypes() {
        return totalTypes;
    }
    
    public void setTotalTypes(int totalTypes) {
        this.totalTypes = totalTypes;
    }
    
    /**
     * 文章摘要DTO，避免无限递归
     */
    public static class ArticleSummaryDto {
        private UUID articleId;
        private String type;
        private String postImg;
        private String color;
        private String title;
        private OffsetDateTime createAt;
        private String status;
        
        public ArticleSummaryDto() {}
        
        public ArticleSummaryDto(Article article) {
            this.articleId = article.getArticleId();
            this.type = article.getType();
            this.postImg = article.getPostImg();
            this.color = article.getColor();
            this.title = article.getTitle();
            this.createAt = article.getCreateAt();
            this.status = article.getStatus();
        }
        
        // Getters and Setters
        public UUID getArticleId() {
            return articleId;
        }
        
        public void setArticleId(UUID articleId) {
            this.articleId = articleId;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getPostImg() {
            return postImg;
        }
        
        public void setPostImg(String postImg) {
            this.postImg = postImg;
        }
        
        public String getColor() {
            return color;
        }
        
        public void setColor(String color) {
            this.color = color;
        }
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public OffsetDateTime getCreateAt() {
            return createAt;
        }
        
        public void setCreateAt(OffsetDateTime createAt) {
            this.createAt = createAt;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
    }
}
