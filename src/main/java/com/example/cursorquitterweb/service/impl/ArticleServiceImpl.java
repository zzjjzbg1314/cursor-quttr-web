package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.ArticleWithSectionsDto;
import com.example.cursorquitterweb.entity.Article;
import com.example.cursorquitterweb.entity.ArticleSection;
import com.example.cursorquitterweb.repository.ArticleRepository;
import com.example.cursorquitterweb.repository.ArticleSectionRepository;
import com.example.cursorquitterweb.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文章服务实现类
 */
@Service
@Transactional
public class ArticleServiceImpl implements ArticleService {
    
    @Autowired
    private ArticleRepository articleRepository;
    
    @Autowired
    private ArticleSectionRepository articleSectionRepository;
    
    @Override
    public Optional<Article> findById(UUID articleId) {
        return articleRepository.findByArticleIdAndStatus(articleId, "active");
    }
    
    @Override
    public Article createArticle(String type, String postImg, String color, String title) {
        Article article = new Article(type, postImg, color, title);
        return articleRepository.save(article);
    }
    
    @Override
    public Article createArticle(String type, String postImg, String color, String title, String status) {
        Article article = new Article(type, postImg, color, title, status);
        return articleRepository.save(article);
    }
    
    @Override
    public Article updateArticle(UUID articleId, String type, String postImg, String color, String title) {
        Optional<Article> optionalArticle = articleRepository.findById(articleId);
        if (optionalArticle.isPresent()) {
            Article article = optionalArticle.get();
            article.setType(type);
            article.setPostImg(postImg);
            article.setColor(color);
            article.setTitle(title);
            return articleRepository.save(article);
        }
        throw new RuntimeException("文章不存在");
    }
    
    @Override
    public void updateArticleStatus(UUID articleId, String status) {
        if (!status.equals("active") && !status.equals("inactive")) {
            throw new IllegalArgumentException("状态只能是 'active' 或 'inactive'");
        }
        articleRepository.updateArticleStatus(articleId, status);
    }
    
    @Override
    public void deleteArticle(UUID articleId) {
        updateArticleStatus(articleId, "inactive");
    }
    
    @Override
    public List<Article> findByType(String type) {
        return articleRepository.findByTypeAndStatusOrderByCreateAtDesc(type, "active");
    }
    
    @Override
    public Page<Article> findByType(String type, Pageable pageable) {
        return articleRepository.findByTypeAndStatus(type, "active", pageable);
    }
    
    @Override
    public List<Article> searchByTitle(String title) {
        return articleRepository.findByTitleContainingIgnoreCaseAndStatusOrderByCreateAtDesc(title, "active");
    }
    
    @Override
    public List<Article> findByColor(String color) {
        return articleRepository.findByColorAndStatusOrderByCreateAtDesc(color, "active");
    }
    
    @Override
    public Page<Article> getAllActiveArticles(Pageable pageable) {
        return articleRepository.findByStatusOrderByCreateAtDesc("active", pageable);
    }
    
    @Override
    public List<Article> getAllActiveArticles() {
        return articleRepository.findByStatusOrderByCreateAtDesc("active");
    }
    
    @Override
    public Page<Article> getAllArticles(Pageable pageable) {
        return articleRepository.findAll(pageable);
    }
    
    @Override
    public List<Article> getAllArticles() {
        return articleRepository.findAll();
    }
    
    @Override
    public List<Article> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return articleRepository.findByCreateAtBetweenAndStatusOrderByCreateAtDesc(startTime, endTime, "active");
    }
    
    @Override
    public long countByType(String type) {
        return articleRepository.countByTypeAndStatus(type, "active");
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return articleRepository.countByCreateAtBetweenAndStatus(startTime, endTime, "active");
    }
    
    @Override
    public Optional<ArticleWithSectionsDto> findArticleWithSectionsById(UUID articleId) {
        Optional<Article> optionalArticle = articleRepository.findByArticleIdAndStatus(articleId, "active");
        if (optionalArticle.isPresent()) {
            Article article = optionalArticle.get();
            List<ArticleSection> sections = articleSectionRepository.findByArticleIdOrderBySectionIndexAsc(articleId);
            ArticleWithSectionsDto dto = new ArticleWithSectionsDto(article, sections);
            return Optional.of(dto);
        }
        return Optional.empty();
    }
    
    @Override
    public List<ArticleWithSectionsDto> getAllArticlesWithSections() {
        List<Article> articles = articleRepository.findByStatusOrderByCreateAtDesc("active");
        return articles.stream()
                .map(article -> {
                    List<ArticleSection> sections = articleSectionRepository.findByArticleIdOrderBySectionIndexAsc(article.getArticleId());
                    return new ArticleWithSectionsDto(article, sections);
                })
                .collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public List<ArticleWithSectionsDto> getArticlesWithSectionsByType(String type) {
        List<Article> articles = articleRepository.findByTypeAndStatusOrderByCreateAtDesc(type, "active");
        return articles.stream()
                .map(article -> {
                    List<ArticleSection> sections = articleSectionRepository.findByArticleIdOrderBySectionIndexAsc(article.getArticleId());
                    return new ArticleWithSectionsDto(article, sections);
                })
                .collect(java.util.stream.Collectors.toList());
    }
}
