package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateAppBlockScheduleRequest;
import com.example.cursorquitterweb.dto.UpdateAppBlockScheduleRequest;
import com.example.cursorquitterweb.entity.AppBlockSchedule;
import com.example.cursorquitterweb.service.AppBlockScheduleService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 应用屏蔽计划控制器
 */
@RestController
@RequestMapping("/api/app-block-schedules")
public class AppBlockScheduleController {
    
    private static final Logger logger = LogUtil.getLogger(AppBlockScheduleController.class);
    
    @Autowired
    private AppBlockScheduleService appBlockScheduleService;
    
    /**
     * 根据ID获取屏蔽计划
     */
    @GetMapping("/{id}")
    public ApiResponse<AppBlockSchedule> getAppBlockScheduleById(@PathVariable UUID id) {
        logger.info("获取屏蔽计划信息，ID: {}", id);
        Optional<AppBlockSchedule> schedule = appBlockScheduleService.findById(id);
        if (schedule.isPresent()) {
            return ApiResponse.success(schedule.get());
        } else {
            return ApiResponse.error("屏蔽计划不存在");
        }
    }
    
    /**
     * 创建新的屏蔽计划
     */
    @PostMapping("/create")
    public ApiResponse<AppBlockSchedule> createAppBlockSchedule(@RequestBody CreateAppBlockScheduleRequest request) {
        logger.info("创建新的屏蔽计划，标题: {}", request.getTitle());
        
        try {
            AppBlockSchedule schedule = appBlockScheduleService.createAppBlockSchedule(request);
            return ApiResponse.success("屏蔽计划创建成功", schedule);
        } catch (Exception e) {
            logger.error("创建屏蔽计划失败: {}", e.getMessage());
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新屏蔽计划
     */
    @PutMapping("/{id}")
    public ApiResponse<AppBlockSchedule> updateAppBlockSchedule(@PathVariable UUID id, @RequestBody UpdateAppBlockScheduleRequest request) {
        logger.info("更新屏蔽计划，ID: {}", id);
        
        try {
            AppBlockSchedule schedule = appBlockScheduleService.updateAppBlockSchedule(id, request);
            return ApiResponse.success("屏蔽计划更新成功", schedule);
        } catch (RuntimeException e) {
            logger.error("更新屏蔽计划失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新屏蔽计划失败: {}", e.getMessage());
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除屏蔽计划
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAppBlockSchedule(@PathVariable UUID id) {
        logger.info("删除屏蔽计划，ID: {}", id);
        
        try {
            appBlockScheduleService.deleteAppBlockSchedule(id);
            return ApiResponse.success("屏蔽计划删除成功", null);
        } catch (RuntimeException e) {
            logger.error("删除屏蔽计划失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除屏蔽计划失败: {}", e.getMessage());
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有屏蔽计划
     */
    @GetMapping
    public ApiResponse<List<AppBlockSchedule>> getAllAppBlockSchedules() {
        logger.info("获取所有屏蔽计划");
        List<AppBlockSchedule> schedules = appBlockScheduleService.getAllAppBlockSchedules();
        return ApiResponse.success(schedules);
    }
    
    /**
     * 根据标题搜索屏蔽计划
     */
    @GetMapping("/search/title")
    public ApiResponse<List<AppBlockSchedule>> searchByTitle(@RequestParam String title) {
        logger.info("根据标题搜索屏蔽计划: {}", title);
        List<AppBlockSchedule> schedules = appBlockScheduleService.searchByTitle(title);
        return ApiResponse.success(schedules);
    }
    
    /**
     * 根据原因搜索屏蔽计划
     */
    @GetMapping("/search/reason")
    public ApiResponse<List<AppBlockSchedule>> searchByReason(@RequestParam String reason) {
        logger.info("根据原因搜索屏蔽计划: {}", reason);
        List<AppBlockSchedule> schedules = appBlockScheduleService.searchByReason(reason);
        return ApiResponse.success(schedules);
    }
    
    /**
     * 根据日期查询屏蔽计划
     */
    @GetMapping("/search/days")
    public ApiResponse<List<AppBlockSchedule>> findByDays(@RequestParam String day) {
        logger.info("根据日期查询屏蔽计划: {}", day);
        List<AppBlockSchedule> schedules = appBlockScheduleService.findByDays(day);
        return ApiResponse.success(schedules);
    }
    
    /**
     * 根据时间段查询屏蔽计划
     */
    @GetMapping("/search/time")
    public ApiResponse<List<AppBlockSchedule>> findByTime(@RequestParam String timeRange) {
        logger.info("根据时间段查询屏蔽计划: {}", timeRange);
        List<AppBlockSchedule> schedules = appBlockScheduleService.findByTime(timeRange);
        return ApiResponse.success(schedules);
    }
    
    /**
     * 根据标题和原因组合查询
     */
    @GetMapping("/search/combined")
    public ApiResponse<List<AppBlockSchedule>> findByTitleAndReason(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String reason) {
        logger.info("根据标题和原因组合查询，标题: {}, 原因: {}", title, reason);
        List<AppBlockSchedule> schedules = appBlockScheduleService.findByTitleAndReason(title, reason);
        return ApiResponse.success(schedules);
    }
    
    /**
     * 统计指定原因的屏蔽计划数量
     */
    @GetMapping("/stats/reason")
    public ApiResponse<Long> countByReason(@RequestParam String reason) {
        logger.info("统计指定原因的屏蔽计划数量: {}", reason);
        long count = appBlockScheduleService.countByReason(reason);
        return ApiResponse.success(count);
    }
    
    /**
     * 获取所有不同的原因列表
     */
    @GetMapping("/reasons")
    public ApiResponse<List<String>> getDistinctReasons() {
        logger.info("获取所有不同的原因列表");
        List<String> reasons = appBlockScheduleService.getDistinctReasons();
        return ApiResponse.success(reasons);
    }
    
    /**
     * 根据ID列表批量查询
     */
    @PostMapping("/batch")
    public ApiResponse<List<AppBlockSchedule>> findByIds(@RequestBody List<UUID> ids) {
        logger.info("根据ID列表批量查询屏蔽计划，数量: {}", ids.size());
        List<AppBlockSchedule> schedules = appBlockScheduleService.findByIds(ids);
        return ApiResponse.success(schedules);
    }
}
