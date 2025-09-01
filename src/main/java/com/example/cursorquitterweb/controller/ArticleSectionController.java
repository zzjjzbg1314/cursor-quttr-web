package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateArticleSectionRequest;
import com.example.cursorquitterweb.dto.UpdateArticleSectionRequest;
import com.example.cursorquitterweb.entity.ArticleSection;
import com.example.cursorquitterweb.service.ArticleSectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * 文章章节控制器
 */
@RestController
@RequestMapping("/api/article-sections")
@CrossOrigin(origins = "*")
public class ArticleSectionController {
    
    @Autowired
    private ArticleSectionService articleSectionService;
    
    /**
     * 创建文章章节
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ArticleSection>> createSection(@Valid @RequestBody CreateArticleSectionRequest request) {
        try {
            ArticleSection section = articleSectionService.createSection(request);
            return ResponseEntity.ok(ApiResponse.success("章节创建成功", section));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("创建章节失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新文章章节
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ArticleSection>> updateSection(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateArticleSectionRequest request) {
        try {
            request.setId(id);
            ArticleSection section = articleSectionService.updateSection(request);
            return ResponseEntity.ok(ApiResponse.success("章节更新成功", section));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("更新章节失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取章节
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArticleSection>> getSectionById(@PathVariable UUID id) {
        try {
            return articleSectionService.getSectionById(id)
                    .map(section -> ResponseEntity.ok(ApiResponse.success("获取章节成功", section)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("获取章节失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据文章ID获取所有章节
     */
    @GetMapping("/article/{articleId}")
    public ResponseEntity<ApiResponse<List<ArticleSection>>> getSectionsByArticleId(@PathVariable UUID articleId) {
        try {
            List<ArticleSection> sections = articleSectionService.getSectionsByArticleId(articleId);
            return ResponseEntity.ok(ApiResponse.success("获取章节列表成功", sections));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("获取章节列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除章节
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSection(@PathVariable UUID id) {
        try {
            articleSectionService.deleteSection(id);
            return ResponseEntity.ok(ApiResponse.success("章节删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("删除章节失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据文章ID删除所有章节
     */
    @DeleteMapping("/article/{articleId}")
    public ResponseEntity<ApiResponse<Void>> deleteSectionsByArticleId(@PathVariable UUID articleId) {
        try {
            articleSectionService.deleteSectionsByArticleId(articleId);
            return ResponseEntity.ok(ApiResponse.success("文章所有章节删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("删除文章章节失败: " + e.getMessage()));
        }
    }
    
    /**
     * 重新排序章节
     */
    @PostMapping("/{articleId}/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderSections(@PathVariable UUID articleId) {
        try {
            articleSectionService.reorderSections(articleId);
            return ResponseEntity.ok(ApiResponse.success("章节重新排序成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("重新排序失败: " + e.getMessage()));
        }
    }
    
    /**
     * 移动章节到指定位置
     */
    @PostMapping("/{id}/move")
    public ResponseEntity<ApiResponse<Void>> moveSection(
            @PathVariable UUID id,
            @RequestParam Integer newIndex) {
        try {
            articleSectionService.moveSection(id, newIndex);
            return ResponseEntity.ok(ApiResponse.success("章节移动成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("移动章节失败: " + e.getMessage()));
        }
    }
}
