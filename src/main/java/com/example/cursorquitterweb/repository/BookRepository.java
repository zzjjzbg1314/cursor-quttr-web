package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 电子书数据访问接口
 */
@Repository
public interface BookRepository extends JpaRepository<Book, UUID> {
    
    /**
     * 根据书名模糊查询电子书
     */
    List<Book> findByTitleContainingIgnoreCase(String title);
    
    /**
     * 根据创建时间范围查询电子书
     */
    List<Book> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据更新时间范围查询电子书
     */
    List<Book> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据书名精确查询电子书
     */
    Optional<Book> findByTitle(String title);
    
    /**
     * 查询有封面图片的电子书
     */
    @Query("SELECT b FROM Book b WHERE b.postUrl IS NOT NULL AND b.postUrl != ''")
    List<Book> findBooksWithPostUrl();
    
    /**
     * 查询有PDF链接的电子书
     */
    @Query("SELECT b FROM Book b WHERE b.pdfUrl IS NOT NULL AND b.pdfUrl != ''")
    List<Book> findBooksWithPdfUrl();
    
    /**
     * 统计指定时间范围内创建的电子书数量
     */
    @Query("SELECT COUNT(b) FROM Book b WHERE b.createAt BETWEEN :startTime AND :endTime")
    long countBooksByCreateAtBetween(@Param("startTime") OffsetDateTime startTime, 
                                   @Param("endTime") OffsetDateTime endTime);
    
    /**
     * 根据书名关键词搜索电子书（支持中文全文搜索）
     * 使用原生SQL查询以支持PostgreSQL的全文搜索功能
     */
    @Query(value = "SELECT * FROM books WHERE to_tsvector('chinese', title) @@ plainto_tsquery('chinese', :keyword)", nativeQuery = true)
    List<Book> searchBooksByTitleKeyword(@Param("keyword") String keyword);
    
    /**
     * 获取最新的电子书列表（按创建时间降序）
     */
    @Query("SELECT b FROM Book b ORDER BY b.createAt DESC")
    List<Book> findLatestBooks();
    
    /**
     * 获取最新的电子书列表（按创建时间降序，限制数量）
     */
    @Query("SELECT b FROM Book b ORDER BY b.createAt DESC")
    List<Book> findLatestBooks(org.springframework.data.domain.Pageable pageable);
    
    /**
     * 分页查询电子书列表（按创建时间降序）
     */
    @Query("SELECT b FROM Book b ORDER BY b.createAt DESC")
    org.springframework.data.domain.Page<Book> findBooksPage(org.springframework.data.domain.Pageable pageable);
    
    /**
     * 根据书名模糊查询并分页
     */
    @Query("SELECT b FROM Book b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%')) ORDER BY b.createAt DESC")
    org.springframework.data.domain.Page<Book> findByTitleContainingIgnoreCasePage(@Param("title") String title, 
                                                                                  org.springframework.data.domain.Pageable pageable);
}
