package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 更新文章章节请求
 */
public class UpdateArticleSectionRequest {
    
    @NotNull(message = "章节ID不能为空")
    private UUID id;
    
    private String title;
    
    private String sectionContent;
    
    private Integer sectionIndex;
    
    public UpdateArticleSectionRequest() {}
    
    public UpdateArticleSectionRequest(UUID id, String title, String sectionContent, Integer sectionIndex) {
        this.id = id;
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
