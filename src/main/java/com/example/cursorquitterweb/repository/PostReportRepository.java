package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.PostReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 帖子举报数据访问接口
 */
@Repository
public interface PostReportRepository extends JpaRepository<PostReport, UUID> {
    
    /**
     * 根据被举报的帖子ID查找所有举报记录
     */
    List<PostReport> findByReportedPostIdOrderByCreatedAtDesc(UUID reportedPostId);
    
    /**
     * 根据被举报的帖子ID分页查找举报记录
     */
    Page<PostReport> findByReportedPostIdOrderByCreatedAtDesc(UUID reportedPostId, Pageable pageable);
    
    /**
     * 根据举报人ID查找所有举报记录
     */
    List<PostReport> findByReporterUserIdOrderByCreatedAtDesc(UUID reporterUserId);
    
    /**
     * 根据举报人ID分页查找举报记录
     */
    Page<PostReport> findByReporterUserIdOrderByCreatedAtDesc(UUID reporterUserId, Pageable pageable);
    
    /**
     * 根据举报原因查找举报记录
     */
    List<PostReport> findByReportReasonOrderByCreatedAtDesc(String reportReason);
    
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
    List<PostReport> findByCreatedAtBetweenOrderByCreatedAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 检查用户是否已经举报过该帖子
     */
    boolean existsByReportedPostIdAndReporterUserId(UUID reportedPostId, UUID reporterUserId);
    
    /**
     * 查找所有举报记录（分页）
     */
    Page<PostReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * 查询被举报次数最多的帖子
     */
    @Query("SELECT pr.reportedPostId, COUNT(pr.id) as reportCount " +
           "FROM PostReport pr " +
           "GROUP BY pr.reportedPostId " +
           "ORDER BY reportCount DESC")
    List<Object[]> findMostReportedPosts();
    
    /**
     * 查询被举报次数最多的帖子（分页）
     */
    @Query("SELECT pr.reportedPostId, COUNT(pr.id) as reportCount " +
           "FROM PostReport pr " +
           "GROUP BY pr.reportedPostId " +
           "ORDER BY reportCount DESC")
    Page<Object[]> findMostReportedPosts(Pageable pageable);
}

