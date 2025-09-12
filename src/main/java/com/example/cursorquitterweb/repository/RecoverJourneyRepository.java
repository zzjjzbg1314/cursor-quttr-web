package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.RecoverJourney;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 康复记录数据访问接口
 */
@Repository
public interface RecoverJourneyRepository extends JpaRepository<RecoverJourney, UUID> {
    
    /**
     * 根据用户ID查询康复记录
     */
    List<RecoverJourney> findByUserIdOrderByCreateAtDesc(UUID userId);
    
    /**
     * 根据用户ID查询最新的康复记录
     */
    Optional<RecoverJourney> findFirstByUserIdOrderByCreateAtDesc(UUID userId);
    
    /**
     * 统计用户的康复记录总数
     */
    @Query("SELECT COUNT(r) FROM RecoverJourney r WHERE r.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);
    
    /**
     * 查询指定时间范围内创建的记录
     */
    List<RecoverJourney> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 查询指定时间范围内更新的记录
     */
    List<RecoverJourney> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据感受内容模糊查询
     */
    List<RecoverJourney> findByFellContentContainingIgnoreCase(String content);
    
    /**
     * 查询用户指定时间范围内的康复记录
     */
    @Query("SELECT r FROM RecoverJourney r WHERE r.userId = :userId AND r.createAt BETWEEN :startTime AND :endTime ORDER BY r.createAt DESC")
    List<RecoverJourney> findByUserIdAndCreateAtBetween(@Param("userId") UUID userId, 
                                                       @Param("startTime") OffsetDateTime startTime, 
                                                       @Param("endTime") OffsetDateTime endTime);
}
