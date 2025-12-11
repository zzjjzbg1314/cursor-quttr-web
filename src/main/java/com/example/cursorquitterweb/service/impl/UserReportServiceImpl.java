package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.UserReportDto;
import com.example.cursorquitterweb.dto.UserReportPageResult;
import com.example.cursorquitterweb.entity.UserReport;
import com.example.cursorquitterweb.service.UserReportService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户举报服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class UserReportServiceImpl implements UserReportService {
    
    private static final Logger logger = LogUtil.getLogger(UserReportServiceImpl.class);
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public UserReport createReport(UUID reportedUserId, UUID reporterUserId, String reason, String remark) {
        // 验证被举报的用户是否存在
        if (!d1Util.exists("users", "id = ?", EntityMapper.uuidToString(reportedUserId))) {
            logger.error("举报失败：被举报的用户不存在，user_id: {}", reportedUserId);
            throw new RuntimeException("举报失败：被举报的用户不存在");
        }
        
        // 验证举报人是否存在
        if (!d1Util.exists("users", "id = ?", EntityMapper.uuidToString(reporterUserId))) {
            logger.error("举报失败：举报人不存在，user_id: {}", reporterUserId);
            throw new RuntimeException("举报失败：举报人不存在");
        }
        
        // 不允许举报自己
        if (reportedUserId.equals(reporterUserId)) {
            logger.warn("举报失败：不能举报自己，user_id: {}", reporterUserId);
            throw new RuntimeException("不能举报自己");
        }
        
        // 检查是否已经举报过该用户
        if (hasReportedUser(reportedUserId, reporterUserId)) {
            logger.warn("用户已经举报过该用户，reported_user_id: {}, reporter_user_id: {}", reportedUserId, reporterUserId);
            throw new RuntimeException("您已经举报过该用户");
        }
        
        UserReport userReport = new UserReport(reportedUserId, reporterUserId, reason, remark);
        UserReport savedReport = saveUserReport(userReport);
        
        logger.info("用户举报创建成功，report_id: {}, reported_user_id: {}, reporter_user_id: {}, reason: {}", 
                    savedReport.getId(), reportedUserId, reporterUserId, reason);
        
        return savedReport;
    }
    
    @Override
    public Optional<UserReport> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("user_reports", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToUserReport(row)) : Optional.empty();
    }
    
    @Override
    public List<UserReport> getAllReports(int page, int size) {
        String sql = "SELECT * FROM user_reports ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToUserReport)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有举报记录（分页，使用窗口函数一次性获取数据和总数）
     * 性能优化：使用窗口函数 COUNT(*) OVER() 在单次查询中同时获取数据和总数，避免2次数据库查询
     */
    public UserReportPageResult getAllReportsWithCount(int page, int size) {
        String sql = "SELECT *, COUNT(*) OVER() as total_count FROM user_reports ORDER BY created_at DESC LIMIT ? OFFSET ?";
        
        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, size, offset);
        
        long totalElements = 0;
        List<UserReport> reports = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }
            Map<String, Object> reportRow = new HashMap<>(row);
            reportRow.remove("total_count");
            reports.add(mapToUserReport(reportRow));
        }
        
        // 转换为DTO
        List<UserReportDto> content = reports.stream()
                .map(UserReportDto::new)
                .collect(Collectors.toList());
        
        return new UserReportPageResult(content, totalElements);
    }
    
    @Override
    public List<UserReport> findByReportedUserId(UUID reportedUserId) {
        String sql = "SELECT * FROM user_reports WHERE reported_user_id = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(reportedUserId));
        return rows.stream().map(this::mapToUserReport).collect(Collectors.toList());
    }
    
    @Override
    public List<UserReport> findByReportedUserId(UUID reportedUserId, int page, int size) {
        String sql = "SELECT * FROM user_reports WHERE reported_user_id = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(reportedUserId)).stream()
            .map(this::mapToUserReport)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserReport> findByReporterUserId(UUID reporterUserId) {
        String sql = "SELECT * FROM user_reports WHERE reporter_user_id = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(reporterUserId));
        return rows.stream().map(this::mapToUserReport).collect(Collectors.toList());
    }
    
    @Override
    public List<UserReport> findByReporterUserId(UUID reporterUserId, int page, int size) {
        String sql = "SELECT * FROM user_reports WHERE reporter_user_id = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(reporterUserId)).stream()
            .map(this::mapToUserReport)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserReport> findByReason(String reason) {
        String sql = "SELECT * FROM user_reports WHERE reason = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, reason);
        return rows.stream().map(this::mapToUserReport).collect(Collectors.toList());
    }
    
    @Override
    public long countByReportedUserId(UUID reportedUserId) {
        String sql = "SELECT COUNT(*) as count FROM user_reports WHERE reported_user_id = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(reportedUserId));
    }
    
    @Override
    public long countByReporterUserId(UUID reporterUserId) {
        String sql = "SELECT COUNT(*) as count FROM user_reports WHERE reporter_user_id = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(reporterUserId));
    }
    
    @Override
    public List<UserReport> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM user_reports WHERE created_at >= ? AND created_at <= ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToUserReport).collect(Collectors.toList());
    }
    
    @Override
    public boolean hasReportedUser(UUID reportedUserId, UUID reporterUserId) {
        String sql = "SELECT 1 FROM user_reports WHERE reported_user_id = ? AND reporter_user_id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(reportedUserId), EntityMapper.uuidToString(reporterUserId));
        return row != null;
    }
    
    @Override
    public void deleteReport(UUID id) {
        if (!d1Util.exists("user_reports", "id = ?", EntityMapper.uuidToString(id))) {
            logger.error("删除举报失败：举报记录不存在，report_id: {}", id);
            throw new RuntimeException("举报记录不存在");
        }
        d1Util.deleteById("user_reports", "id", EntityMapper.uuidToString(id));
        logger.info("用户举报记录删除成功，report_id: {}", id);
    }
    
    @Override
    public List<Object[]> findMostReportedUsers() {
        String sql = "SELECT reported_user_id, COUNT(*) as report_count FROM user_reports GROUP BY reported_user_id ORDER BY report_count DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(row -> new Object[]{
            EntityMapper.getUUID(row, "reported_user_id"),
            row.get("report_count")
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<Object[]> findMostReportedUsers(int page, int size) {
        String sql = "SELECT reported_user_id, COUNT(*) as report_count FROM user_reports GROUP BY reported_user_id ORDER BY report_count DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(row -> new Object[]{
                EntityMapper.getUUID(row, "reported_user_id"),
                row.get("report_count")
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 保存用户举报
     */
    private UserReport saveUserReport(UserReport userReport) {
        if (userReport.getId() == null) {
            // 插入新记录
            userReport.setId(UUID.randomUUID());
            userReport.setCreatedAt(OffsetDateTime.now());
            userReport.setUpdatedAt(OffsetDateTime.now());
            Map<String, Object> data = userReportToMap(userReport);
            d1Util.insert("user_reports", data);
            return userReport;
        } else {
            // 更新记录
            userReport.preUpdate();
            Map<String, Object> data = userReportToMap(userReport);
            d1Util.updateById("user_reports", data, "id", EntityMapper.uuidToString(userReport.getId()));
            return userReport;
        }
    }
    
    /**
     * 将 Map 转换为 UserReport 实体
     */
    private UserReport mapToUserReport(Map<String, Object> row) {
        UserReport userReport = new UserReport();
        userReport.setId(EntityMapper.getUUID(row, "id"));
        userReport.setReportedUserId(EntityMapper.getUUID(row, "reported_user_id"));
        userReport.setReporterUserId(EntityMapper.getUUID(row, "reporter_user_id"));
        userReport.setReason(EntityMapper.getString(row, "reason"));
        userReport.setRemark(EntityMapper.getString(row, "remark"));
        userReport.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        userReport.setUpdatedAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
        return userReport;
    }
    
    /**
     * 将 UserReport 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> userReportToMap(UserReport userReport) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", userReport.getId());
        EntityMapper.putIfNotNull(data, "reported_user_id", userReport.getReportedUserId());
        EntityMapper.putIfNotNull(data, "reporter_user_id", userReport.getReporterUserId());
        EntityMapper.putIfNotNull(data, "reason", userReport.getReason());
        EntityMapper.putIfNotNull(data, "remark", userReport.getRemark());
        EntityMapper.putIfNotNull(data, "created_at", userReport.getCreatedAt());
        EntityMapper.putIfNotNull(data, "updated_at", userReport.getUpdatedAt());
        return data;
    }
}

