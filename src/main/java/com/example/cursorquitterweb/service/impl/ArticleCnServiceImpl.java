package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.ArticleCnPageResult;
import com.example.cursorquitterweb.dto.ArticleWithSectionsCnDto;
import com.example.cursorquitterweb.dto.ArticleWithDetailedSectionsCnDto;
import com.example.cursorquitterweb.dto.ArticlesGroupedByTypeCnDto;
import com.example.cursorquitterweb.entity.ArticleCn;
import com.example.cursorquitterweb.entity.ArticleSectionCn;
import com.example.cursorquitterweb.service.ArticleCnService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文章服务实现类（中文版）
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class ArticleCnServiceImpl implements ArticleCnService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public Optional<ArticleCn> findById(UUID articleId) {
        String sql = "SELECT * FROM article_cn WHERE article_id = ? AND status = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(articleId), "active");
        return row != null ? Optional.of(mapToArticle(row)) : Optional.empty();
    }
    
    @Override
    @CacheEvict(value = "articles", allEntries = true)
    public ArticleCn createArticle(String type, String postImg, String color, String title) {
        ArticleCn article = new ArticleCn(type, postImg, color, title);
        return saveArticle(article);
    }
    
    @Override
    @CacheEvict(value = "articles", allEntries = true)
    public ArticleCn createArticle(String type, String postImg, String color, String title, String content) {
        ArticleCn article = new ArticleCn(type, postImg, color, title, content);
        return saveArticle(article);
    }
    
    @Override
    @CacheEvict(value = "articles", allEntries = true)
    public ArticleCn createArticle(String type, String postImg, String color, String title, String content, String status) {
        ArticleCn article = new ArticleCn(type, postImg, color, title, content, status);
        return saveArticle(article);
    }
    
    @Override
    @CacheEvict(value = "articles", allEntries = true)
    public ArticleCn updateArticle(UUID articleId, String type, String postImg, String color, String title) {
        Optional<ArticleCn> optionalArticle = findById(articleId);
        if (optionalArticle.isPresent()) {
            ArticleCn article = optionalArticle.get();
            article.setType(type);
            article.setPostImg(postImg);
            article.setColor(color);
            article.setTitle(title);
            return saveArticle(article);
        }
        throw new RuntimeException("文章不存在");
    }
    
    @Override
    @CacheEvict(value = "articles", allEntries = true)
    public ArticleCn updateArticle(UUID articleId, String type, String postImg, String color, String title, String content) {
        Optional<ArticleCn> optionalArticle = findById(articleId);
        if (optionalArticle.isPresent()) {
            ArticleCn article = optionalArticle.get();
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
    @CacheEvict(value = "articles", allEntries = true)
    public void updateArticleStatus(UUID articleId, String status) {
        if (!status.equals("active") && !status.equals("inactive")) {
            throw new IllegalArgumentException("状态只能是 'active' 或 'inactive'");
        }
        String sql = "UPDATE article_cn SET status = ? WHERE article_id = ?";
        d1Util.execute(sql, status, EntityMapper.uuidToString(articleId));
    }
    
    @Override
    @CacheEvict(value = "articles", allEntries = true)
    public void deleteArticle(UUID articleId) {
        updateArticleStatus(articleId, "inactive");
    }
    
    @Override
    public List<ArticleCn> findByType(String type) {
        String sql = "SELECT * FROM article_cn WHERE type = ? AND status = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, type, "active");
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public List<ArticleCn> findByType(String type, int page, int size) {
        String sql = "SELECT * FROM article_cn WHERE type = ? AND status = ? ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size, type, "active").stream()
            .map(this::mapToArticle)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ArticleCn> searchByTitle(String title) {
        String sql = "SELECT * FROM article_cn WHERE LOWER(title) LIKE LOWER(?) AND status = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%", "active");
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public List<ArticleCn> findByColor(String color) {
        String sql = "SELECT * FROM article_cn WHERE color = ? AND status = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, color, "active");
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public List<ArticleCn> getAllActiveArticles(int page, int size) {
        String sql = "SELECT * FROM article_cn WHERE status = ? ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size, "active").stream()
            .map(this::mapToArticle)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有活跃文章（分页，使用窗口函数一次性获取数据和总数）
     * 性能优化：使用窗口函数 COUNT(*) OVER() 在单次查询中同时获取数据和总数，避免2次数据库查询
     */
    public ArticleCnPageResult getAllActiveArticlesWithCount(int page, int size) {
        String sql = "SELECT *, COUNT(*) OVER() as total_count FROM article_cn WHERE status = ? ORDER BY create_at ASC LIMIT ? OFFSET ?";
        
        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, "active", size, offset);
        
        long totalElements = 0;
        List<ArticleCn> articles = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }
            Map<String, Object> articleRow = new HashMap<>(row);
            articleRow.remove("total_count");
            articles.add(mapToArticle(articleRow));
        }
        
        return new ArticleCnPageResult(articles, totalElements);
    }
    
    @Override
    public List<ArticleCn> getAllActiveArticles() {
        String sql = "SELECT * FROM article_cn WHERE status = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "active");
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public List<ArticleCn> getAllArticles(int page, int size) {
        String sql = "SELECT * FROM article_cn ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToArticle)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有文章（分页，使用窗口函数一次性获取数据和总数）
     * 性能优化：使用窗口函数 COUNT(*) OVER() 在单次查询中同时获取数据和总数，避免2次数据库查询
     */
    public ArticleCnPageResult getAllArticlesWithCount(int page, int size) {
        String sql = "SELECT *, COUNT(*) OVER() as total_count FROM article_cn ORDER BY create_at ASC LIMIT ? OFFSET ?";
        
        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, size, offset);
        
        long totalElements = 0;
        List<ArticleCn> articles = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }
            Map<String, Object> articleRow = new HashMap<>(row);
            articleRow.remove("total_count");
            articles.add(mapToArticle(articleRow));
        }
        
        return new ArticleCnPageResult(articles, totalElements);
    }
    
    @Override
    public List<ArticleCn> getAllArticles() {
        String sql = "SELECT * FROM article_cn ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public List<ArticleCn> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM article_cn WHERE create_at >= ? AND create_at <= ? AND status = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime), 
            "active");
        return rows.stream().map(this::mapToArticle).collect(Collectors.toList());
    }
    
    @Override
    public long countByType(String type) {
        String sql = "SELECT COUNT(*) as count FROM article_cn WHERE type = ? AND status = ?";
        return d1Util.queryLong(sql, type, "active");
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM article_cn WHERE create_at >= ? AND create_at <= ? AND status = ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime), 
            "active");
    }
    
    @Override
    public Optional<ArticleWithSectionsCnDto> findArticleWithSectionsById(UUID articleId) {
        Optional<ArticleCn> optionalArticle = findById(articleId);
        if (optionalArticle.isPresent()) {
            ArticleCn article = optionalArticle.get();
            List<ArticleSectionCn> sections = findSectionsByArticleId(articleId);
            ArticleWithSectionsCnDto dto = new ArticleWithSectionsCnDto(article, sections);
            return Optional.of(dto);
        }
        return Optional.empty();
    }
    
    @Override
    public List<ArticleWithSectionsCnDto> getAllArticlesWithSections() {
        List<ArticleCn> articles = getAllActiveArticles();
        return articles.stream()
                .map(article -> {
                    List<ArticleSectionCn> sections = findSectionsByArticleId(article.getArticleId());
                    return new ArticleWithSectionsCnDto(article, sections);
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public List<ArticleWithSectionsCnDto> getArticlesWithSectionsByType(String type) {
        List<ArticleCn> articles = findByType(type);
        return articles.stream()
                .map(article -> {
                    List<ArticleSectionCn> sections = findSectionsByArticleId(article.getArticleId());
                    return new ArticleWithSectionsCnDto(article, sections);
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<ArticleWithDetailedSectionsCnDto> findArticleWithDetailedSectionsById(UUID articleId) {
        Optional<ArticleCn> optionalArticle = findById(articleId);
        if (optionalArticle.isPresent()) {
            ArticleCn article = optionalArticle.get();
            List<ArticleSectionCn> sections = findSectionsByArticleId(articleId);
            ArticleWithDetailedSectionsCnDto dto = new ArticleWithDetailedSectionsCnDto(article, sections);
            return Optional.of(dto);
        }
        return Optional.empty();
    }
    
    @Override
    public ArticlesGroupedByTypeCnDto getAllArticlesGroupedByType() {
        // 获取所有文章，按创建时间排序
        List<ArticleCn> allArticles = getAllArticles();
        
        // 按type分组，每个组内的文章保持按创建时间排序
        Map<String, List<ArticleCn>> articlesByType = allArticles.stream()
                .collect(Collectors.groupingBy(ArticleCn::getType));
        
        return new ArticlesGroupedByTypeCnDto(articlesByType);
    }
    
    /**
     * 保存文章
     */
    private ArticleCn saveArticle(ArticleCn article) {
        if (article.getArticleId() == null) {
            // 插入新记录
            article.setArticleId(UUID.randomUUID());
            article.setCreateAt(OffsetDateTime.now());
            article.setStatus("active");
            Map<String, Object> data = articleToMap(article);
            d1Util.insert("article_cn", data);
            return article;
        } else {
            // 更新记录
            Map<String, Object> data = articleToMap(article);
            d1Util.updateById("article_cn", data, "article_id", EntityMapper.uuidToString(article.getArticleId()));
            return article;
        }
    }
    
    /**
     * 根据文章ID查找所有章节
     */
    private List<ArticleSectionCn> findSectionsByArticleId(UUID articleId) {
        String sql = "SELECT * FROM article_section_cn WHERE article_id = ? ORDER BY section_index ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(articleId));
        return rows.stream().map(this::mapToArticleSection).collect(Collectors.toList());
    }
    
    /**
     * 将 Map 转换为 ArticleCn 实体
     */
    private ArticleCn mapToArticle(Map<String, Object> row) {
        ArticleCn article = new ArticleCn();
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
     * 将 ArticleCn 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> articleToMap(ArticleCn article) {
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
     * 将 Map 转换为 ArticleSectionCn 实体
     */
    private ArticleSectionCn mapToArticleSection(Map<String, Object> row) {
        ArticleSectionCn section = new ArticleSectionCn();
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

