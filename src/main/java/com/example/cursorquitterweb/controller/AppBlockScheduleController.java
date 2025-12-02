package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateAppBlockScheduleRequest;
import com.example.cursorquitterweb.dto.UpdateAppBlockScheduleRequest;
import com.example.cursorquitterweb.entity.AppBlockSchedule;
import com.example.cursorquitterweb.service.AppBlockScheduleService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 应用屏蔽计划控制器
 * 提供应用屏蔽计划的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/app-block-schedules")
@Validated
public class AppBlockScheduleController {
    
    private static final Logger logger = LogUtil.getLogger(AppBlockScheduleController.class);
    
    @Autowired
    private AppBlockScheduleService appBlockScheduleService;
    
    /**
     * 获取所有应用屏蔽计划
     * 直接通过根路径 /api/app-block-schedules 访问
     * 使用缓存（在 Service 层实现）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AppBlockSchedule>>> getAllAppBlockSchedules() {
        try {
            logger.info("获取所有应用屏蔽计划");
            
            List<AppBlockSchedule> schedules = appBlockScheduleService.getAllAppBlockSchedules();
            
            return ResponseEntity.ok(ApiResponse.success("获取应用屏蔽计划列表成功", schedules));
            
        } catch (Exception e) {
            logger.error("获取应用屏蔽计划列表失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取应用屏蔽计划列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建新的应用屏蔽计划
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<AppBlockSchedule>> createAppBlockSchedule(
            @Valid @RequestBody CreateAppBlockScheduleRequest request) {
        try {
            logger.info("创建应用屏蔽计划，请求参数: {}", request);
            
            AppBlockSchedule schedule = appBlockScheduleService.createAppBlockSchedule(
                request.getTitle(),
                request.getSubtitle(),
                request.getDays(),
                request.getTime(),
                request.getReason(),
                request.getImage()
            );
            
            logger.info("应用屏蔽计划创建成功，ID: {}", schedule.getId());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("应用屏蔽计划创建成功", schedule));
                    
        } catch (Exception e) {
            logger.error("创建应用屏蔽计划失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建应用屏蔽计划失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取应用屏蔽计划
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppBlockSchedule>> getAppBlockSchedule(@PathVariable UUID id) {
        try {
            logger.info("获取应用屏蔽计划，ID: {}", id);
            
            Optional<AppBlockSchedule> optionalSchedule = appBlockScheduleService.findById(id);
            if (optionalSchedule.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("获取应用屏蔽计划成功", optionalSchedule.get()));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("应用屏蔽计划不存在"));
            }
            
        } catch (Exception e) {
            logger.error("获取应用屏蔽计划失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取应用屏蔽计划失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新应用屏蔽计划
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AppBlockSchedule>> updateAppBlockSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppBlockScheduleRequest request) {
        try {
            logger.info("更新应用屏蔽计划，ID: {}, 请求参数: {}", id, request);
            
            AppBlockSchedule schedule = appBlockScheduleService.updateAppBlockSchedule(
                id,
                request.getTitle(),
                request.getSubtitle(),
                request.getDays(),
                request.getTime(),
                request.getReason(),
                request.getImage()
            );
            
            logger.info("应用屏蔽计划更新成功，ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("应用屏蔽计划更新成功", schedule));
                    
        } catch (RuntimeException e) {
            logger.error("更新应用屏蔽计划失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("更新应用屏蔽计划失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新应用屏蔽计划失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除应用屏蔽计划
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAppBlockSchedule(@PathVariable UUID id) {
        try {
            logger.info("删除应用屏蔽计划，ID: {}", id);
            
            appBlockScheduleService.deleteAppBlockSchedule(id);
            
            logger.info("应用屏蔽计划删除成功，ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("应用屏蔽计划删除成功", null));
                    
        } catch (RuntimeException e) {
            logger.error("删除应用屏蔽计划失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("删除应用屏蔽计划失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除应用屏蔽计划失败: " + e.getMessage()));
        }
    }
    
    /**
     * 分页查询应用屏蔽计划列表
     * 使用缓存（在 Service 层实现），缓存键包含 page 和 size 参数
     */
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<List<AppBlockSchedule>>> getAppBlockSchedulesPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            logger.info("分页查询应用屏蔽计划列表，页码: {}, 大小: {}", page, size);
            
            if (page < 0) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("页码不能小于0"));
            }
            if (size <= 0 || size > 100) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("每页大小必须在1-100之间"));
            }
            
            List<AppBlockSchedule> schedules = appBlockScheduleService.getAppBlockSchedulesPage(page, size);
            
            return ResponseEntity.ok(ApiResponse.success("获取应用屏蔽计划列表成功", schedules));
            
        } catch (Exception e) {
            logger.error("获取应用屏蔽计划列表失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取应用屏蔽计划列表失败: " + e.getMessage()));
        }
    }
}

