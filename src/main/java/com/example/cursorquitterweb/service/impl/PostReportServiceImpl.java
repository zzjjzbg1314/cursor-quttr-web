package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.PostReport;
import com.example.cursorquitterweb.repository.PostReportRepository;
import com.example.cursorquitterweb.repository.PostRepository;
import com.example.cursorquitterweb.service.PostReportService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 帖子举报服务实现类
 */
@Service
@Transactional
public class PostReportServiceImpl implements PostReportService {
    
    private static final Logger logger = LogUtil.getLogger(PostReportServiceImpl.class);
    
    @Autowired
    private PostReportRepository postReportRepository;
    
    @Autowired
    private PostRepository postRepository;
    
    @Override
    public PostReport createReport(UUID reportedPostId, String reportReason, String reportNotes, UUID reporterUserId) {
        // 验证帖子是否存在
        if (!postRepository.existsById(reportedPostId)) {
            logger.error("举报失败：帖子不存在，post_id: {}", reportedPostId);
            throw new RuntimeException("举报失败：帖子不存在");
        }
        
        // 检查用户是否已经举报过该帖子
        if (postReportRepository.existsByReportedPostIdAndReporterUserId(reportedPostId, reporterUserId)) {
            logger.warn("用户已经举报过该帖子，post_id: {}, user_id: {}", reportedPostId, reporterUserId);
            throw new RuntimeException("您已经举报过该帖子");
        }
        
        PostReport postReport = new PostReport(reportedPostId, reportReason, reportNotes, reporterUserId);
        PostReport savedReport = postReportRepository.save(postReport);
        
        logger.info("举报创建成功，report_id: {}, post_id: {}, user_id: {}, reason: {}", 
                    savedReport.getId(), reportedPostId, reporterUserId, reportReason);
        
        return savedReport;
    }
    
    @Override
    public Optional<PostReport> findById(UUID id) {
        return postReportRepository.findById(id);
    }
    
    @Override
    public Page<PostReport> getAllReports(Pageable pageable) {
        return postReportRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
    
    @Override
    public List<PostReport> findByReportedPostId(UUID reportedPostId) {
        return postReportRepository.findByReportedPostIdOrderByCreatedAtDesc(reportedPostId);
    }
    
    @Override
    public Page<PostReport> findByReportedPostId(UUID reportedPostId, Pageable pageable) {
        return postReportRepository.findByReportedPostIdOrderByCreatedAtDesc(reportedPostId, pageable);
    }
    
    @Override
    public List<PostReport> findByReporterUserId(UUID reporterUserId) {
        return postReportRepository.findByReporterUserIdOrderByCreatedAtDesc(reporterUserId);
    }
    
    @Override
    public Page<PostReport> findByReporterUserId(UUID reporterUserId, Pageable pageable) {
        return postReportRepository.findByReporterUserIdOrderByCreatedAtDesc(reporterUserId, pageable);
    }
    
    @Override
    public List<PostReport> findByReportReason(String reportReason) {
        return postReportRepository.findByReportReasonOrderByCreatedAtDesc(reportReason);
    }
    
    @Override
    public long countByReportedPostId(UUID reportedPostId) {
        return postReportRepository.countByReportedPostId(reportedPostId);
    }
    
    @Override
    public long countByReporterUserId(UUID reporterUserId) {
        return postReportRepository.countByReporterUserId(reporterUserId);
    }
    
    @Override
    public List<PostReport> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return postReportRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startTime, endTime);
    }
    
    @Override
    public boolean hasUserReportedPost(UUID reportedPostId, UUID reporterUserId) {
        return postReportRepository.existsByReportedPostIdAndReporterUserId(reportedPostId, reporterUserId);
    }
    
    @Override
    public void deleteReport(UUID id) {
        if (!postReportRepository.existsById(id)) {
            logger.error("删除举报失败：举报记录不存在，report_id: {}", id);
            throw new RuntimeException("举报记录不存在");
        }
        postReportRepository.deleteById(id);
        logger.info("举报记录删除成功，report_id: {}", id);
    }
    
    @Override
    public List<Object[]> findMostReportedPosts() {
        return postReportRepository.findMostReportedPosts();
    }
    
    @Override
    public Page<Object[]> findMostReportedPosts(Pageable pageable) {
        return postReportRepository.findMostReportedPosts(pageable);
    }
}

