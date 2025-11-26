package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.CreateArticleSectionRequest;
import com.example.cursorquitterweb.dto.UpdateArticleSectionRequest;
import com.example.cursorquitterweb.entity.ArticleSection;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章章节服务层
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class ArticleSectionService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    /**
     * 创建文章章节
     */
    public ArticleSection createSection(CreateArticleSectionRequest request, String articleId) {
        UUID articleUuid = UUID.fromString(articleId);
        
        // 如果没有指定章节索引，自动设置为下一个索引
        if (request.getSectionIndex() == null) {
            String sql = "SELECT MAX(section_index) as max_index FROM article_sections WHERE article_id = ?";
            Integer maxIndex = d1Util.queryInt(sql, EntityMapper.uuidToString(articleUuid));
            request.setSectionIndex(maxIndex != null ? maxIndex + 1 : 0);
        }
        
        ArticleSection section = new ArticleSection(
            articleUuid,
            request.getTitle(),
            request.getSectionContent(),
            request.getSectionIndex()
        );
        
        return saveSection(section);
    }
    
    /**
     * 批量创建文章章节
     */
    public List<ArticleSection> createMultipleSections(List<CreateArticleSectionRequest> requests, String articleId) {
        List<ArticleSection> createdSections = new ArrayList<>();
        UUID articleUuid = UUID.fromString(articleId);
        
        // 验证请求
        if (requests == null || requests.isEmpty()) {
            throw new RuntimeException("章节列表不能为空");
        }
        
        // 先获取该文章的所有章节记录
        List<ArticleSection> existingSections = getSectionsByArticleId(articleUuid);
        
        // 获取当前最大索引
        Integer maxIndex = existingSections.isEmpty() ? -1 : 
            existingSections.stream().mapToInt(ArticleSection::getSectionIndex).max().orElse(-1);
        int currentIndex = maxIndex + 1;
        
        // 验证所有请求的索引
        Set<Integer> usedIndexes = new HashSet<>();
        for (CreateArticleSectionRequest request : requests) {
            if (request.getSectionIndex() != null) {
                int specifiedIndex = request.getSectionIndex();
                // 检查是否与现有索引冲突
                boolean indexExists = existingSections.stream()
                    .anyMatch(section -> section.getSectionIndex().equals(specifiedIndex));
                if (indexExists) {
                    throw new RuntimeException("章节索引 " + specifiedIndex + " 已存在，请使用不同的索引");
                }
                // 检查是否与当前批次中的其他索引冲突
                if (!usedIndexes.add(specifiedIndex)) {
                    throw new RuntimeException("章节索引 " + specifiedIndex + " 在当前请求中重复");
                }
            }
        }
        
        // 批量创建章节
        for (CreateArticleSectionRequest request : requests) {
            // 如果没有指定章节索引，自动设置为下一个索引
            if (request.getSectionIndex() == null) {
                request.setSectionIndex(currentIndex++);
            }
            
            ArticleSection section = new ArticleSection(
                articleUuid,
                request.getTitle(),
                request.getSectionContent(),
                request.getSectionIndex()
            );
            
            try {
                createdSections.add(saveSection(section));
            } catch (Exception e) {
                // 如果出现唯一约束冲突，抛出更友好的错误信息
                if (e.getMessage() != null && e.getMessage().contains("uk_article_section_article_id_index")) {
                    throw new RuntimeException("章节索引 " + request.getSectionIndex() + " 已存在，可能是并发操作导致的冲突");
                }
                throw e;
            }
        }
        
        return createdSections;
    }
    
    /**
     * 更新文章章节
     */
    public ArticleSection updateSection(UpdateArticleSectionRequest request) {
        Optional<ArticleSection> optionalSection = getSectionById(request.getId());
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
        
        return saveSection(section);
    }
    
    /**
     * 根据ID获取章节
     */
    public Optional<ArticleSection> getSectionById(UUID id) {
        String sql = "SELECT * FROM article_sections WHERE id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToArticleSection(row)) : Optional.empty();
    }
    
    /**
     * 根据文章ID获取所有章节（按索引排序）
     */
    public List<ArticleSection> getSectionsByArticleId(UUID articleId) {
        String sql = "SELECT * FROM article_sections WHERE article_id = ? ORDER BY section_index ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(articleId));
        return rows.stream().map(this::mapToArticleSection).collect(Collectors.toList());
    }
    
    /**
     * 删除章节
     */
    public void deleteSection(UUID id) {
        String sql = "DELETE FROM article_sections WHERE id = ?";
        d1Util.execute(sql, EntityMapper.uuidToString(id));
    }
    
    /**
     * 根据文章ID删除所有章节
     */
    public void deleteSectionsByArticleId(UUID articleId) {
        String sql = "DELETE FROM article_sections WHERE article_id = ?";
        d1Util.execute(sql, EntityMapper.uuidToString(articleId));
    }
    
    /**
     * 重新排序章节索引
     */
    public void reorderSections(UUID articleId) {
        List<ArticleSection> sections = getSectionsByArticleId(articleId);
        
        for (int i = 0; i < sections.size(); i++) {
            ArticleSection section = sections.get(i);
            section.setSectionIndex(i);
            saveSection(section);
        }
    }
    
    /**
     * 移动章节到指定位置
     */
    public void moveSection(UUID sectionId, Integer newIndex) {
        Optional<ArticleSection> optionalSection = getSectionById(sectionId);
        if (!optionalSection.isPresent()) {
            throw new RuntimeException("章节不存在");
        }
        
        ArticleSection section = optionalSection.get();
        UUID articleId = section.getArticleId();
        
        // 获取当前章节列表
        List<ArticleSection> sections = getSectionsByArticleId(articleId);
        
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
            saveSection(s);
        }
    }
    
    /**
     * 保存章节
     */
    private ArticleSection saveSection(ArticleSection section) {
        if (section.getId() == null) {
            // 插入新记录
            section.setId(UUID.randomUUID());
            Map<String, Object> data = articleSectionToMap(section);
            d1Util.insert("article_sections", data);
            return section;
        } else {
            // 更新记录
            Map<String, Object> data = articleSectionToMap(section);
            d1Util.updateById("article_sections", data, "id", EntityMapper.uuidToString(section.getId()));
            return section;
        }
    }
    
    /**
     * 将 Map 转换为 ArticleSection 实体
     */
    private ArticleSection mapToArticleSection(Map<String, Object> row) {
        ArticleSection section = new ArticleSection();
        section.setId(EntityMapper.getUUID(row, "id"));
        section.setArticleId(EntityMapper.getUUID(row, "article_id"));
        section.setTitle(EntityMapper.getString(row, "title"));
        section.setSectionContent(EntityMapper.getString(row, "section_content"));
        section.setSectionIndex(EntityMapper.getInteger(row, "section_index"));
        return section;
    }
    
    /**
     * 将 ArticleSection 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> articleSectionToMap(ArticleSection section) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", section.getId());
        EntityMapper.putIfNotNull(data, "article_id", section.getArticleId());
        EntityMapper.putIfNotNull(data, "title", section.getTitle());
        EntityMapper.putIfNotNull(data, "section_content", section.getSectionContent());
        EntityMapper.putIfNotNull(data, "section_index", section.getSectionIndex());
        return data;
    }
}
