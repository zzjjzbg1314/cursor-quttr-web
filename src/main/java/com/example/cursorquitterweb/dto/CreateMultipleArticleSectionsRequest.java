package com.example.cursorquitterweb.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量创建文章章节请求
 */
public class CreateMultipleArticleSectionsRequest {
    
    @NotNull(message = "文章ID不能为空")
    private String articleId;
    
    @NotEmpty(message = "章节列表不能为空")
    @Valid
    private List<CreateArticleSectionRequest> sections;
    
    public CreateMultipleArticleSectionsRequest() {}
    
    public CreateMultipleArticleSectionsRequest(String articleId, List<CreateArticleSectionRequest> sections) {
        this.articleId = articleId;
        this.sections = sections;
    }
    
    // Getters and Setters
    public String getArticleId() {
        return articleId;
    }
    
    public void setArticleId(String articleId) {
        this.articleId = articleId;
    }
    
    public List<CreateArticleSectionRequest> getSections() {
        return sections;
    }
    
    public void setSections(List<CreateArticleSectionRequest> sections) {
        this.sections = sections;
    }
}
