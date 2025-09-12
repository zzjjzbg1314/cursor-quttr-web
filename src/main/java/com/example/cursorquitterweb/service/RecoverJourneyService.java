package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.RecoverJourney;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 康复记录服务接口
 */
public interface RecoverJourneyService {
    
    /**
     * 根据ID查找康复记录
     */
    Optional<RecoverJourney> findById(UUID id);
    
    /**
     * 保存康复记录
     */
    RecoverJourney save(RecoverJourney recoverJourney);
    
    /**
     * 创建新的康复记录（String userId）
     */
    RecoverJourney createRecoverJourney(String userId);
    
    /**
     * 创建新的康复记录（String userId，带感受内容）
     */
    RecoverJourney createRecoverJourney(String userId, String fellContent);
    
    /**
     * 创建新的康复记录
     */
    RecoverJourney createRecoverJourney(UUID userId);
    
    /**
     * 创建新的康复记录（带感受内容）
     */
    RecoverJourney createRecoverJourney(UUID userId, String fellContent);
    
    /**
     * 更新康复记录
     */
    RecoverJourney updateRecoverJourney(RecoverJourney recoverJourney);
    
    /**
     * 删除康复记录
     */
    void deleteRecoverJourney(UUID id);
    
    /**
     * 根据用户ID查询康复记录
     */
    List<RecoverJourney> findByUserId(UUID userId);
    
    /**
     * 根据用户ID查询最新的康复记录
     */
    Optional<RecoverJourney> findLatestByUserId(UUID userId);
    
    /**
     * 更新康复记录感受内容
     */
    RecoverJourney updateFellContent(UUID journeyId, String fellContent);
    
    /**
     * 根据创建时间范围查询记录
     */
    List<RecoverJourney> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据更新时间范围查询记录
     */
    List<RecoverJourney> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计用户的康复记录总数
     */
    long countByUserId(UUID userId);
    
    /**
     * 根据感受内容模糊查询
     */
    List<RecoverJourney> findByFellContentContaining(String content);
    
    /**
     * 查询用户指定时间范围内的康复记录
     */
    List<RecoverJourney> findByUserIdAndCreateAtBetween(UUID userId, OffsetDateTime startTime, OffsetDateTime endTime);
}
