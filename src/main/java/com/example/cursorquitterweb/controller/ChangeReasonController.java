package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateChangeReasonRequest;
import com.example.cursorquitterweb.dto.UpdateChangeReasonRequest;
import com.example.cursorquitterweb.entity.ChangeReason;
import com.example.cursorquitterweb.service.ChangeReasonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * 用户戒色改变理由控制器
 */
@RestController
@RequestMapping("/api/change-reasons")
public class ChangeReasonController {
    
    @Autowired
    private ChangeReasonService changeReasonService;
    
    /**
     * 创建改变理由记录
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChangeReason>> createChangeReason(
            @Valid @RequestBody CreateChangeReasonRequest request) {
        try {
            ChangeReason changeReason = changeReasonService.createChangeReason(request);
            return ResponseEntity.ok(ApiResponse.success("改变理由记录创建成功", changeReason));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("创建失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取改变理由记录
     * @param id 记录ID
     * @return 改变理由记录
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChangeReason>> getChangeReasonById(@PathVariable UUID id) {
        try {
            ChangeReason changeReason = changeReasonService.getChangeReasonById(id);
            return ResponseEntity.ok(ApiResponse.success("获取成功", changeReason));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("获取失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据用户ID获取所有改变理由记录
     * @param userId 用户ID
     * @return 改变理由记录列表
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<ChangeReason>>> getChangeReasonsByUserId(
            @PathVariable UUID userId) {
        try {
            List<ChangeReason> changeReasons = changeReasonService.getChangeReasonsByUserId(userId);
            return ResponseEntity.ok(ApiResponse.success("获取成功", changeReasons));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("获取失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取用户最近的改变理由记录
     * @param userId 用户ID
     * @return 最近的改变理由记录
     */
    @GetMapping("/user/{userId}/latest")
    public ResponseEntity<ApiResponse<ChangeReason>> getLatestChangeReasonByUserId(
            @PathVariable UUID userId) {
        try {
            ChangeReason changeReason = changeReasonService.getLatestChangeReasonByUserId(userId);
            if (changeReason == null) {
                return ResponseEntity.ok(ApiResponse.success("用户暂无改变理由记录", null));
            }
            return ResponseEntity.ok(ApiResponse.success("获取成功", changeReason));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("获取失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新改变理由记录
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping
    public ResponseEntity<ApiResponse<ChangeReason>> updateChangeReason(
            @Valid @RequestBody UpdateChangeReasonRequest request) {
        try {
            ChangeReason changeReason = changeReasonService.updateChangeReason(request);
            return ResponseEntity.ok(ApiResponse.success("更新成功", changeReason));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除改变理由记录
     * @param id 记录ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteChangeReason(@PathVariable UUID id) {
        try {
            changeReasonService.deleteChangeReason(id);
            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("删除失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除用户的所有改变理由记录
     * @param userId 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteChangeReasonsByUserId(@PathVariable UUID userId) {
        try {
            changeReasonService.deleteChangeReasonsByUserId(userId);
            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("删除失败: " + e.getMessage()));
        }
    }
    
    /**
     * 统计用户的改变理由记录数量
     * @param userId 用户ID
     * @return 记录数量
     */
    @GetMapping("/user/{userId}/count")
    public ResponseEntity<ApiResponse<Long>> countChangeReasonsByUserId(@PathVariable UUID userId) {
        try {
            long count = changeReasonService.countChangeReasonsByUserId(userId);
            return ResponseEntity.ok(ApiResponse.success("统计成功", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("统计失败: " + e.getMessage()));
        }
    }
}
