package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文章数据访问接口
 */
@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {
    
    /**
     * 根据文章ID查找文章（排除已删除的）
     */
    Optional<Article> findByArticleIdAndStatus(UUID articleId, String status);
    
    /**
     * 根据类型查找文章（活跃状态）
     */
    List<Article> findByTypeAndStatusOrderByCreateAtDesc(String type, String status);
    
    /**
     * 根据类型分页查找文章（活跃状态）
     */
    Page<Article> findByTypeAndStatus(String type, String status, Pageable pageable);
    
    /**
     * 根据标题模糊查询文章（活跃状态）
     */
    List<Article> findByTitleContainingIgnoreCaseAndStatusOrderByCreateAtDesc(String title, String status);
    
    /**
     * 根据颜色查找文章（活跃状态）
     */
    List<Article> findByColorAndStatusOrderByCreateAtDesc(String color, String status);
    
    /**
     * 查找所有活跃的文章，按创建时间倒序排列
     */
    List<Article> findByStatusOrderByCreateAtDesc(String status);
    
    /**
     * 分页查找所有活跃的文章，按创建时间倒序排列
     */
    Page<Article> findByStatusOrderByCreateAtDesc(String status, Pageable pageable);
    
    /**
     * 根据创建时间范围查找文章（活跃状态）
     */
    List<Article> findByCreateAtBetweenAndStatusOrderByCreateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime, String status);
    
    /**
     * 统计指定类型的文章数量（活跃状态）
     */
    long countByTypeAndStatus(String type, String status);
    
    /**
     * 统计指定时间范围内的文章数量（活跃状态）
     */
    long countByCreateAtBetweenAndStatus(OffsetDateTime startTime, OffsetDateTime endTime, String status);
    
    /**
     * 更新文章状态
     */
    @Modifying
    @Query("UPDATE Article a SET a.status = :status WHERE a.articleId = :articleId")
    void updateArticleStatus(@Param("articleId") UUID articleId, @Param("status") String status);
    
    /**
     * 根据状态查找文章
     */
    List<Article> findByStatus(String status);
}
