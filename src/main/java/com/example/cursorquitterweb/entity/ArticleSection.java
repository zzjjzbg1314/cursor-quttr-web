package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 文章章节实体类
 * 对应数据库表: public.article_section
 */
@Entity
@Table(name = "article_section", schema = "public")
public class ArticleSection {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    
    @Column(name = "title")
    private String title;
    
    @Column(name = "section_content", columnDefinition = "TEXT")
    private String sectionContent;
    
    @Column(name = "section_index")
    private Integer sectionIndex = 0;
    
    @Column(name = "createAt")
    private OffsetDateTime createAt;
    
    @Column(name = "updateAt")
    private OffsetDateTime updateAt;
    
    // 关联关系
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", insertable = false, updatable = false)
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
    
    @PreUpdate
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
