package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 创建文章章节请求
 */
public class CreateArticleSectionRequest {
    
    @NotNull(message = "文章ID不能为空")
    private UUID articleId;
    
    @NotBlank(message = "章节标题不能为空")
    private String title;
    
    @NotBlank(message = "章节内容不能为空")
    private String sectionContent;
    
    private Integer sectionIndex;
    
    public CreateArticleSectionRequest() {}
    
    public CreateArticleSectionRequest(UUID articleId, String title, String sectionContent, Integer sectionIndex) {
        this.articleId = articleId;
        this.title = title;
        this.sectionContent = sectionContent;
        this.sectionIndex = sectionIndex;
    }
    
    // Getters and Setters
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
}
