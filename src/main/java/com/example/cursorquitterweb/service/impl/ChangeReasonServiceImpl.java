package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.CreateChangeReasonRequest;
import com.example.cursorquitterweb.dto.UpdateChangeReasonRequest;
import com.example.cursorquitterweb.entity.ChangeReason;
import com.example.cursorquitterweb.service.ChangeReasonService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户戒色改变理由服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class ChangeReasonServiceImpl implements ChangeReasonService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public ChangeReason createChangeReason(CreateChangeReasonRequest request) {
        ChangeReason changeReason = new ChangeReason(UUID.fromString(request.getUserId()), request.getContent());
        return saveChangeReason(changeReason);
    }
    
    @Override
    public ChangeReason getChangeReasonById(UUID id) {
        Map<String, Object> row = d1Util.findById("change_reason", "id", EntityMapper.uuidToString(id));
        if (row == null) {
            throw new RuntimeException("改变理由记录不存在，ID: " + id);
        }
        return mapToChangeReason(row);
    }
    
    @Override
    public List<ChangeReason> getChangeReasonsByUserId(UUID userId) {
        String sql = "SELECT * FROM change_reason WHERE user_id = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(userId));
        return rows.stream().map(this::mapToChangeReason).collect(Collectors.toList());
    }
    
    @Override
    public ChangeReason getLatestChangeReasonByUserId(UUID userId) {
        String sql = "SELECT * FROM change_reason WHERE user_id = ? ORDER BY created_at DESC LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(userId));
        return row != null ? mapToChangeReason(row) : null;
    }
    
    @Override
    public ChangeReason updateChangeReason(UpdateChangeReasonRequest request) {
        ChangeReason existingReason = getChangeReasonById(request.getId());
        existingReason.setContent(request.getContent());
        return saveChangeReason(existingReason);
    }
    
    @Override
    public void deleteChangeReason(UUID id) {
        if (!d1Util.exists("change_reason", "id = ?", EntityMapper.uuidToString(id))) {
            throw new RuntimeException("改变理由记录不存在，ID: " + id);
        }
        d1Util.deleteById("change_reason", "id", EntityMapper.uuidToString(id));
    }
    
    @Override
    public void deleteChangeReasonsByUserId(UUID userId) {
        String sql = "DELETE FROM change_reason WHERE user_id = ?";
        d1Util.execute(sql, EntityMapper.uuidToString(userId));
    }
    
    @Override
    public long countChangeReasonsByUserId(UUID userId) {
        String sql = "SELECT COUNT(*) as count FROM change_reason WHERE user_id = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(userId));
    }
    
    /**
     * 保存改变理由
     */
    private ChangeReason saveChangeReason(ChangeReason changeReason) {
        if (changeReason.getId() == null) {
            // 插入新记录
            changeReason.setId(UUID.randomUUID());
            changeReason.setCreatedAt(OffsetDateTime.now());
            changeReason.setUpdatedAt(OffsetDateTime.now());
            Map<String, Object> data = changeReasonToMap(changeReason);
            d1Util.insert("change_reason", data);
            return changeReason;
        } else {
            // 更新记录
            changeReason.preUpdate();
            Map<String, Object> data = changeReasonToMap(changeReason);
            d1Util.updateById("change_reason", data, "id", EntityMapper.uuidToString(changeReason.getId()));
            return changeReason;
        }
    }
    
    /**
     * 将 Map 转换为 ChangeReason 实体
     */
    private ChangeReason mapToChangeReason(Map<String, Object> row) {
        ChangeReason changeReason = new ChangeReason();
        changeReason.setId(EntityMapper.getUUID(row, "id"));
        changeReason.setUserId(EntityMapper.getUUID(row, "user_id"));
        changeReason.setContent(EntityMapper.getString(row, "content"));
        changeReason.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        changeReason.setUpdatedAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
        return changeReason;
    }
    
    /**
     * 将 ChangeReason 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> changeReasonToMap(ChangeReason changeReason) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", changeReason.getId());
        EntityMapper.putIfNotNull(data, "user_id", changeReason.getUserId());
        EntityMapper.putIfNotNull(data, "content", changeReason.getContent());
        EntityMapper.putIfNotNull(data, "created_at", changeReason.getCreatedAt());
        EntityMapper.putIfNotNull(data, "updated_at", changeReason.getUpdatedAt());
        return data;
    }
}
