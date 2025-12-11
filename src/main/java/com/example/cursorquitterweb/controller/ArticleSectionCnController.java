package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateArticleSectionRequest;
import com.example.cursorquitterweb.dto.CreateMultipleArticleSectionsRequest;
import com.example.cursorquitterweb.dto.UpdateArticleSectionRequest;
import com.example.cursorquitterweb.entity.ArticleSectionCn;
import com.example.cursorquitterweb.service.ArticleSectionCnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * 文章章节控制器（中文版）
 */
@RestController
@RequestMapping("/api/article-sections/cn")
public class ArticleSectionCnController {
    
    @Autowired
    private ArticleSectionCnService articleSectionCnService;
    
    /**
     * 创建文章章节
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ArticleSectionCn>> createSection(@Valid @RequestBody CreateArticleSectionRequest request, @RequestParam String articleId) {
        try {
            ArticleSectionCn section = articleSectionCnService.createSection(request, articleId);
            return ResponseEntity.ok(ApiResponse.success("章节创建成功", section));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("创建章节失败: " + e.getMessage()));
        }
    }
    
    /**
     * 批量创建文章章节
     */
    @PostMapping("/create-multiple")
    public ResponseEntity<ApiResponse<List<ArticleSectionCn>>> createMultipleSections(@Valid @RequestBody CreateMultipleArticleSectionsRequest request) {
        try {
            List<ArticleSectionCn> sections = articleSectionCnService.createMultipleSections(request.getSections(), request.getArticleId());
            return ResponseEntity.ok(ApiResponse.success("批量创建章节成功", sections));
        } catch (RuntimeException e) {
            // 处理并发冲突等业务异常
            if (e.getMessage().contains("已存在") || e.getMessage().contains("冲突")) {
                return ResponseEntity.status(409).body(ApiResponse.error("并发冲突: " + e.getMessage()));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error("批量创建章节失败: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("批量创建章节失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新文章章节
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ArticleSectionCn>> updateSection(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateArticleSectionRequest request) {
        try {
            request.setId(id);
            ArticleSectionCn section = articleSectionCnService.updateSection(request);
            return ResponseEntity.ok(ApiResponse.success("章节更新成功", section));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("更新章节失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取章节
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArticleSectionCn>> getSectionById(@PathVariable UUID id) {
        try {
            return articleSectionCnService.getSectionById(id)
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
    public ResponseEntity<ApiResponse<List<ArticleSectionCn>>> getSectionsByArticleId(@PathVariable UUID articleId) {
        try {
            List<ArticleSectionCn> sections = articleSectionCnService.getSectionsByArticleId(articleId);
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
            articleSectionCnService.deleteSection(id);
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
            articleSectionCnService.deleteSectionsByArticleId(articleId);
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
            articleSectionCnService.reorderSections(articleId);
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
            articleSectionCnService.moveSection(id, newIndex);
            return ResponseEntity.ok(ApiResponse.success("章节移动成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("移动章节失败: " + e.getMessage()));
        }
    }
}

