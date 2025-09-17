package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.AppBlockSchedule;
import com.example.cursorquitterweb.dto.CreateAppBlockScheduleRequest;
import com.example.cursorquitterweb.dto.UpdateAppBlockScheduleRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 应用屏蔽计划服务接口
 */
public interface AppBlockScheduleService {
    
    /**
     * 根据ID查找屏蔽计划
     */
    Optional<AppBlockSchedule> findById(UUID id);
    
    /**
     * 保存屏蔽计划
     */
    AppBlockSchedule save(AppBlockSchedule appBlockSchedule);
    
    /**
     * 创建新的屏蔽计划
     */
    AppBlockSchedule createAppBlockSchedule(CreateAppBlockScheduleRequest request);
    
    /**
     * 更新屏蔽计划
     */
    AppBlockSchedule updateAppBlockSchedule(UUID id, UpdateAppBlockScheduleRequest request);
    
    /**
     * 删除屏蔽计划
     */
    void deleteAppBlockSchedule(UUID id);
    
    /**
     * 获取所有屏蔽计划
     */
    List<AppBlockSchedule> getAllAppBlockSchedules();
    
    /**
     * 根据标题搜索屏蔽计划
     */
    List<AppBlockSchedule> searchByTitle(String title);
    
    /**
     * 根据原因搜索屏蔽计划
     */
    List<AppBlockSchedule> searchByReason(String reason);
    
    /**
     * 根据日期查询屏蔽计划
     */
    List<AppBlockSchedule> findByDays(String day);
    
    /**
     * 根据时间段查询屏蔽计划
     */
    List<AppBlockSchedule> findByTime(String timeRange);
    
    /**
     * 根据标题和原因组合查询
     */
    List<AppBlockSchedule> findByTitleAndReason(String title, String reason);
    
    /**
     * 统计指定原因的屏蔽计划数量
     */
    long countByReason(String reason);
    
    /**
     * 获取所有不同的原因列表
     */
    List<String> getDistinctReasons();
    
    /**
     * 根据ID列表批量查询
     */
    List<AppBlockSchedule> findByIds(List<UUID> ids);
}
