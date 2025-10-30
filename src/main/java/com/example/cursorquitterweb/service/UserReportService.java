package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.UserReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
     * 获取所有举报记录（分页）
     */
    Page<UserReport> getAllReports(Pageable pageable);
    
    /**
     * 根据被举报用户ID查找举报记录
     */
    List<UserReport> findByReportedUserId(UUID reportedUserId);
    
    /**
     * 根据被举报用户ID分页查找举报记录
     */
    Page<UserReport> findByReportedUserId(UUID reportedUserId, Pageable pageable);
    
    /**
     * 根据举报人ID查找举报记录
     */
    List<UserReport> findByReporterUserId(UUID reporterUserId);
    
    /**
     * 根据举报人ID分页查找举报记录
     */
    Page<UserReport> findByReporterUserId(UUID reporterUserId, Pageable pageable);
    
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
     * 查询被举报次数最多的用户（分页）
     */
    Page<Object[]> findMostReportedUsers(Pageable pageable);
}

