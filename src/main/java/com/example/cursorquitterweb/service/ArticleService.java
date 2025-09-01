package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.ArticleWithSectionsDto;
import com.example.cursorquitterweb.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文章服务接口
 */
public interface ArticleService {
    
    /**
     * 根据ID查找文章
     */
    Optional<Article> findById(UUID articleId);
    
    /**
     * 创建新文章
     */
    Article createArticle(String type, String postImg, String color, String title);
    
    /**
     * 创建新文章（包含状态）
     */
    Article createArticle(String type, String postImg, String color, String title, String status);
    
    /**
     * 更新文章信息
     */
    Article updateArticle(UUID articleId, String type, String postImg, String color, String title);
    
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
    List<Article> findByType(String type);
    
    /**
     * 根据类型分页查找文章
     */
    Page<Article> findByType(String type, Pageable pageable);
    
    /**
     * 根据标题搜索文章
     */
    List<Article> searchByTitle(String title);
    
    /**
     * 根据颜色查找文章
     */
    List<Article> findByColor(String color);
    
    /**
     * 获取所有活跃文章（分页）
     */
    Page<Article> getAllActiveArticles(Pageable pageable);
    
    /**
     * 获取所有活跃文章
     */
    List<Article> getAllActiveArticles();
    
    /**
     * 获取所有文章（分页）
     */
    Page<Article> getAllArticles(Pageable pageable);
    
    /**
     * 获取所有文章
     */
    List<Article> getAllArticles();
    
    /**
     * 根据时间范围查找文章
     */
    List<Article> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
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
    Optional<ArticleWithSectionsDto> findArticleWithSectionsById(UUID articleId);
    
    /**
     * 获取所有带章节的文章
     */
    List<ArticleWithSectionsDto> getAllArticlesWithSections();
    
    /**
     * 根据类型获取带章节的文章
     */
    List<ArticleWithSectionsDto> getArticlesWithSectionsByType(String type);
}
