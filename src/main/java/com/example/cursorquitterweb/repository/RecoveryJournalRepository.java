package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.RecoveryJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 康复之旅数据访问接口
 */
@Repository
public interface RecoveryJournalRepository extends JpaRepository<RecoveryJournal, UUID> {
    
    /**
     * 根据用户ID查询康复日记列表，按创建时间降序排列
     */
    List<RecoveryJournal> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * 根据用户ID分页查询康复日记，按创建时间降序排列
     */
    Page<RecoveryJournal> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * 根据用户ID和创建时间范围查询康复日记
     */
    List<RecoveryJournal> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID userId, OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据标题或内容模糊搜索康复日记（指定用户）
     */
    @Query("SELECT rj FROM RecoveryJournal rj WHERE rj.userId = :userId AND " +
           "(LOWER(rj.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(rj.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY rj.createdAt DESC")
    List<RecoveryJournal> findByUserIdAndKeywordSearch(@Param("userId") UUID userId, @Param("keyword") String keyword);
    
    /**
     * 统计用户的康复日记数量
     */
    long countByUserId(UUID userId);
    
    /**
     * 根据用户ID和日期范围统计康复日记数量
     */
    long countByUserIdAndCreatedAtBetween(UUID userId, OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 获取用户最近的一篇康复日记
     */
    RecoveryJournal findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * 获取用户最早的康复日记创建时间
     */
    @Query("SELECT MIN(rj.createdAt) FROM RecoveryJournal rj WHERE rj.userId = :userId")
    OffsetDateTime findEarliestCreatedAtByUserId(@Param("userId") UUID userId);
    
    /**
     * 获取用户最新的康复日记创建时间
     */
    @Query("SELECT MAX(rj.createdAt) FROM RecoveryJournal rj WHERE rj.userId = :userId")
    OffsetDateTime findLatestCreatedAtByUserId(@Param("userId") UUID userId);
}
