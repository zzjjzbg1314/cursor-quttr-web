package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.CommentReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 海外评论举报服务接口
 */
public interface GlCommentReportService {

    CommentReport createReport(UUID reportedCommentId, String reportReason, String reportNotes, UUID reporterUserId);

    Optional<CommentReport> findById(UUID id);

    List<CommentReport> findByReportedCommentId(UUID reportedCommentId);

    long countByReportedCommentId(UUID reportedCommentId);

    boolean hasUserReportedComment(UUID reportedCommentId, UUID reporterUserId);
}
