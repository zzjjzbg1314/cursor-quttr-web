package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.PostReport;
import com.example.cursorquitterweb.service.PostReportService;
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
 * 帖子举报服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class PostReportServiceImpl implements PostReportService {
    
    private static final Logger logger = LogUtil.getLogger(PostReportServiceImpl.class);
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public PostReport createReport(UUID reportedPostId, String reportReason, String reportNotes, UUID reporterUserId) {
        // 验证帖子是否存在
        if (!d1Util.exists("posts", "post_id = ? AND is_deleted = ?", EntityMapper.uuidToString(reportedPostId), false)) {
            logger.error("举报失败：帖子不存在，post_id: {}", reportedPostId);
            throw new RuntimeException("举报失败：帖子不存在");
        }
        
        // 检查用户是否已经举报过该帖子
        if (hasUserReportedPost(reportedPostId, reporterUserId)) {
            logger.warn("用户已经举报过该帖子，post_id: {}, user_id: {}", reportedPostId, reporterUserId);
            throw new RuntimeException("您已经举报过该帖子");
        }
        
        PostReport postReport = new PostReport(reportedPostId, reportReason, reportNotes, reporterUserId);
        PostReport savedReport = savePostReport(postReport);
        
        logger.info("举报创建成功，report_id: {}, post_id: {}, user_id: {}, reason: {}", 
                    savedReport.getId(), reportedPostId, reporterUserId, reportReason);
        
        return savedReport;
    }
    
    @Override
    public Optional<PostReport> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("post_reports", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToPostReport(row)) : Optional.empty();
    }
    
    @Override
    public List<PostReport> getAllReports(int page, int size) {
        String sql = "SELECT * FROM post_reports ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToPostReport)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<PostReport> findByReportedPostId(UUID reportedPostId) {
        String sql = "SELECT * FROM post_reports WHERE reported_post_id = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(reportedPostId));
        return rows.stream().map(this::mapToPostReport).collect(Collectors.toList());
    }
    
    @Override
    public List<PostReport> findByReportedPostId(UUID reportedPostId, int page, int size) {
        String sql = "SELECT * FROM post_reports WHERE reported_post_id = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(reportedPostId)).stream()
            .map(this::mapToPostReport)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<PostReport> findByReporterUserId(UUID reporterUserId) {
        String sql = "SELECT * FROM post_reports WHERE reporter_user_id = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(reporterUserId));
        return rows.stream().map(this::mapToPostReport).collect(Collectors.toList());
    }
    
    @Override
    public List<PostReport> findByReporterUserId(UUID reporterUserId, int page, int size) {
        String sql = "SELECT * FROM post_reports WHERE reporter_user_id = ? ORDER BY created_at DESC";
        return d1Util.queryPage(sql, page + 1, size, EntityMapper.uuidToString(reporterUserId)).stream()
            .map(this::mapToPostReport)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<PostReport> findByReportReason(String reportReason) {
        String sql = "SELECT * FROM post_reports WHERE report_reason = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, reportReason);
        return rows.stream().map(this::mapToPostReport).collect(Collectors.toList());
    }
    
    @Override
    public long countByReportedPostId(UUID reportedPostId) {
        String sql = "SELECT COUNT(*) as count FROM post_reports WHERE reported_post_id = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(reportedPostId));
    }
    
    @Override
    public long countByReporterUserId(UUID reporterUserId) {
        String sql = "SELECT COUNT(*) as count FROM post_reports WHERE reporter_user_id = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(reporterUserId));
    }
    
    @Override
    public List<PostReport> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM post_reports WHERE created_at >= ? AND created_at <= ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToPostReport).collect(Collectors.toList());
    }
    
    @Override
    public boolean hasUserReportedPost(UUID reportedPostId, UUID reporterUserId) {
        String sql = "SELECT 1 FROM post_reports WHERE reported_post_id = ? AND reporter_user_id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(reportedPostId), EntityMapper.uuidToString(reporterUserId));
        return row != null;
    }
    
    @Override
    public void deleteReport(UUID id) {
        if (!d1Util.exists("post_reports", "id = ?", EntityMapper.uuidToString(id))) {
            logger.error("删除举报失败：举报记录不存在，report_id: {}", id);
            throw new RuntimeException("举报记录不存在");
        }
        d1Util.deleteById("post_reports", "id", EntityMapper.uuidToString(id));
        logger.info("举报记录删除成功，report_id: {}", id);
    }
    
    @Override
    public List<Object[]> findMostReportedPosts() {
        String sql = "SELECT reported_post_id, COUNT(*) as report_count FROM post_reports GROUP BY reported_post_id ORDER BY report_count DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(row -> new Object[]{
            EntityMapper.getUUID(row, "reported_post_id"),
            row.get("report_count")
        }).collect(Collectors.toList());
    }
    
    @Override
    public List<Object[]> findMostReportedPosts(int page, int size) {
        String sql = "SELECT reported_post_id, COUNT(*) as report_count FROM post_reports GROUP BY reported_post_id ORDER BY report_count DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(row -> new Object[]{
                EntityMapper.getUUID(row, "reported_post_id"),
                row.get("report_count")
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 保存帖子举报
     */
    private PostReport savePostReport(PostReport postReport) {
        if (postReport.getId() == null) {
            // 插入新记录
            postReport.setId(UUID.randomUUID());
            postReport.setCreatedAt(OffsetDateTime.now());
            Map<String, Object> data = postReportToMap(postReport);
            d1Util.insert("post_reports", data);
            return postReport;
        } else {
            // 更新记录（通常不需要更新）
            Map<String, Object> data = postReportToMap(postReport);
            d1Util.updateById("post_reports", data, "id", EntityMapper.uuidToString(postReport.getId()));
            return postReport;
        }
    }
    
    /**
     * 将 Map 转换为 PostReport 实体
     */
    private PostReport mapToPostReport(Map<String, Object> row) {
        PostReport postReport = new PostReport();
        postReport.setId(EntityMapper.getUUID(row, "id"));
        postReport.setReportedPostId(EntityMapper.getUUID(row, "reported_post_id"));
        postReport.setReportReason(EntityMapper.getString(row, "report_reason"));
        postReport.setReportNotes(EntityMapper.getString(row, "report_notes"));
        postReport.setReporterUserId(EntityMapper.getUUID(row, "reporter_user_id"));
        postReport.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        return postReport;
    }
    
    /**
     * 将 PostReport 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> postReportToMap(PostReport postReport) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", postReport.getId());
        EntityMapper.putIfNotNull(data, "reported_post_id", postReport.getReportedPostId());
        EntityMapper.putIfNotNull(data, "report_reason", postReport.getReportReason());
        EntityMapper.putIfNotNull(data, "report_notes", postReport.getReportNotes());
        EntityMapper.putIfNotNull(data, "reporter_user_id", postReport.getReporterUserId());
        EntityMapper.putIfNotNull(data, "created_at", postReport.getCreatedAt());
        return data;
    }
}

