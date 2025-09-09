package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.Breathe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 呼吸练习数据访问接口
 */
@Repository
public interface BreatheRepository extends JpaRepository<Breathe, UUID> {
    
    /**
     * 根据ID查找呼吸练习
     */
    Optional<Breathe> findById(UUID id);
    
    /**
     * 根据标题模糊查询呼吸练习，按创建时间倒序排列
     */
    List<Breathe> findByTitleContainingIgnoreCaseOrderByCreateAtDesc(String title);
    
    /**
     * 查找所有呼吸练习，按创建时间倒序排列
     */
    List<Breathe> findAllByOrderByCreateAtDesc();
    
    /**
     * 查找所有呼吸练习，按创建时间升序排列
     */
    List<Breathe> findAllByOrderByCreateAtAsc();
    
    /**
     * 分页查找所有呼吸练习，支持动态排序
     */
    Page<Breathe> findAll(Pageable pageable);
    
    /**
     * 分页查找所有呼吸练习，按创建时间倒序排列
     */
    Page<Breathe> findAllByOrderByCreateAtDesc(Pageable pageable);
    
    /**
     * 根据创建时间范围查找呼吸练习，按创建时间倒序排列
     */
    List<Breathe> findByCreateAtBetweenOrderByCreateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据更新时间范围查找呼吸练习，按更新时间倒序排列
     */
    List<Breathe> findByUpdateAtBetweenOrderByUpdateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计指定时间范围内的呼吸练习数量
     */
    long countByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据音频链接查找呼吸练习
     */
    Optional<Breathe> findByAudiourl(String audiourl);
    
    /**
     * 查找有音频链接的呼吸练习
     */
    List<Breathe> findByAudiourlIsNotNullOrderByCreateAtDesc();
    
    /**
     * 根据标题精确查询呼吸练习
     */
    Optional<Breathe> findByTitle(String title);
    
    /**
     * 统计呼吸练习总数
     */
    long count();
    
    /**
     * 根据标题关键词搜索呼吸练习（支持中文全文搜索）
     */
    @Query(value = "SELECT * FROM breathe WHERE to_tsvector('chinese', title) @@ plainto_tsquery('chinese', :keyword)", nativeQuery = true)
    List<Breathe> searchBreatheByTitleKeyword(@Param("keyword") String keyword);
    
    /**
     * 获取最新的呼吸练习列表（按创建时间降序）
     */
    @Query("SELECT b FROM Breathe b ORDER BY b.createAt DESC")
    List<Breathe> findLatestBreathe();
    
    /**
     * 获取最新的呼吸练习列表（按创建时间降序，限制数量）
     */
    @Query("SELECT b FROM Breathe b ORDER BY b.createAt DESC")
    List<Breathe> findLatestBreathe(Pageable pageable);
    
    /**
     * 分页查询呼吸练习列表（按创建时间降序）
     */
    @Query("SELECT b FROM Breathe b ORDER BY b.createAt DESC")
    Page<Breathe> findBreathePage(Pageable pageable);
    
    /**
     * 根据标题模糊查询并分页
     */
    @Query("SELECT b FROM Breathe b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%')) ORDER BY b.createAt DESC")
    Page<Breathe> findByTitleContainingIgnoreCasePage(@Param("title") String title, Pageable pageable);
}
