package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.PostReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 帖子举报服务接口
 */
public interface PostReportService {
    
    /**
     * 创建举报
     */
    PostReport createReport(UUID reportedPostId, String reportReason, String reportNotes, UUID reporterUserId);
    
    /**
     * 根据ID查找举报记录
     */
    Optional<PostReport> findById(UUID id);
    
    /**
     * 获取所有举报记录（分页）
     */
    Page<PostReport> getAllReports(Pageable pageable);
    
    /**
     * 根据被举报的帖子ID查找举报记录
     */
    List<PostReport> findByReportedPostId(UUID reportedPostId);
    
    /**
     * 根据被举报的帖子ID分页查找举报记录
     */
    Page<PostReport> findByReportedPostId(UUID reportedPostId, Pageable pageable);
    
    /**
     * 根据举报人ID查找举报记录
     */
    List<PostReport> findByReporterUserId(UUID reporterUserId);
    
    /**
     * 根据举报人ID分页查找举报记录
     */
    Page<PostReport> findByReporterUserId(UUID reporterUserId, Pageable pageable);
    
    /**
     * 根据举报原因查找举报记录
     */
    List<PostReport> findByReportReason(String reportReason);
    
    /**
     * 统计某个帖子被举报的次数
     */
    long countByReportedPostId(UUID reportedPostId);
    
    /**
     * 统计某个用户举报的次数
     */
    long countByReporterUserId(UUID reporterUserId);
    
    /**
     * 根据时间范围查找举报记录
     */
    List<PostReport> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 检查用户是否已经举报过该帖子
     */
    boolean hasUserReportedPost(UUID reportedPostId, UUID reporterUserId);
    
    /**
     * 删除举报记录
     */
    void deleteReport(UUID id);
    
    /**
     * 查询被举报次数最多的帖子
     */
    List<Object[]> findMostReportedPosts();
    
    /**
     * 查询被举报次数最多的帖子（分页）
     */
    Page<Object[]> findMostReportedPosts(Pageable pageable);
}

