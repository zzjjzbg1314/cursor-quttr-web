package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 文章章节实体类
 * 对应数据库表: article_section
 * 已移除 JPA 注解，现在作为普通 POJO 使用
 */
public class ArticleSection {
    
    private UUID id;
    
    private UUID articleId;
    
    private String title;
    
    private String sectionContent;
    
    private Integer sectionIndex = 0;
    
    private OffsetDateTime createAt;
    
    private OffsetDateTime updateAt;
    
    // 关联关系（不再使用 JPA 关联，需要手动查询）
    private Article article;
    
    public ArticleSection() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
        this.sectionIndex = 0;
    }
    
    public ArticleSection(UUID articleId, String title, String sectionContent, Integer sectionIndex) {
        this();
        this.articleId = articleId;
        this.title = title;
        this.sectionContent = sectionContent;
        this.sectionIndex = sectionIndex;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getArticleId() {
        return articleId;
    }
    
    public void setArticleId(UUID articleId) {
        this.articleId = articleId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getSectionContent() {
        return sectionContent;
    }
    
    public void setSectionContent(String sectionContent) {
        this.sectionContent = sectionContent;
    }
    
    public Integer getSectionIndex() {
        return sectionIndex;
    }
    
    public void setSectionIndex(Integer sectionIndex) {
        this.sectionIndex = sectionIndex;
    }
    
    public OffsetDateTime getCreateAt() {
        return createAt;
    }
    
    public void setCreateAt(OffsetDateTime createAt) {
        this.createAt = createAt;
    }
    
    public OffsetDateTime getUpdateAt() {
        return updateAt;
    }
    
    public void setUpdateAt(OffsetDateTime updateAt) {
        this.updateAt = updateAt;
    }
    
    public Article getArticle() {
        return article;
    }
    
    public void setArticle(Article article) {
        this.article = article;
    }
    
    /**
     * 更新前调用，设置更新时间
     */
    public void preUpdate() {
        this.updateAt = OffsetDateTime.now();
    }
    
    @Override
    public String toString() {
        return "ArticleSection{" +
                "id=" + id +
                ", articleId=" + articleId +
                ", title='" + title + '\'' +
                ", sectionContent='" + sectionContent + '\'' +
                ", sectionIndex=" + sectionIndex +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
