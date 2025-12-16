package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.Feedback;
import com.example.cursorquitterweb.service.FeedbackService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 反馈服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class FeedbackServiceImpl implements FeedbackService {
    
    private static final Logger logger = LogUtil.getLogger(FeedbackServiceImpl.class);
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public Optional<Feedback> findById(Long id) {
        String sql = "SELECT * FROM feedback WHERE id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, id);
        return row != null ? Optional.of(mapToFeedback(row)) : Optional.empty();
    }
    
    @Override
    public Feedback createFeedback(String feedbackType, String content, UUID userId) {
        // 验证用户是否存在
        if (!d1Util.exists("users", "id = ?", EntityMapper.uuidToString(userId))) {
            logger.error("创建反馈失败：用户不存在，user_id: {}", userId);
            throw new RuntimeException("创建反馈失败：用户不存在");
        }
        
        Feedback feedback = new Feedback(feedbackType, content, userId);
        Feedback savedFeedback = saveFeedback(feedback);
        
        logger.info("反馈创建成功，feedback_id: {}, user_id: {}, feedback_type: {}", 
                    savedFeedback.getId(), userId, feedbackType);
        
        return savedFeedback;
    }
    
    @Override
    public List<Feedback> findByUserId(UUID userId) {
        String sql = "SELECT * FROM feedback WHERE user_id = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(userId));
        return rows.stream().map(this::mapToFeedback).collect(Collectors.toList());
    }
    
    @Override
    public List<Feedback> findByUserId(UUID userId, int page, int size) {
        String sql = "SELECT * FROM feedback WHERE user_id = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(userId)).stream()
            .map(this::mapToFeedback)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Feedback> findByFeedbackType(String feedbackType) {
        String sql = "SELECT * FROM feedback WHERE feedback_type = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, feedbackType);
        return rows.stream().map(this::mapToFeedback).collect(Collectors.toList());
    }
    
    @Override
    public List<Feedback> findByFeedbackType(String feedbackType, int page, int size) {
        String sql = "SELECT * FROM feedback WHERE feedback_type = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, feedbackType).stream()
            .map(this::mapToFeedback)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Feedback> getAllFeedbacks(int page, int size) {
        String sql = "SELECT * FROM feedback ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToFeedback)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Feedback> getAllFeedbacks() {
        String sql = "SELECT * FROM feedback ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToFeedback).collect(Collectors.toList());
    }
    
    @Override
    public List<Feedback> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM feedback WHERE created_at >= ? AND created_at <= ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToFeedback).collect(Collectors.toList());
    }
    
    @Override
    public long countByUserId(UUID userId) {
        String sql = "SELECT COUNT(*) as count FROM feedback WHERE user_id = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(userId));
    }
    
    @Override
    public long countByFeedbackType(String feedbackType) {
        String sql = "SELECT COUNT(*) as count FROM feedback WHERE feedback_type = ?";
        return d1Util.queryLong(sql, feedbackType);
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM feedback WHERE created_at >= ? AND created_at <= ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
    }
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) as count FROM feedback";
        return d1Util.queryLong(sql);
    }
    
    /**
     * 保存反馈
     */
    private Feedback saveFeedback(Feedback feedback) {
        if (feedback.getId() == null) {
            // 插入新记录
            feedback.setCreatedAt(OffsetDateTime.now());
            Map<String, Object> data = feedbackToMap(feedback);
            long newId = d1Util.insert("feedback", data);
            feedback.setId(newId);
            return feedback;
        } else {
            // 更新记录（通常不需要更新反馈）
            Map<String, Object> data = feedbackToMap(feedback);
            d1Util.updateById("feedback", data, "id", feedback.getId());
            return feedback;
        }
    }
    
    /**
     * 将 Map 转换为 Feedback 实体
     */
    private Feedback mapToFeedback(Map<String, Object> row) {
        Feedback feedback = new Feedback();
        feedback.setId(EntityMapper.getLong(row, "id"));
        feedback.setFeedbackType(EntityMapper.getString(row, "feedback_type"));
        feedback.setContent(EntityMapper.getString(row, "content"));
        feedback.setUserId(EntityMapper.getUUID(row, "user_id"));
        feedback.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        return feedback;
    }
    
    /**
     * 将 Feedback 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> feedbackToMap(Feedback feedback) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", feedback.getId());
        EntityMapper.putIfNotNull(data, "feedback_type", feedback.getFeedbackType());
        EntityMapper.putIfNotNull(data, "content", feedback.getContent());
        EntityMapper.putIfNotNull(data, "user_id", feedback.getUserId());
        EntityMapper.putIfNotNull(data, "created_at", feedback.getCreatedAt());
        return data;
    }
}

