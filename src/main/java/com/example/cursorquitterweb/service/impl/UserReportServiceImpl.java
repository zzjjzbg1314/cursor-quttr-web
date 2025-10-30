package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.UserReport;
import com.example.cursorquitterweb.repository.UserReportRepository;
import com.example.cursorquitterweb.repository.UserRepository;
import com.example.cursorquitterweb.service.UserReportService;
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
 * 用户举报服务实现类
 */
@Service
@Transactional
public class UserReportServiceImpl implements UserReportService {
    
    private static final Logger logger = LogUtil.getLogger(UserReportServiceImpl.class);
    
    @Autowired
    private UserReportRepository userReportRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public UserReport createReport(UUID reportedUserId, UUID reporterUserId, String reason, String remark) {
        // 验证被举报的用户是否存在
        if (!userRepository.existsById(reportedUserId)) {
            logger.error("举报失败：被举报的用户不存在，user_id: {}", reportedUserId);
            throw new RuntimeException("举报失败：被举报的用户不存在");
        }
        
        // 验证举报人是否存在
        if (!userRepository.existsById(reporterUserId)) {
            logger.error("举报失败：举报人不存在，user_id: {}", reporterUserId);
            throw new RuntimeException("举报失败：举报人不存在");
        }
        
        // 不允许举报自己
        if (reportedUserId.equals(reporterUserId)) {
            logger.warn("举报失败：不能举报自己，user_id: {}", reporterUserId);
            throw new RuntimeException("不能举报自己");
        }
        
        // 检查是否已经举报过该用户
        if (userReportRepository.existsByReportedUserIdAndReporterUserId(reportedUserId, reporterUserId)) {
            logger.warn("用户已经举报过该用户，reported_user_id: {}, reporter_user_id: {}", reportedUserId, reporterUserId);
            throw new RuntimeException("您已经举报过该用户");
        }
        
        UserReport userReport = new UserReport(reportedUserId, reporterUserId, reason, remark);
        UserReport savedReport = userReportRepository.save(userReport);
        
        logger.info("用户举报创建成功，report_id: {}, reported_user_id: {}, reporter_user_id: {}, reason: {}", 
                    savedReport.getId(), reportedUserId, reporterUserId, reason);
        
        return savedReport;
    }
    
    @Override
    public Optional<UserReport> findById(UUID id) {
        return userReportRepository.findById(id);
    }
    
    @Override
    public Page<UserReport> getAllReports(Pageable pageable) {
        return userReportRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
    
    @Override
    public List<UserReport> findByReportedUserId(UUID reportedUserId) {
        return userReportRepository.findByReportedUserIdOrderByCreatedAtDesc(reportedUserId);
    }
    
    @Override
    public Page<UserReport> findByReportedUserId(UUID reportedUserId, Pageable pageable) {
        return userReportRepository.findByReportedUserIdOrderByCreatedAtDesc(reportedUserId, pageable);
    }
    
    @Override
    public List<UserReport> findByReporterUserId(UUID reporterUserId) {
        return userReportRepository.findByReporterUserIdOrderByCreatedAtDesc(reporterUserId);
    }
    
    @Override
    public Page<UserReport> findByReporterUserId(UUID reporterUserId, Pageable pageable) {
        return userReportRepository.findByReporterUserIdOrderByCreatedAtDesc(reporterUserId, pageable);
    }
    
    @Override
    public List<UserReport> findByReason(String reason) {
        return userReportRepository.findByReasonOrderByCreatedAtDesc(reason);
    }
    
    @Override
    public long countByReportedUserId(UUID reportedUserId) {
        return userReportRepository.countByReportedUserId(reportedUserId);
    }
    
    @Override
    public long countByReporterUserId(UUID reporterUserId) {
        return userReportRepository.countByReporterUserId(reporterUserId);
    }
    
    @Override
    public List<UserReport> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        return userReportRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startTime, endTime);
    }
    
    @Override
    public boolean hasReportedUser(UUID reportedUserId, UUID reporterUserId) {
        return userReportRepository.existsByReportedUserIdAndReporterUserId(reportedUserId, reporterUserId);
    }
    
    @Override
    public void deleteReport(UUID id) {
        if (!userReportRepository.existsById(id)) {
            logger.error("删除举报失败：举报记录不存在，report_id: {}", id);
            throw new RuntimeException("举报记录不存在");
        }
        userReportRepository.deleteById(id);
        logger.info("用户举报记录删除成功，report_id: {}", id);
    }
    
    @Override
    public List<Object[]> findMostReportedUsers() {
        return userReportRepository.findMostReportedUsers();
    }
    
    @Override
    public Page<Object[]> findMostReportedUsers(Pageable pageable) {
        return userReportRepository.findMostReportedUsers(pageable);
    }
}

