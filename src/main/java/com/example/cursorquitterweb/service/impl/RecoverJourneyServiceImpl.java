package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.RecoverJourney;
import com.example.cursorquitterweb.service.RecoverJourneyService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
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
 * 康复记录服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class RecoverJourneyServiceImpl implements RecoverJourneyService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public Optional<RecoverJourney> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("recover_journey", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToRecoverJourney(row)) : Optional.empty();
    }
    
    @Override
    public RecoverJourney save(RecoverJourney recoverJourney) {
        if (recoverJourney.getId() == null) {
            // 插入新记录
            recoverJourney.setId(UUID.randomUUID());
            recoverJourney.setCreateAt(OffsetDateTime.now());
            recoverJourney.setUpdateAt(OffsetDateTime.now());
            Map<String, Object> data = recoverJourneyToMap(recoverJourney);
            d1Util.insert("recover_journey", data);
            return recoverJourney;
        } else {
            // 更新记录
            recoverJourney.preUpdate();
            Map<String, Object> data = recoverJourneyToMap(recoverJourney);
            d1Util.updateById("recover_journey", data, "id", EntityMapper.uuidToString(recoverJourney.getId()));
            return recoverJourney;
        }
    }
    
    @Override
    public RecoverJourney createRecoverJourney(String userId) {
        UUID uuid = UUID.fromString(userId);
        RecoverJourney recoverJourney = new RecoverJourney(uuid);
        return save(recoverJourney);
    }
    
    @Override
    public RecoverJourney createRecoverJourney(String userId, String fellContent) {
        UUID uuid = UUID.fromString(userId);
        RecoverJourney recoverJourney = new RecoverJourney(uuid, fellContent);
        return save(recoverJourney);
    }
    
    @Override
    public RecoverJourney createRecoverJourney(UUID userId) {
        RecoverJourney recoverJourney = new RecoverJourney(userId);
        return save(recoverJourney);
    }
    
    @Override
    public RecoverJourney createRecoverJourney(UUID userId, String fellContent) {
        RecoverJourney recoverJourney = new RecoverJourney(userId, fellContent);
        return save(recoverJourney);
    }
    
    @Override
    public RecoverJourney updateRecoverJourney(RecoverJourney recoverJourney) {
        return save(recoverJourney);
    }
    
    @Override
    public void deleteRecoverJourney(UUID id) {
        d1Util.deleteById("recover_journey", "id", EntityMapper.uuidToString(id));
    }
    
    @Override
    public List<RecoverJourney> findByUserId(UUID userId) {
        String sql = "SELECT * FROM recover_journey WHERE user_id = ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(userId));
        return rows.stream().map(this::mapToRecoverJourney).collect(Collectors.toList());
    }
    
    @Override
    public Optional<RecoverJourney> findLatestByUserId(UUID userId) {
        String sql = "SELECT * FROM recover_journey WHERE user_id = ? ORDER BY create_at DESC LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(userId));
        return row != null ? Optional.of(mapToRecoverJourney(row)) : Optional.empty();
    }
    
    @Override
    public RecoverJourney updateFellContent(UUID journeyId, String fellContent) {
        Optional<RecoverJourney> optional = findById(journeyId);
        if (!optional.isPresent()) {
            throw new IllegalArgumentException("康复记录不存在");
        }
        
        RecoverJourney recoverJourney = optional.get();
        recoverJourney.setFellContent(fellContent);
        return save(recoverJourney);
    }
    
    @Override
    public List<RecoverJourney> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM recover_journey WHERE create_at >= ? AND create_at <= ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToRecoverJourney).collect(Collectors.toList());
    }
    
    @Override
    public List<RecoverJourney> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM recover_journey WHERE update_at >= ? AND update_at <= ? ORDER BY update_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToRecoverJourney).collect(Collectors.toList());
    }
    
    @Override
    public long countByUserId(UUID userId) {
        String sql = "SELECT COUNT(*) as count FROM recover_journey WHERE user_id = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(userId));
    }
    
    @Override
    public List<RecoverJourney> findByFellContentContaining(String content) {
        String sql = "SELECT * FROM recover_journey WHERE LOWER(fell_content) LIKE LOWER(?) ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + content + "%");
        return rows.stream().map(this::mapToRecoverJourney).collect(Collectors.toList());
    }
    
    @Override
    public List<RecoverJourney> findByUserIdAndCreateAtBetween(UUID userId, OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM recover_journey WHERE user_id = ? AND create_at >= ? AND create_at <= ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.uuidToString(userId),
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToRecoverJourney).collect(Collectors.toList());
    }
    
    /**
     * 将 Map 转换为 RecoverJourney 实体
     */
    private RecoverJourney mapToRecoverJourney(Map<String, Object> row) {
        RecoverJourney recoverJourney = new RecoverJourney();
        recoverJourney.setId(EntityMapper.getUUID(row, "id"));
        recoverJourney.setUserId(EntityMapper.getUUID(row, "user_id"));
        recoverJourney.setFellContent(EntityMapper.getString(row, "fell_content"));
        recoverJourney.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        recoverJourney.setUpdateAt(EntityMapper.getOffsetDateTime(row, "update_at"));
        return recoverJourney;
    }
    
    /**
     * 将 RecoverJourney 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> recoverJourneyToMap(RecoverJourney recoverJourney) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", recoverJourney.getId());
        EntityMapper.putIfNotNull(data, "user_id", recoverJourney.getUserId());
        EntityMapper.putIfNotNull(data, "fell_content", recoverJourney.getFellContent());
        EntityMapper.putIfNotNull(data, "create_at", recoverJourney.getCreateAt());
        EntityMapper.putIfNotNull(data, "update_at", recoverJourney.getUpdateAt());
        return data;
    }
}
