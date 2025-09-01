package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;

/**
 * 创建文章章节请求
 */
public class CreateArticleSectionRequest {
    
    @NotBlank(message = "章节标题不能为空")
    private String title;
    
    @NotBlank(message = "章节内容不能为空")
    private String sectionContent;
    
    private Integer sectionIndex;
    
    public CreateArticleSectionRequest() {}
    
    public CreateArticleSectionRequest(String title, String sectionContent, Integer sectionIndex) {
        this.title = title;
        this.sectionContent = sectionContent;
        this.sectionIndex = sectionIndex;
    }
    
    // Getters and Setters
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
