package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文章实体类（中文版）
 * 对应数据库表: article_cn
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class ArticleCn {
    
    private UUID articleId;
    
    private String type;
    
    private String postImg;
    
    private String color;
    
    private String title;
    
    private String content;
    
    private OffsetDateTime createAt;
    
    private String status = "active";
    
    // 关联关系（不再使用 JPA 关联，需要手动查询）
    private List<ArticleSectionCn> sections = new ArrayList<>();
    
    public ArticleCn() {
        this.createAt = OffsetDateTime.now();
        this.status = "active";
    }
    
    public ArticleCn(String type, String postImg, String color, String title) {
        this();
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
    }
    
    public ArticleCn(String type, String postImg, String color, String title, String content) {
        this();
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
        this.content = content;
    }
    
    public ArticleCn(String type, String postImg, String color, String title, String content, String status) {
        this();
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
        this.content = content;
        this.status = status;
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
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
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
    
    public List<ArticleSectionCn> getSections() {
        return sections;
    }
    
    public void setSections(List<ArticleSectionCn> sections) {
        this.sections = sections;
    }
    
    // 便捷方法
    public void addSection(ArticleSectionCn section) {
        if (sections == null) {
            sections = new ArrayList<>();
        }
        sections.add(section);
        section.setArticle(this);
    }
    
    public void removeSection(ArticleSectionCn section) {
        if (sections != null) {
            sections.remove(section);
            section.setArticle(null);
        }
    }
    
    @Override
    public String toString() {
        return "ArticleCn{" +
                "articleId=" + articleId +
                ", type='" + type + '\'' +
                ", postImg='" + postImg + '\'' +
                ", color='" + color + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", createAt=" + createAt +
                ", status='" + status + '\'' +
                ", sectionsCount=" + (sections != null ? sections.size() : 0) +
                '}';
    }
}

