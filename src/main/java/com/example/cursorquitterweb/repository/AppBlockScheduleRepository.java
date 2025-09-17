package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.AppBlockSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 应用屏蔽计划数据访问接口
 */
@Repository
public interface AppBlockScheduleRepository extends JpaRepository<AppBlockSchedule, UUID> {
    
    /**
     * 根据标题模糊查询屏蔽计划
     */
    List<AppBlockSchedule> findByTitleContainingIgnoreCase(String title);
    
    /**
     * 根据原因模糊查询屏蔽计划
     */
    List<AppBlockSchedule> findByReasonContainingIgnoreCase(String reason);
    
    /**
     * 根据日期查询屏蔽计划
     */
    @Query("SELECT a FROM AppBlockSchedule a WHERE a.days LIKE %:day%")
    List<AppBlockSchedule> findByDaysContaining(@Param("day") String day);
    
    /**
     * 根据时间段查询屏蔽计划
     */
    @Query("SELECT a FROM AppBlockSchedule a WHERE a.time LIKE %:timeRange%")
    List<AppBlockSchedule> findByTimeContaining(@Param("timeRange") String timeRange);
    
    /**
     * 根据标题和原因组合查询
     */
    @Query("SELECT a FROM AppBlockSchedule a WHERE " +
           "(:title IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
           "(:reason IS NULL OR LOWER(a.reason) LIKE LOWER(CONCAT('%', :reason, '%')))")
    List<AppBlockSchedule> findByTitleAndReason(@Param("title") String title, @Param("reason") String reason);
    
    /**
     * 统计指定原因的屏蔽计划数量
     */
    @Query("SELECT COUNT(a) FROM AppBlockSchedule a WHERE a.reason = :reason")
    long countByReason(@Param("reason") String reason);
    
    /**
     * 获取所有不同的原因列表
     */
    @Query("SELECT DISTINCT a.reason FROM AppBlockSchedule a WHERE a.reason IS NOT NULL")
    List<String> findDistinctReasons();
    
    /**
     * 根据ID列表批量查询
     */
    List<AppBlockSchedule> findByIdIn(List<UUID> ids);
    
    /**
     * 获取所有屏蔽计划，按创建时间正序排列
     */
    @Query("SELECT a FROM AppBlockSchedule a ORDER BY a.createdAt ASC")
    List<AppBlockSchedule> findAllOrderByCreatedAtAsc();
}
