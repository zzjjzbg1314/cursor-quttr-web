package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.UserReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 用户举报数据访问接口
 */
@Repository
public interface UserReportRepository extends JpaRepository<UserReport, UUID> {
    
    /**
     * 根据被举报用户ID查找所有举报记录
     */
    List<UserReport> findByReportedUserIdOrderByCreatedAtDesc(UUID reportedUserId);
    
    /**
     * 根据被举报用户ID分页查找举报记录
     */
    Page<UserReport> findByReportedUserIdOrderByCreatedAtDesc(UUID reportedUserId, Pageable pageable);
    
    /**
     * 根据举报人ID查找所有举报记录
     */
    List<UserReport> findByReporterUserIdOrderByCreatedAtDesc(UUID reporterUserId);
    
    /**
     * 根据举报人ID分页查找举报记录
     */
    Page<UserReport> findByReporterUserIdOrderByCreatedAtDesc(UUID reporterUserId, Pageable pageable);
    
    /**
     * 根据举报原因查找举报记录
     */
    List<UserReport> findByReasonOrderByCreatedAtDesc(String reason);
    
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
    List<UserReport> findByCreatedAtBetweenOrderByCreatedAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 检查举报人是否已经举报过该用户
     */
    boolean existsByReportedUserIdAndReporterUserId(UUID reportedUserId, UUID reporterUserId);
    
    /**
     * 查找所有举报记录（分页）
     */
    Page<UserReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * 查询被举报次数最多的用户
     */
    @Query("SELECT ur.reportedUserId, COUNT(ur.id) as reportCount " +
           "FROM UserReport ur " +
           "GROUP BY ur.reportedUserId " +
           "ORDER BY reportCount DESC")
    List<Object[]> findMostReportedUsers();
    
    /**
     * 查询被举报次数最多的用户（分页）
     */
    @Query("SELECT ur.reportedUserId, COUNT(ur.id) as reportCount " +
           "FROM UserReport ur " +
           "GROUP BY ur.reportedUserId " +
           "ORDER BY reportCount DESC")
    Page<Object[]> findMostReportedUsers(Pageable pageable);
}

