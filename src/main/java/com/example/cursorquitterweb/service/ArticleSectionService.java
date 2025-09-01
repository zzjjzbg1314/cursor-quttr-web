package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.CreateArticleSectionRequest;
import com.example.cursorquitterweb.dto.UpdateArticleSectionRequest;
import com.example.cursorquitterweb.entity.ArticleSection;
import com.example.cursorquitterweb.repository.ArticleSectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;

/**
 * 文章章节服务层
 */
@Service
@Transactional
public class ArticleSectionService {
    
    @Autowired
    private ArticleSectionRepository articleSectionRepository;
    
    /**
     * 创建文章章节
     */
    public ArticleSection createSection(CreateArticleSectionRequest request, String articleId) {
        // 如果没有指定章节索引，自动设置为下一个索引
        if (request.getSectionIndex() == null) {
            Integer maxIndex = articleSectionRepository.findMaxSectionIndexByArticleId(UUID.fromString(articleId));
            request.setSectionIndex(maxIndex != null ? maxIndex + 1 : 0);
        }
        
        ArticleSection section = new ArticleSection(
            UUID.fromString(articleId),
            request.getTitle(),
            request.getSectionContent(),
            request.getSectionIndex()
        );
        
        return articleSectionRepository.save(section);
    }
    
    /**
     * 批量创建文章章节
     */
    public List<ArticleSection> createMultipleSections(List<CreateArticleSectionRequest> requests, String articleId) {
        List<ArticleSection> createdSections = new ArrayList<>();
        
        // 获取当前最大索引
        Integer maxIndex = articleSectionRepository.findMaxSectionIndexByArticleId(UUID.fromString(articleId));
        int currentIndex = maxIndex != null ? maxIndex + 1 : 0;
        
        for (CreateArticleSectionRequest request : requests) {
            // 如果没有指定章节索引，自动设置为下一个索引
            if (request.getSectionIndex() == null) {
                request.setSectionIndex(currentIndex++);
            }
            
            ArticleSection section = new ArticleSection(
                UUID.fromString(articleId),
                request.getTitle(),
                request.getSectionContent(),
                request.getSectionIndex()
            );
            
            createdSections.add(articleSectionRepository.save(section));
        }
        
        return createdSections;
    }
    
    /**
     * 更新文章章节
     */
    public ArticleSection updateSection(UpdateArticleSectionRequest request) {
        Optional<ArticleSection> optionalSection = articleSectionRepository.findById(request.getId());
        if (!optionalSection.isPresent()) {
            throw new RuntimeException("章节不存在");
        }
        
        ArticleSection section = optionalSection.get();
        
        if (request.getTitle() != null) {
            section.setTitle(request.getTitle());
        }
        if (request.getSectionContent() != null) {
            section.setSectionContent(request.getSectionContent());
        }
        if (request.getSectionIndex() != null) {
            section.setSectionIndex(request.getSectionIndex());
        }
        
        return articleSectionRepository.save(section);
    }
    
    /**
     * 根据ID获取章节
     */
    public Optional<ArticleSection> getSectionById(UUID id) {
        return articleSectionRepository.findById(id);
    }
    
    /**
     * 根据文章ID获取所有章节（按索引排序）
     */
    public List<ArticleSection> getSectionsByArticleId(UUID articleId) {
        return articleSectionRepository.findByArticleIdOrderBySectionIndexAsc(articleId);
    }
    
    /**
     * 删除章节
     */
    public void deleteSection(UUID id) {
        articleSectionRepository.deleteById(id);
    }
    
    /**
     * 根据文章ID删除所有章节
     */
    public void deleteSectionsByArticleId(UUID articleId) {
        articleSectionRepository.deleteByArticleId(articleId);
    }
    
    /**
     * 重新排序章节索引
     */
    public void reorderSections(UUID articleId) {
        List<ArticleSection> sections = articleSectionRepository.findByArticleIdOrderBySectionIndexAsc(articleId);
        
        for (int i = 0; i < sections.size(); i++) {
            ArticleSection section = sections.get(i);
            section.setSectionIndex(i);
            articleSectionRepository.save(section);
        }
    }
    
    /**
     * 移动章节到指定位置
     */
    public void moveSection(UUID sectionId, Integer newIndex) {
        Optional<ArticleSection> optionalSection = articleSectionRepository.findById(sectionId);
        if (!optionalSection.isPresent()) {
            throw new RuntimeException("章节不存在");
        }
        
        ArticleSection section = optionalSection.get();
        UUID articleId = section.getArticleId();
        
        // 获取当前章节列表
        List<ArticleSection> sections = articleSectionRepository.findByArticleIdOrderBySectionIndexAsc(articleId);
        
        // 移除当前章节
        sections.removeIf(s -> s.getId().equals(sectionId));
        
        // 插入到新位置
        if (newIndex >= sections.size()) {
            sections.add(section);
        } else {
            sections.add(newIndex, section);
        }
        
        // 重新设置索引
        for (int i = 0; i < sections.size(); i++) {
            ArticleSection s = sections.get(i);
            s.setSectionIndex(i);
            articleSectionRepository.save(s);
        }
    }
}
