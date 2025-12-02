package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.AppBlockSchedule;
import com.example.cursorquitterweb.service.AppBlockScheduleService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 应用屏蔽计划服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class AppBlockScheduleServiceImpl implements AppBlockScheduleService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public Optional<AppBlockSchedule> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("app_block_schedule", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToAppBlockSchedule(row)) : Optional.empty();
    }
    
    @Override
    @CacheEvict(value = "appBlockSchedules", allEntries = true)
    public AppBlockSchedule createAppBlockSchedule(String title, String subtitle, String days, String time, String reason, String image) {
        AppBlockSchedule schedule = new AppBlockSchedule(title, subtitle, days, time, reason, image);
        schedule.setId(UUID.randomUUID());
        schedule.setCreatedAt(OffsetDateTime.now());
        Map<String, Object> data = appBlockScheduleToMap(schedule);
        d1Util.insert("app_block_schedule", data);
        return schedule;
    }
    
    @Override
    @CacheEvict(value = "appBlockSchedules", allEntries = true)
    public AppBlockSchedule updateAppBlockSchedule(UUID id, String title, String subtitle, String days, String time, String reason, String image) {
        Optional<AppBlockSchedule> optionalSchedule = findById(id);
        if (!optionalSchedule.isPresent()) {
            throw new RuntimeException("应用屏蔽计划不存在，ID: " + id);
        }
        
        AppBlockSchedule schedule = optionalSchedule.get();
        if (title != null) {
            schedule.setTitle(title);
        }
        if (subtitle != null) {
            schedule.setSubtitle(subtitle);
        }
        if (days != null) {
            schedule.setDays(days);
        }
        if (time != null) {
            schedule.setTime(time);
        }
        if (reason != null) {
            schedule.setReason(reason);
        }
        if (image != null) {
            schedule.setImage(image);
        }
        
        Map<String, Object> data = appBlockScheduleToMap(schedule);
        d1Util.updateById("app_block_schedule", data, "id", EntityMapper.uuidToString(id));
        return schedule;
    }
    
    @Override
    @CacheEvict(value = "appBlockSchedules", allEntries = true)
    public void deleteAppBlockSchedule(UUID id) {
        if (!d1Util.exists("app_block_schedule", "id = ?", EntityMapper.uuidToString(id))) {
            throw new RuntimeException("应用屏蔽计划不存在，ID: " + id);
        }
        d1Util.deleteById("app_block_schedule", "id", EntityMapper.uuidToString(id));
    }
    
    @Override
    @Cacheable(value = "appBlockSchedules", key = "'all'")
    public List<AppBlockSchedule> getAllAppBlockSchedules() {
        String sql = "SELECT * FROM app_block_schedule ORDER BY created_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToAppBlockSchedule).collect(Collectors.toList());
    }
    
    @Override
    @Cacheable(value = "appBlockSchedules", key = "#page + '_' + #size")
    public List<AppBlockSchedule> getAppBlockSchedulesPage(int page, int size) {
        String sql = "SELECT * FROM app_block_schedule ORDER BY created_at ASC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToAppBlockSchedule)
            .collect(Collectors.toList());
    }
    
    /**
     * 将 Map 转换为 AppBlockSchedule 实体
     */
    private AppBlockSchedule mapToAppBlockSchedule(Map<String, Object> row) {
        AppBlockSchedule schedule = new AppBlockSchedule();
        schedule.setId(EntityMapper.getUUID(row, "id"));
        schedule.setTitle(EntityMapper.getString(row, "title"));
        schedule.setSubtitle(EntityMapper.getString(row, "subtitle"));
        schedule.setDays(EntityMapper.getString(row, "days"));
        schedule.setTime(EntityMapper.getString(row, "time"));
        schedule.setReason(EntityMapper.getString(row, "reason"));
        schedule.setImage(EntityMapper.getString(row, "image"));
        schedule.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        return schedule;
    }
    
    /**
     * 将 AppBlockSchedule 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> appBlockScheduleToMap(AppBlockSchedule schedule) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", schedule.getId());
        EntityMapper.putIfNotNull(data, "title", schedule.getTitle());
        EntityMapper.putIfNotNull(data, "subtitle", schedule.getSubtitle());
        EntityMapper.putIfNotNull(data, "days", schedule.getDays());
        EntityMapper.putIfNotNull(data, "time", schedule.getTime());
        EntityMapper.putIfNotNull(data, "reason", schedule.getReason());
        EntityMapper.putIfNotNull(data, "image", schedule.getImage());
        EntityMapper.putIfNotNull(data, "created_at", schedule.getCreatedAt());
        return data;
    }
}

