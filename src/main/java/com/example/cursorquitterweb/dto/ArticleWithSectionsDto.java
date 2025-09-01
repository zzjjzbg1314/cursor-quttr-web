package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.Article;
import com.example.cursorquitterweb.entity.ArticleSection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 包含章节的文章DTO
 */
public class ArticleWithSectionsDto {
    
    private UUID articleId;
    private String type;
    private String postImg;
    private String color;
    private String title;
    private OffsetDateTime createAt;
    private String status;
    private List<ArticleSection> sections;
    
    public ArticleWithSectionsDto() {}
    
    public ArticleWithSectionsDto(Article article, List<ArticleSection> sections) {
        this.articleId = article.getArticleId();
        this.type = article.getType();
        this.postImg = article.getPostImg();
        this.color = article.getColor();
        this.title = article.getTitle();
        this.createAt = article.getCreateAt();
        this.status = article.getStatus();
        this.sections = sections;
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
    
    public List<ArticleSection> getSections() {
        return sections;
    }
    
    public void setSections(List<ArticleSection> sections) {
        this.sections = sections;
    }
    
    @Override
    public String toString() {
        return "ArticleWithSectionsDto{" +
                "articleId=" + articleId +
                ", type='" + type + '\'' +
                ", postImg='" + postImg + '\'' +
                ", color='" + color + '\'' +
                ", title='" + title + '\'' +
                ", createAt=" + createAt +
                ", status='" + status + '\'' +
                ", sectionsCount=" + (sections != null ? sections.size() : 0) +
                '}';
    }
}
