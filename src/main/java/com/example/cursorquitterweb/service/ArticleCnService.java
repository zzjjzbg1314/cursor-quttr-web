package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.ArticleWithSectionsCnDto;
import com.example.cursorquitterweb.dto.ArticleWithDetailedSectionsCnDto;
import com.example.cursorquitterweb.dto.ArticlesGroupedByTypeCnDto;
import com.example.cursorquitterweb.entity.ArticleCn;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文章服务接口（中文版）
 */
public interface ArticleCnService {
    
    /**
     * 根据ID查找文章
     */
    Optional<ArticleCn> findById(UUID articleId);
    
    /**
     * 创建新文章
     */
    ArticleCn createArticle(String type, String postImg, String color, String title);
    
    /**
     * 创建新文章（包含内容）
     */
    ArticleCn createArticle(String type, String postImg, String color, String title, String content);
    
    /**
     * 创建新文章（包含内容和状态）
     */
    ArticleCn createArticle(String type, String postImg, String color, String title, String content, String status);
    
    /**
     * 更新文章信息
     */
    ArticleCn updateArticle(UUID articleId, String type, String postImg, String color, String title);
    
    /**
     * 更新文章信息（包含内容）
     */
    ArticleCn updateArticle(UUID articleId, String type, String postImg, String color, String title, String content);
    
    /**
     * 更新文章状态
     */
    void updateArticleStatus(UUID articleId, String status);
    
    /**
     * 删除文章（软删除，将状态设为inactive）
     */
    void deleteArticle(UUID articleId);
    
    /**
     * 根据类型查找文章
     */
    List<ArticleCn> findByType(String type);
    
    /**
     * 根据类型分页查找文章（已移除 Spring Data Page，返回 List）
     */
    List<ArticleCn> findByType(String type, int page, int size);
    
    /**
     * 根据标题搜索文章
     */
    List<ArticleCn> searchByTitle(String title);
    
    /**
     * 根据颜色查找文章
     */
    List<ArticleCn> findByColor(String color);
    
    /**
     * 获取所有活跃文章（分页，已移除 Spring Data Page，返回 List）
     */
    List<ArticleCn> getAllActiveArticles(int page, int size);
    
    /**
     * 获取所有活跃文章（分页，使用窗口函数一次性获取数据和总数）
     * 性能优化：使用窗口函数在单次查询中同时获取数据和总数，避免2次数据库查询
     */
    com.example.cursorquitterweb.dto.ArticleCnPageResult getAllActiveArticlesWithCount(int page, int size);
    
    /**
     * 获取所有活跃文章
     */
    List<ArticleCn> getAllActiveArticles();
    
    /**
     * 获取所有文章（分页，已移除 Spring Data Page，返回 List）
     */
    List<ArticleCn> getAllArticles(int page, int size);
    
    /**
     * 获取所有文章（分页，使用窗口函数一次性获取数据和总数）
     * 性能优化：使用窗口函数在单次查询中同时获取数据和总数，避免2次数据库查询
     */
    com.example.cursorquitterweb.dto.ArticleCnPageResult getAllArticlesWithCount(int page, int size);
    
    /**
     * 获取所有文章
     */
    List<ArticleCn> getAllArticles();
    
    /**
     * 根据时间范围查找文章
     */
    List<ArticleCn> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计指定类型的文章数量
     */
    long countByType(String type);
    
    /**
     * 统计指定时间范围内的文章数量
     */
    long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据ID获取带章节的文章
     */
    Optional<ArticleWithSectionsCnDto> findArticleWithSectionsById(UUID articleId);
    
    /**
     * 获取所有带章节的文章
     */
    List<ArticleWithSectionsCnDto> getAllArticlesWithSections();
    
    /**
     * 根据类型获取带章节的文章
     */
    List<ArticleWithSectionsCnDto> getArticlesWithSectionsByType(String type);
    
    /**
     * 根据ID获取带详细章节信息的文章
     */
    Optional<ArticleWithDetailedSectionsCnDto> findArticleWithDetailedSectionsById(UUID articleId);
    
    /**
     * 获取所有文章并按type分组，按创建时间排序
     */
    ArticlesGroupedByTypeCnDto getAllArticlesGroupedByType();
}

