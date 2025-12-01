package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.ArticleWithSectionsDto;
import com.example.cursorquitterweb.dto.ArticleWithDetailedSectionsDto;
import com.example.cursorquitterweb.dto.ArticlesGroupedByTypeDto;
import com.example.cursorquitterweb.entity.Article;
import com.example.cursorquitterweb.entity.ArticleSection;
import com.example.cursorquitterweb.service.ArticleService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文章服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class ArticleServiceImpl implements ArticleService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public Optional<Article> findById(UUID articleId) {
        String sql = "SELECT * FROM article WHERE article_id = ? AND status = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(articleId), "active");
        return row != null ? Optional.of(mapToArticle(row)) : Optional.empty();
    }
    
    @Override
    public Article createArticle(String type, String postImg, String color, String title) {
        Article article = new Article(type, postImg, color, title);
        return saveArticle(article);
    }
    
    @Override
    public Article createArticle(String type, String postImg, String color, String title, String content) {
        Article article = new Article(type, postImg, color, title, content);
        return saveArticle(article);
    }
    
    @Override
    public Article createArticle(String type, String postImg, String color, String title, String content, String status) {
        Article article = new Article(type, postImg, color, title, content, status);
        return saveArticle(article);
    }
    
    @Override
    public Article updateArticle(UUID articleId, String type, String postImg, String color, String title) {
        Optional<Article> optionalArticle = findById(articleId);
        if (optionalArticle.isPresent()) {
            Article article = optionalArticle.get();
            article.setType(type);
            article.setPostImg(postImg);
            article.setColor(color);
            article.setTitle(title);
            return saveArticle(article);
        }
        throw new RuntimeException("文章不存在");
    }
    
    @Override
    public Article updateArticle(UUID articleId, String type, String postImg, String color, String title, String content) {
        Optional<Article> optionalArticle = findById(articleId);
        if (optionalArticle.isPresent()) {
            Article article = optionalArticle.get();
            article.setType(type);
            article.setPostImg(postImg);
            article.setColor(color);
            article.setTitle(title);
            article.setContent(content);
            return saveArticle(article);
        }
        throw new RuntimeException("文章不存在");
    }
    
    @Override
    public void updateArticleStatus(UUID articleId, String status) {
        if (!status.equals("active") && !status.equals("inactive")) {
            throw new IllegalArgumentException("状态只能是 'active' 或 'inactive'");
        }
        String sql = "UPDATE article SET status = ? WHERE article_id = ?";
        d1Util.execute(sql, status, EntityMapper.uuidToString(articleId));
    }
    
    @Override
    public void deleteArticle(UUID articleId) {
        updateArticleStatus(articleId, "inactive");
    }
    
    @Override
    public List<Article> findByType(String type) {
        String sql = "SELECT * FROM article WHERE type = ? AND status = ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, type, "active");
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public List<Article> findByType(String type, int page, int size) {
        String sql = "SELECT * FROM article WHERE type = ? AND status = ? ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size, type, "active").stream()
            .map(this::mapToArticle)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Article> searchByTitle(String title) {
        String sql = "SELECT * FROM article WHERE LOWER(title) LIKE LOWER(?) AND status = ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%", "active");
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public List<Article> findByColor(String color) {
        String sql = "SELECT * FROM article WHERE color = ? AND status = ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, color, "active");
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public List<Article> getAllActiveArticles(int page, int size) {
        String sql = "SELECT * FROM article WHERE status = ? ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size, "active").stream()
            .map(this::mapToArticle)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Article> getAllActiveArticles() {
        String sql = "SELECT * FROM article WHERE status = ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "active");
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public List<Article> getAllArticles(int page, int size) {
        String sql = "SELECT * FROM article ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToArticle)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Article> getAllArticles() {
        String sql = "SELECT * FROM article ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public List<Article> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM article WHERE create_at >= ? AND create_at <= ? AND status = ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime), 
            "active");
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public long countByType(String type) {
        String sql = "SELECT COUNT(*) as count FROM article WHERE type = ? AND status = ?";
        return d1Util.queryLong(sql, type, "active");
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM article WHERE create_at >= ? AND create_at <= ? AND status = ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime), 
            "active");
    }
    
    @Override
    public Optional<ArticleWithSectionsDto> findArticleWithSectionsById(UUID articleId) {
        Optional<Article> optionalArticle = findById(articleId);
        if (optionalArticle.isPresent()) {
            Article article = optionalArticle.get();
            List<ArticleSection> sections = findSectionsByArticleId(articleId);
            ArticleWithSectionsDto dto = new ArticleWithSectionsDto(article, sections);
            return Optional.of(dto);
        }
        return Optional.empty();
    }
    
    @Override
    public List<ArticleWithSectionsDto> getAllArticlesWithSections() {
        List<Article> articles = getAllActiveArticles();
        return articles.stream()
                .map(article -> {
                    List<ArticleSection> sections = findSectionsByArticleId(article.getArticleId());
                    return new ArticleWithSectionsDto(article, sections);
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public List<ArticleWithSectionsDto> getArticlesWithSectionsByType(String type) {
        List<Article> articles = findByType(type);
        return articles.stream()
                .map(article -> {
                    List<ArticleSection> sections = findSectionsByArticleId(article.getArticleId());
                    return new ArticleWithSectionsDto(article, sections);
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<ArticleWithDetailedSectionsDto> findArticleWithDetailedSectionsById(UUID articleId) {
        Optional<Article> optionalArticle = findById(articleId);
        if (optionalArticle.isPresent()) {
            Article article = optionalArticle.get();
            List<ArticleSection> sections = findSectionsByArticleId(articleId);
            ArticleWithDetailedSectionsDto dto = new ArticleWithDetailedSectionsDto(article, sections);
            return Optional.of(dto);
        }
        return Optional.empty();
    }
    
    @Override
    public ArticlesGroupedByTypeDto getAllArticlesGroupedByType() {
        // 获取所有文章，按创建时间排序
        List<Article> allArticles = getAllArticles();
        
        // 按type分组，每个组内的文章保持按创建时间排序
        Map<String, List<Article>> articlesByType = allArticles.stream()
                .collect(Collectors.groupingBy(Article::getType));
        
        return new ArticlesGroupedByTypeDto(articlesByType);
    }
    
    /**
     * 保存文章
     */
    private Article saveArticle(Article article) {
        if (article.getArticleId() == null) {
            // 插入新记录
            article.setArticleId(UUID.randomUUID());
            article.setCreateAt(OffsetDateTime.now());
            article.setStatus("active");
            Map<String, Object> data = articleToMap(article);
            d1Util.insert("article", data);
            return article;
        } else {
            // 更新记录
            Map<String, Object> data = articleToMap(article);
            d1Util.updateById("article", data, "article_id", EntityMapper.uuidToString(article.getArticleId()));
            return article;
        }
    }
    
    /**
     * 根据文章ID查找所有章节
     */
    private List<ArticleSection> findSectionsByArticleId(UUID articleId) {
        String sql = "SELECT * FROM article_section WHERE article_id = ? ORDER BY section_index ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(articleId));
        return rows.stream().map(this::mapToArticleSection).collect(Collectors.toList());
    }
    
    /**
     * 将 Map 转换为 Article 实体
     */
    private Article mapToArticle(Map<String, Object> row) {
        Article article = new Article();
        article.setArticleId(EntityMapper.getUUID(row, "article_id"));
        article.setType(EntityMapper.getString(row, "type"));
        article.setPostImg(EntityMapper.getString(row, "post_img"));
        article.setColor(EntityMapper.getString(row, "color"));
        article.setTitle(EntityMapper.getString(row, "title"));
        article.setContent(EntityMapper.getString(row, "content"));
        article.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        article.setStatus(EntityMapper.getString(row, "status"));
        return article;
    }
    
    /**
     * 将 Article 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> articleToMap(Article article) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "article_id", article.getArticleId());
        EntityMapper.putIfNotNull(data, "type", article.getType());
        EntityMapper.putIfNotNull(data, "post_img", article.getPostImg());
        EntityMapper.putIfNotNull(data, "color", article.getColor());
        EntityMapper.putIfNotNull(data, "title", article.getTitle());
        EntityMapper.putIfNotNull(data, "content", article.getContent());
        EntityMapper.putIfNotNull(data, "create_at", article.getCreateAt());
        EntityMapper.putIfNotNull(data, "status", article.getStatus());
        return data;
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
        section.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        section.setUpdateAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
        return section;
    }
}
