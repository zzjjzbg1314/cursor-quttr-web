package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.CommentReport;
import com.example.cursorquitterweb.service.GlCommentReportService;
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
 * 海外评论举报服务实现类
 * 使用共享表 comment_reports，评论存在性校验走 comments_gl
 */
@Service
public class GlCommentReportServiceImpl implements GlCommentReportService {

    private static final Logger logger = LogUtil.getLogger(GlCommentReportServiceImpl.class);

    @Autowired
    private CloudflareD1Util d1Util;

    @Override
    public CommentReport createReport(UUID reportedCommentId, String reportReason, String reportNotes, UUID reporterUserId) {
        if (!d1Util.exists("comments_gl", "comment_id = ? AND is_deleted = ?", EntityMapper.uuidToString(reportedCommentId), false)) {
            logger.error("海外评论举报失败：评论不存在，comment_id: {}", reportedCommentId);
            throw new RuntimeException("举报失败：评论不存在");
        }
        if (hasUserReportedComment(reportedCommentId, reporterUserId)) {
            logger.warn("海外用户已经举报过该评论，comment_id: {}, user_id: {}", reportedCommentId, reporterUserId);
            throw new RuntimeException("您已经举报过该评论");
        }
        CommentReport commentReport = new CommentReport(reportedCommentId, reportReason, reportNotes, reporterUserId);
        return saveCommentReport(commentReport);
    }

    @Override
    public Optional<CommentReport> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("comment_reports", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToCommentReport(row)) : Optional.empty();
    }

    @Override
    public List<CommentReport> findByReportedCommentId(UUID reportedCommentId) {
        String sql = "SELECT * FROM comment_reports WHERE reported_comment_id = ? ORDER BY created_at DESC";
        return d1Util.queryList(sql, EntityMapper.uuidToString(reportedCommentId)).stream()
            .map(this::mapToCommentReport)
            .collect(Collectors.toList());
    }

    @Override
    public long countByReportedCommentId(UUID reportedCommentId) {
        String sql = "SELECT COUNT(*) as count FROM comment_reports WHERE reported_comment_id = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(reportedCommentId));
    }

    @Override
    public boolean hasUserReportedComment(UUID reportedCommentId, UUID reporterUserId) {
        String sql = "SELECT 1 FROM comment_reports WHERE reported_comment_id = ? AND reporter_user_id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(reportedCommentId), EntityMapper.uuidToString(reporterUserId));
        return row != null;
    }

    private CommentReport saveCommentReport(CommentReport commentReport) {
        commentReport.setId(UUID.randomUUID());
        commentReport.setCreatedAt(OffsetDateTime.now());
        d1Util.insert("comment_reports", commentReportToMap(commentReport));
        return commentReport;
    }

    private CommentReport mapToCommentReport(Map<String, Object> row) {
        CommentReport commentReport = new CommentReport();
        commentReport.setId(EntityMapper.getUUID(row, "id"));
        commentReport.setReportedCommentId(EntityMapper.getUUID(row, "reported_comment_id"));
        commentReport.setReportReason(EntityMapper.getString(row, "report_reason"));
        commentReport.setReportNotes(EntityMapper.getString(row, "report_notes"));
        commentReport.setReporterUserId(EntityMapper.getUUID(row, "reporter_user_id"));
        commentReport.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        return commentReport;
    }

    private Map<String, Object> commentReportToMap(CommentReport commentReport) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", commentReport.getId());
        EntityMapper.putIfNotNull(data, "reported_comment_id", commentReport.getReportedCommentId());
        EntityMapper.putIfNotNull(data, "report_reason", commentReport.getReportReason());
        EntityMapper.putIfNotNull(data, "report_notes", commentReport.getReportNotes());
        EntityMapper.putIfNotNull(data, "reporter_user_id", commentReport.getReporterUserId());
        EntityMapper.putIfNotNull(data, "created_at", commentReport.getCreatedAt());
        return data;
    }
}
