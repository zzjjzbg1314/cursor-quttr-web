package com.example.cursorquitterweb.entity;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文章实体类
 * 对应数据库表: public.article
 */
@Entity
@Table(name = "article", schema = "public")
public class Article {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "article_id")
    private UUID articleId;
    
    @Column(name = "type")
    private String type;
    
    @Column(name = "post_img")
    private String postImg;
    
    @Column(name = "color")
    private String color;
    
    @Column(name = "title", columnDefinition = "TEXT")
    private String title;
    
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "createAt")
    private OffsetDateTime createAt;
    
    @Column(name = "status")
    private String status = "active";
    
    // 关联关系
    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sectionIndex ASC")
    private List<ArticleSection> sections = new ArrayList<>();
    
    public Article() {
        this.createAt = OffsetDateTime.now();
        this.status = "active";
    }
    
    public Article(String type, String postImg, String color, String title) {
        this();
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
    }
    
    public Article(String type, String postImg, String color, String title, String content) {
        this();
        this.type = type;
        this.postImg = postImg;
        this.color = color;
        this.title = title;
        this.content = content;
    }
    
    public Article(String type, String postImg, String color, String title, String content, String status) {
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
    
    public List<ArticleSection> getSections() {
        return sections;
    }
    
    public void setSections(List<ArticleSection> sections) {
        this.sections = sections;
    }
    
    // 便捷方法
    public void addSection(ArticleSection section) {
        if (sections == null) {
            sections = new ArrayList<>();
        }
        sections.add(section);
        section.setArticle(this);
    }
    
    public void removeSection(ArticleSection section) {
        if (sections != null) {
            sections.remove(section);
            section.setArticle(null);
        }
    }
    
    @Override
    public String toString() {
        return "Article{" +
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
