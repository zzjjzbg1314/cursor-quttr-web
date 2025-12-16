package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.Feedback;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 反馈服务接口
 */
public interface FeedbackService {
    
    /**
     * 根据ID查找反馈
     */
    Optional<Feedback> findById(Long id);
    
    /**
     * 创建新反馈
     */
    Feedback createFeedback(String feedbackType, String content, UUID userId);
    
    /**
     * 根据用户ID查找反馈
     */
    List<Feedback> findByUserId(UUID userId);
    
    /**
     * 根据用户ID分页查找反馈
     */
    List<Feedback> findByUserId(UUID userId, int page, int size);
    
    /**
     * 根据反馈类型查找反馈
     */
    List<Feedback> findByFeedbackType(String feedbackType);
    
    /**
     * 根据反馈类型分页查找反馈
     */
    List<Feedback> findByFeedbackType(String feedbackType, int page, int size);
    
    /**
     * 获取所有反馈（分页）
     */
    List<Feedback> getAllFeedbacks(int page, int size);
    
    /**
     * 获取所有反馈
     */
    List<Feedback> getAllFeedbacks();
    
    /**
     * 根据时间范围查找反馈
     */
    List<Feedback> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计用户的反馈数量
     */
    long countByUserId(UUID userId);
    
    /**
     * 统计反馈类型的数量
     */
    long countByFeedbackType(String feedbackType);
    
    /**
     * 统计时间范围内的反馈数量
     */
    long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计总反馈数量
     */
    long count();
}

