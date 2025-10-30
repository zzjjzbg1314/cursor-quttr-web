package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateUserReportRequest;
import com.example.cursorquitterweb.dto.UserReportDto;
import com.example.cursorquitterweb.entity.UserReport;
import com.example.cursorquitterweb.service.UserReportService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户举报控制器
 */
@RestController
@RequestMapping("/api/user-reports")
public class UserReportController {
    
    private static final Logger logger = LogUtil.getLogger(UserReportController.class);
    
    @Autowired
    private UserReportService userReportService;
    
    /**
     * 创建举报
     * POST /api/user-reports/create
     */
    @PostMapping("/create")
    public ApiResponse<UserReportDto> createReport(@Valid @RequestBody CreateUserReportRequest request) {
        try {
            logger.info("收到用户举报请求，reported_user_id: {}, reporter_user_id: {}, reason: {}", 
                       request.getReportedUserId(), request.getReporterUserId(), request.getReason());
            
            UserReport report = userReportService.createReport(
                request.getReportedUserId(),
                request.getReporterUserId(),
                request.getReason(),
                request.getRemark()
            );
            
            UserReportDto dto = new UserReportDto(report);
            return ApiResponse.success("举报提交成功", dto);
            
        } catch (RuntimeException e) {
            logger.error("举报失败：{}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("举报异常", e);
            return ApiResponse.error("举报失败，请稍后重试");
        }
    }
    
    /**
     * 根据ID获取举报记录
     * GET /api/user-reports/{reportId}
     */
    @GetMapping("/{reportId}")
    public ApiResponse<UserReportDto> getReport(@PathVariable UUID reportId) {
        try {
            UserReport report = userReportService.findById(reportId)
                .orElseThrow(() -> new RuntimeException("举报记录不存在"));
            
            UserReportDto dto = new UserReportDto(report);
            return ApiResponse.success("获取举报记录成功", dto);
            
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取举报记录异常", e);
            return ApiResponse.error("获取举报记录失败");
        }
    }
    
    /**
     * 获取所有举报记录（分页）
     * GET /api/user-reports
     */
    @GetMapping
    public ApiResponse<Page<UserReportDto>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            
            Page<UserReport> reports = userReportService.getAllReports(pageable);
            Page<UserReportDto> dtoPage = reports.map(UserReportDto::new);
            
            return ApiResponse.success("获取举报记录成功", dtoPage);
            
        } catch (Exception e) {
            logger.error("获取举报记录列表异常", e);
            return ApiResponse.error("获取举报记录列表失败");
        }
    }
    
    /**
     * 根据被举报用户ID获取举报记录
     * GET /api/user-reports/reported/{userId}
     */
    @GetMapping("/reported/{userId}")
    public ApiResponse<List<UserReportDto>> getReportsByReportedUserId(@PathVariable UUID userId) {
        try {
            List<UserReport> reports = userReportService.findByReportedUserId(userId);
            List<UserReportDto> dtoList = reports.stream()
                .map(UserReportDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success("获取用户被举报记录成功", dtoList);
            
        } catch (Exception e) {
            logger.error("获取用户被举报记录异常", e);
            return ApiResponse.error("获取用户被举报记录失败");
        }
    }
    
    /**
     * 根据被举报用户ID分页获取举报记录
     * GET /api/user-reports/reported/{userId}/page
     */
    @GetMapping("/reported/{userId}/page")
    public ApiResponse<Page<UserReportDto>> getReportsByReportedUserIdPage(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<UserReport> reports = userReportService.findByReportedUserId(userId, pageable);
            Page<UserReportDto> dtoPage = reports.map(UserReportDto::new);
            
            return ApiResponse.success("获取用户被举报记录成功", dtoPage);
            
        } catch (Exception e) {
            logger.error("获取用户被举报记录异常", e);
            return ApiResponse.error("获取用户被举报记录失败");
        }
    }
    
    /**
     * 根据举报人ID获取举报记录
     * GET /api/user-reports/reporter/{userId}
     */
    @GetMapping("/reporter/{userId}")
    public ApiResponse<List<UserReportDto>> getReportsByReporterUserId(@PathVariable UUID userId) {
        try {
            List<UserReport> reports = userReportService.findByReporterUserId(userId);
            List<UserReportDto> dtoList = reports.stream()
                .map(UserReportDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success("获取用户举报记录成功", dtoList);
            
        } catch (Exception e) {
            logger.error("获取用户举报记录异常", e);
            return ApiResponse.error("获取用户举报记录失败");
        }
    }
    
    /**
     * 根据举报人ID分页获取举报记录
     * GET /api/user-reports/reporter/{userId}/page
     */
    @GetMapping("/reporter/{userId}/page")
    public ApiResponse<Page<UserReportDto>> getReportsByReporterUserIdPage(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<UserReport> reports = userReportService.findByReporterUserId(userId, pageable);
            Page<UserReportDto> dtoPage = reports.map(UserReportDto::new);
            
            return ApiResponse.success("获取用户举报记录成功", dtoPage);
            
        } catch (Exception e) {
            logger.error("获取用户举报记录异常", e);
            return ApiResponse.error("获取用户举报记录失败");
        }
    }
    
    /**
     * 根据举报原因获取举报记录
     * GET /api/user-reports/reason
     */
    @GetMapping("/reason")
    public ApiResponse<List<UserReportDto>> getReportsByReason(@RequestParam String reason) {
        try {
            List<UserReport> reports = userReportService.findByReason(reason);
            List<UserReportDto> dtoList = reports.stream()
                .map(UserReportDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success("获取举报记录成功", dtoList);
            
        } catch (Exception e) {
            logger.error("获取举报记录异常", e);
            return ApiResponse.error("获取举报记录失败");
        }
    }
    
    /**
     * 统计某个用户被举报的次数
     * GET /api/user-reports/count/reported/{userId}
     */
    @GetMapping("/count/reported/{userId}")
    public ApiResponse<Long> countReportsByReportedUserId(@PathVariable UUID userId) {
        try {
            long count = userReportService.countByReportedUserId(userId);
            return ApiResponse.success("统计成功", count);
        } catch (Exception e) {
            logger.error("统计失败", e);
            return ApiResponse.error("统计失败");
        }
    }
    
    /**
     * 统计某个用户举报的次数
     * GET /api/user-reports/count/reporter/{userId}
     */
    @GetMapping("/count/reporter/{userId}")
    public ApiResponse<Long> countReportsByReporterUserId(@PathVariable UUID userId) {
        try {
            long count = userReportService.countByReporterUserId(userId);
            return ApiResponse.success("统计成功", count);
        } catch (Exception e) {
            logger.error("统计失败", e);
            return ApiResponse.error("统计失败");
        }
    }
    
    /**
     * 根据时间范围获取举报记录
     * GET /api/user-reports/time-range
     */
    @GetMapping("/time-range")
    public ApiResponse<List<UserReportDto>> getReportsByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            
            List<UserReport> reports = userReportService.findByTimeRange(start, end);
            List<UserReportDto> dtoList = reports.stream()
                .map(UserReportDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success("获取举报记录成功", dtoList);
            
        } catch (Exception e) {
            logger.error("获取举报记录异常", e);
            return ApiResponse.error("获取举报记录失败");
        }
    }
    
    /**
     * 检查举报人是否已经举报过该用户
     * GET /api/user-reports/check
     */
    @GetMapping("/check")
    public ApiResponse<Boolean> checkUserReported(
            @RequestParam UUID reportedUserId,
            @RequestParam UUID reporterUserId) {
        try {
            boolean hasReported = userReportService.hasReportedUser(reportedUserId, reporterUserId);
            return ApiResponse.success("查询成功", hasReported);
        } catch (Exception e) {
            logger.error("查询失败", e);
            return ApiResponse.error("查询失败");
        }
    }
    
    /**
     * 删除举报记录
     * DELETE /api/user-reports/{reportId}
     */
    @DeleteMapping("/{reportId}")
    public ApiResponse<String> deleteReport(@PathVariable UUID reportId) {
        try {
            userReportService.deleteReport(reportId);
            return ApiResponse.success("删除举报记录成功", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除举报记录异常", e);
            return ApiResponse.error("删除举报记录失败");
        }
    }
    
    /**
     * 获取被举报最多的用户列表
     * GET /api/user-reports/most-reported
     */
    @GetMapping("/most-reported")
    public ApiResponse<List<Map<String, Object>>> getMostReportedUsers() {
        try {
            List<Object[]> results = userReportService.findMostReportedUsers();
            List<Map<String, Object>> data = results.stream().map(result -> {
                Map<String, Object> map = new HashMap<>();
                map.put("user_id", result[0]);
                map.put("report_count", result[1]);
                return map;
            }).collect(Collectors.toList());
            
            return ApiResponse.success("获取被举报最多的用户列表成功", data);
            
        } catch (Exception e) {
            logger.error("获取被举报最多的用户列表异常", e);
            return ApiResponse.error("获取被举报最多的用户列表失败");
        }
    }
    
    /**
     * 分页获取被举报最多的用户列表
     * GET /api/user-reports/most-reported/page
     */
    @GetMapping("/most-reported/page")
    public ApiResponse<Page<Map<String, Object>>> getMostReportedUsersPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Object[]> results = userReportService.findMostReportedUsers(pageable);
            
            Page<Map<String, Object>> data = results.map(result -> {
                Map<String, Object> map = new HashMap<>();
                map.put("user_id", result[0]);
                map.put("report_count", result[1]);
                return map;
            });
            
            return ApiResponse.success("获取被举报最多的用户列表成功", data);
            
        } catch (Exception e) {
            logger.error("获取被举报最多的用户列表异常", e);
            return ApiResponse.error("获取被举报最多的用户列表失败");
        }
    }
}

