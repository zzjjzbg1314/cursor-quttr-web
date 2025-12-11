package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.UserReport;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户举报服务接口
 */
public interface UserReportService {
    
    /**
     * 创建举报
     */
    UserReport createReport(UUID reportedUserId, UUID reporterUserId, String reason, String remark);
    
    /**
     * 根据ID查找举报记录
     */
    Optional<UserReport> findById(UUID id);
    
    /**
     * 获取所有举报记录（分页，已移除 Spring Data Page，返回 List）
     */
    List<UserReport> getAllReports(int page, int size);
    
    /**
     * 获取所有举报记录（分页，使用窗口函数一次性获取数据和总数）
     * 性能优化：使用窗口函数在单次查询中同时获取数据和总数，避免2次数据库查询
     */
    com.example.cursorquitterweb.dto.UserReportPageResult getAllReportsWithCount(int page, int size);
    
    /**
     * 根据被举报用户ID查找举报记录
     */
    List<UserReport> findByReportedUserId(UUID reportedUserId);
    
    /**
     * 根据被举报用户ID分页查找举报记录（已移除 Spring Data Page，返回 List）
     */
    List<UserReport> findByReportedUserId(UUID reportedUserId, int page, int size);
    
    /**
     * 根据举报人ID查找举报记录
     */
    List<UserReport> findByReporterUserId(UUID reporterUserId);
    
    /**
     * 根据举报人ID分页查找举报记录（已移除 Spring Data Page，返回 List）
     */
    List<UserReport> findByReporterUserId(UUID reporterUserId, int page, int size);
    
    /**
     * 根据举报原因查找举报记录
     */
    List<UserReport> findByReason(String reason);
    
    /**
     * 统计某个用户被举报的次数
     */
    long countByReportedUserId(UUID reportedUserId);
    
    /**
     * 统计某个用户举报的次数
     */
    long countByReporterUserId(UUID reporterUserId);
    
    /**
     * 根据时间范围查找举报记录
     */
    List<UserReport> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 检查举报人是否已经举报过该用户
     */
    boolean hasReportedUser(UUID reportedUserId, UUID reporterUserId);
    
    /**
     * 删除举报记录
     */
    void deleteReport(UUID id);
    
    /**
     * 查询被举报次数最多的用户
     */
    List<Object[]> findMostReportedUsers();
    
    /**
     * 查询被举报次数最多的用户（分页，已移除 Spring Data Page，返回 List）
     */
    List<Object[]> findMostReportedUsers(int page, int size);
}

