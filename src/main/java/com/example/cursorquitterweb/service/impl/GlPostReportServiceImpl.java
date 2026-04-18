package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.PostReport;
import com.example.cursorquitterweb.service.GlPostReportService;
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
 * 海外帖子举报服务实现类
 * 复用共享表 post_reports，但帖子存在性校验走 posts_gl
 */
@Service
public class GlPostReportServiceImpl implements GlPostReportService {

    private static final Logger logger = LogUtil.getLogger(GlPostReportServiceImpl.class);

    @Autowired
    private CloudflareD1Util d1Util;

    @Override
    public PostReport createReport(UUID reportedPostId, String reportReason, String reportNotes, UUID reporterUserId) {
        if (!d1Util.exists("posts_gl", "post_id = ? AND is_deleted = ?", EntityMapper.uuidToString(reportedPostId), false)) {
            logger.error("海外帖子举报失败：帖子不存在，post_id: {}", reportedPostId);
            throw new RuntimeException("举报失败：帖子不存在");
        }
        if (hasUserReportedPost(reportedPostId, reporterUserId)) {
            logger.warn("海外用户已经举报过该帖子，post_id: {}, user_id: {}", reportedPostId, reporterUserId);
            throw new RuntimeException("您已经举报过该帖子");
        }
        PostReport postReport = new PostReport(reportedPostId, reportReason, reportNotes, reporterUserId);
        return savePostReport(postReport);
    }

    @Override
    public Optional<PostReport> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("post_reports", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToPostReport(row)) : Optional.empty();
    }

    @Override
    public List<PostReport> findByReportedPostId(UUID reportedPostId) {
        String sql = "SELECT * FROM post_reports WHERE reported_post_id = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, EntityMapper.uuidToString(reportedPostId)).stream()
            .map(this::mapToPostReport)
            .collect(Collectors.toList());
    }

    @Override
    public long countByReportedPostId(UUID reportedPostId) {
        String sql = "SELECT COUNT(*) as count FROM post_reports WHERE reported_post_id = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(reportedPostId));
    }

    @Override
    public boolean hasUserReportedPost(UUID reportedPostId, UUID reporterUserId) {
        String sql = "SELECT 1 FROM post_reports WHERE reported_post_id = ? AND reporter_user_id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(reportedPostId), EntityMapper.uuidToString(reporterUserId));
        return row != null;
    }

    private PostReport savePostReport(PostReport postReport) {
        postReport.setId(UUID.randomUUID());
        postReport.setCreatedAt(OffsetDateTime.now());
        d1Util.insert("post_reports", postReportToMap(postReport));
        return postReport;
    }

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
