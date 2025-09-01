package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.ArticleSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 文章章节数据访问层
 */
@Repository
public interface ArticleSectionRepository extends JpaRepository<ArticleSection, UUID> {
    
    /**
     * 根据文章ID查找所有章节，按章节索引排序
     */
    List<ArticleSection> findByArticleIdOrderBySectionIndexAsc(UUID articleId);
    
    /**
     * 根据文章ID查找所有章节
     */
    List<ArticleSection> findByArticleId(UUID articleId);
    
    /**
     * 根据文章ID删除所有章节
     */
    void deleteByArticleId(UUID articleId);
    
    /**
     * 统计文章的章节数量
     */
    long countByArticleId(UUID articleId);
    
    /**
     * 查找指定文章的最大章节索引
     */
    @Query("SELECT MAX(s.sectionIndex) FROM ArticleSection s WHERE s.articleId = :articleId")
    Integer findMaxSectionIndexByArticleId(@Param("articleId") UUID articleId);
    
    /**
     * 查找指定文章和索引的章节
     */
    ArticleSection findByArticleIdAndSectionIndex(UUID articleId, Integer sectionIndex);
}
