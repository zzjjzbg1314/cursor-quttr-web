package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.AppBlockSchedule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 应用屏蔽计划服务接口
 */
public interface AppBlockScheduleService {
    
    /**
     * 根据ID查找应用屏蔽计划
     */
    Optional<AppBlockSchedule> findById(UUID id);
    
    /**
     * 创建新的应用屏蔽计划
     */
    AppBlockSchedule createAppBlockSchedule(String title, String subtitle, String days, String time, String reason, String image);
    
    /**
     * 更新应用屏蔽计划
     */
    AppBlockSchedule updateAppBlockSchedule(UUID id, String title, String subtitle, String days, String time, String reason, String image);
    
    /**
     * 删除应用屏蔽计划
     */
    void deleteAppBlockSchedule(UUID id);
    
    /**
     * 获取所有应用屏蔽计划
     */
    List<AppBlockSchedule> getAllAppBlockSchedules();
    
    /**
     * 分页查询应用屏蔽计划列表
     */
    List<AppBlockSchedule> getAppBlockSchedulesPage(int page, int size);
}

