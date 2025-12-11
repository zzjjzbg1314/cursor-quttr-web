package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.ArticleCnPageResult;
import com.example.cursorquitterweb.dto.ArticleWithSectionsCnDto;
import com.example.cursorquitterweb.dto.ArticleWithDetailedSectionsCnDto;
import com.example.cursorquitterweb.dto.ArticlesGroupedByTypeCnDto;
import com.example.cursorquitterweb.dto.CreateArticleRequest;
import com.example.cursorquitterweb.dto.PageResponse;
import com.example.cursorquitterweb.dto.UpdateArticleRequest;
import com.example.cursorquitterweb.entity.ArticleCn;
import com.example.cursorquitterweb.service.ArticleCnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文章控制器（中文版）
 */
@RestController
@RequestMapping("/api/articles/cn")
public class ArticleCnController {
    
    @Autowired
    private ArticleCnService articleCnService;
    
    /**
     * 创建新文章
     */
    @PostMapping("/create")
    public ApiResponse<ArticleCn> createArticle(@RequestBody CreateArticleRequest request) {
        try {
            ArticleCn article;
            if (request.getContent() != null && request.getStatus() != null) {
                article = articleCnService.createArticle(
                    request.getType(),
                    request.getPostImg(),
                    request.getColor(),
                    request.getTitle(),
                    request.getContent(),
                    request.getStatus()
                );
            } else if (request.getContent() != null) {
                article = articleCnService.createArticle(
                    request.getType(),
                    request.getPostImg(),
                    request.getColor(),
                    request.getTitle(),
                    request.getContent()
                );
            } else if (request.getStatus() != null) {
                article = articleCnService.createArticle(
                    request.getType(),
                    request.getPostImg(),
                    request.getColor(),
                    request.getTitle(),
                    request.getStatus()
                );
            } else {
                article = articleCnService.createArticle(
                    request.getType(),
                    request.getPostImg(),
                    request.getColor(),
                    request.getTitle()
                );
            }
            return ApiResponse.success("文章创建成功", article);
        } catch (Exception e) {
            return ApiResponse.error("创建文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取文章
     */
    @GetMapping("/{articleId}")
    public ApiResponse<ArticleCn> getArticle(@PathVariable UUID articleId) {
        try {
            Optional<ArticleCn> article = articleCnService.findById(articleId);
            if (article.isPresent()) {
                return ApiResponse.success("获取文章成功", article.get());
            } else {
                return ApiResponse.error("文章不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新文章
     */
    @PutMapping("/{articleId}/update")
    public ApiResponse<ArticleCn> updateArticle(@PathVariable UUID articleId, @RequestBody UpdateArticleRequest request) {
        try {
            ArticleCn article;
            if (request.getContent() != null) {
                article = articleCnService.updateArticle(
                    articleId,
                    request.getType(),
                    request.getPostImg(),
                    request.getColor(),
                    request.getTitle(),
                    request.getContent()
                );
            } else {
                article = articleCnService.updateArticle(
                    articleId,
                    request.getType(),
                    request.getPostImg(),
                    request.getColor(),
                    request.getTitle()
                );
            }
            return ApiResponse.success("文章更新成功", article);
        } catch (Exception e) {
            return ApiResponse.error("更新文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新文章状态
     */
    @PutMapping("/{articleId}/status")
    public ApiResponse<String> updateArticleStatus(@PathVariable UUID articleId, @RequestParam String status) {
        try {
            articleCnService.updateArticleStatus(articleId, status);
            return ApiResponse.success("文章状态更新成功", null);
        } catch (Exception e) {
            return ApiResponse.error("更新文章状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除文章（软删除）
     */
    @DeleteMapping("/{articleId}/delete")
    public ApiResponse<String> deleteArticle(@PathVariable UUID articleId) {
        try {
            articleCnService.deleteArticle(articleId);
            return ApiResponse.success("文章删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据类型获取文章
     */
    @GetMapping("/type/{type}")
    public ApiResponse<List<ArticleCn>> getArticlesByType(@PathVariable String type) {
        try {
            List<ArticleCn> articles = articleCnService.findByType(type);
            return ApiResponse.success("获取文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据类型分页获取文章
     */
    @GetMapping("/type/{type}/page")
    public ApiResponse<List<ArticleCn>> getArticlesByTypePage(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<ArticleCn> articles = articleCnService.findByType(type, page, size);
            return ApiResponse.success("获取文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据标题搜索文章
     */
    @GetMapping("/search/title")
    public ApiResponse<List<ArticleCn>> searchArticlesByTitle(@RequestParam String title) {
        try {
            List<ArticleCn> articles = articleCnService.searchByTitle(title);
            return ApiResponse.success("搜索文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("搜索文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据颜色获取文章
     */
    @GetMapping("/color/{color}")
    public ApiResponse<List<ArticleCn>> getArticlesByColor(@PathVariable String color) {
        try {
            List<ArticleCn> articles = articleCnService.findByColor(color);
            return ApiResponse.success("获取文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有活跃文章（分页）
     * 优化：使用窗口函数在单次查询中同时获取数据和总数，避免2次数据库查询
     */
    @GetMapping("/active")
    public ApiResponse<PageResponse<ArticleCn>> getActiveArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // 使用单次查询获取数据和总数
            ArticleCnPageResult result = articleCnService.getAllActiveArticlesWithCount(page, size);
            
            // 创建分页响应对象
            PageResponse<ArticleCn> pageResponse = new PageResponse<>(
                result.getContent(), 
                result.getTotalElements(), 
                page, 
                size
            );
            
            return ApiResponse.success("获取活跃文章成功", pageResponse);
        } catch (Exception e) {
            return ApiResponse.error("获取活跃文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有活跃文章
     */
    @GetMapping("/active/all")
    public ApiResponse<List<ArticleCn>> getAllActiveArticles() {
        try {
            List<ArticleCn> articles = articleCnService.getAllActiveArticles();
            return ApiResponse.success("获取所有活跃文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取所有活跃文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有文章（分页）
     * 优化：使用窗口函数在单次查询中同时获取数据和总数，避免2次数据库查询
     * 使用缓存，缓存键包含 page 和 size 参数
     */
    @GetMapping("/all")
    @Cacheable(value = "articles", key = "#page + '_' + #size")
    public ApiResponse<PageResponse<ArticleCn>> getAllArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // 使用单次查询获取数据和总数
            ArticleCnPageResult result = articleCnService.getAllArticlesWithCount(page, size);
            
            // 创建分页响应对象
            PageResponse<ArticleCn> pageResponse = new PageResponse<>(
                result.getContent(), 
                result.getTotalElements(), 
                page, 
                size
            );
            
            return ApiResponse.success("获取所有文章成功", pageResponse);
        } catch (Exception e) {
            return ApiResponse.error("获取所有文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据时间范围获取文章
     */
    @GetMapping("/timerange")
    public ApiResponse<List<ArticleCn>> getArticlesByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            List<ArticleCn> articles = articleCnService.findByTimeRange(start, end);
            return ApiResponse.success("获取文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计指定类型的文章数量
     */
    @GetMapping("/count/type/{type}")
    public ApiResponse<Long> countArticlesByType(@PathVariable String type) {
        try {
            long count = articleCnService.countByType(type);
            return ApiResponse.success("统计成功", count);
        } catch (Exception e) {
            return ApiResponse.error("统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计指定时间范围内的文章数量
     */
    @GetMapping("/count/timerange")
    public ApiResponse<Long> countArticlesByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            long count = articleCnService.countByTimeRange(start, end);
            return ApiResponse.success("统计成功", count);
        } catch (Exception e) {
            return ApiResponse.error("统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取带章节的文章
     */
    @GetMapping("/{articleId}/with-sections")
    public ApiResponse<ArticleWithSectionsCnDto> getArticleWithSections(@PathVariable UUID articleId) {
        try {
            Optional<ArticleWithSectionsCnDto> articleWithSections = articleCnService.findArticleWithSectionsById(articleId);
            if (articleWithSections.isPresent()) {
                return ApiResponse.success("获取带章节的文章成功", articleWithSections.get());
            } else {
                return ApiResponse.error("文章不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取带章节的文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有带章节的文章
     */
    @GetMapping("/with-sections/all")
    public ApiResponse<List<ArticleWithSectionsCnDto>> getAllArticlesWithSections() {
        try {
            List<ArticleWithSectionsCnDto> articlesWithSections = articleCnService.getAllArticlesWithSections();
            return ApiResponse.success("获取所有带章节的文章成功", articlesWithSections);
        } catch (Exception e) {
            return ApiResponse.error("获取所有带章节的文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据类型获取带章节的文章
     */
    @GetMapping("/type/{type}/with-sections")
    public ApiResponse<List<ArticleWithSectionsCnDto>> getArticlesWithSectionsByType(@PathVariable String type) {
        try {
            List<ArticleWithSectionsCnDto> articlesWithSections = articleCnService.getArticlesWithSectionsByType(type);
            return ApiResponse.success("获取带章节的文章成功", articlesWithSections);
        } catch (Exception e) {
            return ApiResponse.error("获取带章节的文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取带详细章节信息的文章
     * 返回文章信息和关联的所有章节细节，章节按sectionIndex排序
     */
    @GetMapping("/{articleId}/with-detailed-sections")
    public ApiResponse<ArticleWithDetailedSectionsCnDto> getArticleWithDetailedSections(@PathVariable UUID articleId) {
        try {
            Optional<ArticleWithDetailedSectionsCnDto> articleWithDetailedSections = articleCnService.findArticleWithDetailedSectionsById(articleId);
            if (articleWithDetailedSections.isPresent()) {
                return ApiResponse.success("获取带详细章节信息的文章成功", articleWithDetailedSections.get());
            } else {
                return ApiResponse.error("文章不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取带详细章节信息的文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有文章数据，按type分组，分组后每个type内的文章按创建时间排序
     * 使用缓存，缓存键固定为'grouped-by-type'
     */
    @GetMapping("/grouped-by-type")
    @Cacheable(value = "articles", key = "'grouped-by-type'")
    public ApiResponse<ArticlesGroupedByTypeCnDto> getAllArticlesGroupedByType() {
        try {
            ArticlesGroupedByTypeCnDto articlesGroupedByType = articleCnService.getAllArticlesGroupedByType();
            return ApiResponse.success("获取按type分组的文章成功", articlesGroupedByType);
        } catch (Exception e) {
            return ApiResponse.error("获取按type分组的文章失败: " + e.getMessage());
        }
    }
}

