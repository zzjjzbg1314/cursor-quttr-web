package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.PostReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 海外帖子举报服务接口
 */
public interface GlPostReportService {

    PostReport createReport(UUID reportedPostId, String reportReason, String reportNotes, UUID reporterUserId);

    Optional<PostReport> findById(UUID id);

    List<PostReport> findByReportedPostId(UUID reportedPostId);

    long countByReportedPostId(UUID reportedPostId);

    boolean hasUserReportedPost(UUID reportedPostId, UUID reporterUserId);
}
