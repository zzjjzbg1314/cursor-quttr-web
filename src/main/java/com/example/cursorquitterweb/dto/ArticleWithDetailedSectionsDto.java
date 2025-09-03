package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.Article;
import com.example.cursorquitterweb.entity.ArticleSection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 包含详细章节信息的文章DTO
 */
public class ArticleWithDetailedSectionsDto {
    
    private UUID articleId;
    private String type;
    private String postImg;
    private String color;
    private String title;
    private String content;
    private OffsetDateTime createAt;
    private String status;
    private List<DetailedSectionDto> sections;
    
    public ArticleWithDetailedSectionsDto() {}
    
    public ArticleWithDetailedSectionsDto(Article article, List<ArticleSection> sections) {
        this.articleId = article.getArticleId();
        this.type = article.getType();
        this.postImg = article.getPostImg();
        this.color = article.getColor();
        this.title = article.getTitle();
        this.content = article.getContent();
        this.createAt = article.getCreateAt();
        this.status = article.getStatus();
        this.sections = sections.stream()
                .map(DetailedSectionDto::new)
                .collect(java.util.stream.Collectors.toList());
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
    
    public List<DetailedSectionDto> getSections() {
        return sections;
    }
    
    public void setSections(List<DetailedSectionDto> sections) {
        this.sections = sections;
    }
    
    @Override
    public String toString() {
        return "ArticleWithDetailedSectionsDto{" +
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
    
    /**
     * 详细章节信息DTO
     */
    public static class DetailedSectionDto {
        private UUID sectionId;
        private UUID articleId;
        private String title;
        private String sectionContent;
        private Integer sectionIndex;
        private OffsetDateTime createAt;
        private OffsetDateTime updateAt;
        
        public DetailedSectionDto() {}
        
        public DetailedSectionDto(ArticleSection section) {
            this.sectionId = section.getId();
            this.articleId = section.getArticleId();
            this.title = section.getTitle();
            this.sectionContent = section.getSectionContent();
            this.sectionIndex = section.getSectionIndex();
            this.createAt = section.getCreateAt();
            this.updateAt = section.getUpdateAt();
        }
        
        // Getters and Setters
        public UUID getSectionId() {
            return sectionId;
        }
        
        public void setSectionId(UUID sectionId) {
            this.sectionId = sectionId;
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
        
        @Override
        public String toString() {
            return "DetailedSectionDto{" +
                    "sectionId=" + sectionId +
                    ", articleId=" + articleId +
                    ", title='" + title + '\'' +
                    ", sectionContent='" + sectionContent + '\'' +
                    ", sectionIndex=" + sectionIndex +
                    ", createAt=" + createAt +
                    ", updateAt=" + updateAt +
                    '}';
        }
    }
}
