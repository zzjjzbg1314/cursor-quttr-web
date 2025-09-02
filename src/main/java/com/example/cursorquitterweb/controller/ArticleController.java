package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.ArticleWithSectionsDto;
import com.example.cursorquitterweb.dto.ArticleWithDetailedSectionsDto;
import com.example.cursorquitterweb.dto.CreateArticleRequest;
import com.example.cursorquitterweb.dto.UpdateArticleRequest;
import com.example.cursorquitterweb.entity.Article;
import com.example.cursorquitterweb.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文章控制器
 */
@RestController
@RequestMapping("/api/articles")
public class ArticleController {
    
    @Autowired
    private ArticleService articleService;
    
    /**
     * 创建新文章
     */
    @PostMapping("/create")
    public ApiResponse<Article> createArticle(@RequestBody CreateArticleRequest request) {
        try {
            Article article;
            if (request.getContent() != null && request.getStatus() != null) {
                article = articleService.createArticle(
                    request.getType(),
                    request.getPostImg(),
                    request.getColor(),
                    request.getTitle(),
                    request.getContent(),
                    request.getStatus()
                );
            } else if (request.getContent() != null) {
                article = articleService.createArticle(
                    request.getType(),
                    request.getPostImg(),
                    request.getColor(),
                    request.getTitle(),
                    request.getContent()
                );
            } else if (request.getStatus() != null) {
                article = articleService.createArticle(
                    request.getType(),
                    request.getPostImg(),
                    request.getColor(),
                    request.getTitle(),
                    request.getStatus()
                );
            } else {
                article = articleService.createArticle(
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
    public ApiResponse<Article> getArticle(@PathVariable UUID articleId) {
        try {
            Optional<Article> article = articleService.findById(articleId);
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
    public ApiResponse<Article> updateArticle(@PathVariable UUID articleId, @RequestBody UpdateArticleRequest request) {
        try {
            Article article;
            if (request.getContent() != null) {
                article = articleService.updateArticle(
                    articleId,
                    request.getType(),
                    request.getPostImg(),
                    request.getColor(),
                    request.getTitle(),
                    request.getContent()
                );
            } else {
                article = articleService.updateArticle(
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
            articleService.updateArticleStatus(articleId, status);
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
            articleService.deleteArticle(articleId);
            return ApiResponse.success("文章删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据类型获取文章
     */
    @GetMapping("/type/{type}")
    public ApiResponse<List<Article>> getArticlesByType(@PathVariable String type) {
        try {
            List<Article> articles = articleService.findByType(type);
            return ApiResponse.success("获取文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据类型分页获取文章
     */
    @GetMapping("/type/{type}/page")
    public ApiResponse<Page<Article>> getArticlesByTypePage(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createAt"));
            Page<Article> articles = articleService.findByType(type, pageable);
            return ApiResponse.success("获取文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据标题搜索文章
     */
    @GetMapping("/search/title")
    public ApiResponse<List<Article>> searchArticlesByTitle(@RequestParam String title) {
        try {
            List<Article> articles = articleService.searchByTitle(title);
            return ApiResponse.success("搜索文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("搜索文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据颜色获取文章
     */
    @GetMapping("/color/{color}")
    public ApiResponse<List<Article>> getArticlesByColor(@PathVariable String color) {
        try {
            List<Article> articles = articleService.findByColor(color);
            return ApiResponse.success("获取文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有活跃文章（分页）
     */
    @GetMapping("/active")
    public ApiResponse<Page<Article>> getActiveArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createAt"));
            Page<Article> articles = articleService.getAllActiveArticles(pageable);
            return ApiResponse.success("获取活跃文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取活跃文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有活跃文章
     */
    @GetMapping("/active/all")
    public ApiResponse<List<Article>> getAllActiveArticles() {
        try {
            List<Article> articles = articleService.getAllActiveArticles();
            return ApiResponse.success("获取所有活跃文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取所有活跃文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有文章（分页）
     */
    @GetMapping("/all")
    public ApiResponse<Page<Article>> getAllArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createAt"));
            Page<Article> articles = articleService.getAllArticles(pageable);
            return ApiResponse.success("获取所有文章成功", articles);
        } catch (Exception e) {
            return ApiResponse.error("获取所有文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据时间范围获取文章
     */
    @GetMapping("/timerange")
    public ApiResponse<List<Article>> getArticlesByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            List<Article> articles = articleService.findByTimeRange(start, end);
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
            long count = articleService.countByType(type);
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
            long count = articleService.countByTimeRange(start, end);
            return ApiResponse.success("统计成功", count);
        } catch (Exception e) {
            return ApiResponse.error("统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取带章节的文章
     */
    @GetMapping("/{articleId}/with-sections")
    public ApiResponse<ArticleWithSectionsDto> getArticleWithSections(@PathVariable UUID articleId) {
        try {
            Optional<ArticleWithSectionsDto> articleWithSections = articleService.findArticleWithSectionsById(articleId);
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
    public ApiResponse<List<ArticleWithSectionsDto>> getAllArticlesWithSections() {
        try {
            List<ArticleWithSectionsDto> articlesWithSections = articleService.getAllArticlesWithSections();
            return ApiResponse.success("获取所有带章节的文章成功", articlesWithSections);
        } catch (Exception e) {
            return ApiResponse.error("获取所有带章节的文章失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据类型获取带章节的文章
     */
    @GetMapping("/type/{type}/with-sections")
    public ApiResponse<List<ArticleWithSectionsDto>> getArticlesWithSectionsByType(@PathVariable String type) {
        try {
            List<ArticleWithSectionsDto> articlesWithSections = articleService.getArticlesWithSectionsByType(type);
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
    public ApiResponse<ArticleWithDetailedSectionsDto> getArticleWithDetailedSections(@PathVariable UUID articleId) {
        try {
            Optional<ArticleWithDetailedSectionsDto> articleWithDetailedSections = articleService.findArticleWithDetailedSectionsById(articleId);
            if (articleWithDetailedSections.isPresent()) {
                return ApiResponse.success("获取带详细章节信息的文章成功", articleWithDetailedSections.get());
            } else {
                return ApiResponse.error("文章不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取带详细章节信息的文章失败: " + e.getMessage());
        }
    }
}
