package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreatePostReportRequest;
import com.example.cursorquitterweb.dto.PostReportDto;
import com.example.cursorquitterweb.entity.PostReport;
import com.example.cursorquitterweb.service.PostReportService;
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
 * 帖子举报控制器
 */
@RestController
@RequestMapping("/api/post-reports")
public class PostReportController {
    
    private static final Logger logger = LogUtil.getLogger(PostReportController.class);
    
    @Autowired
    private PostReportService postReportService;
    
    /**
     * 创建举报
     * POST /api/post-reports/create
     */
    @PostMapping("/create")
    public ApiResponse<PostReportDto> createReport(@Valid @RequestBody CreatePostReportRequest request) {
        try {
            logger.info("收到举报请求，post_id: {}, user_id: {}, reason: {}", 
                       request.getReportedPostId(), request.getReporterUserId(), request.getReportReason());
            
            PostReport report = postReportService.createReport(
                request.getReportedPostId(),
                request.getReportReason(),
                request.getReportNotes(),
                request.getReporterUserId()
            );
            
            PostReportDto dto = new PostReportDto(report);
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
     * GET /api/post-reports/{reportId}
     */
    @GetMapping("/{reportId}")
    public ApiResponse<PostReportDto> getReport(@PathVariable UUID reportId) {
        try {
            PostReport report = postReportService.findById(reportId)
                .orElseThrow(() -> new RuntimeException("举报记录不存在"));
            
            PostReportDto dto = new PostReportDto(report);
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
     * GET /api/post-reports
     */
    @GetMapping
    public ApiResponse<Page<PostReportDto>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            
            Page<PostReport> reports = postReportService.getAllReports(pageable);
            Page<PostReportDto> dtoPage = reports.map(PostReportDto::new);
            
            return ApiResponse.success("获取举报记录成功", dtoPage);
            
        } catch (Exception e) {
            logger.error("获取举报记录列表异常", e);
            return ApiResponse.error("获取举报记录列表失败");
        }
    }
    
    /**
     * 根据帖子ID获取举报记录
     * GET /api/post-reports/post/{postId}
     */
    @GetMapping("/post/{postId}")
    public ApiResponse<List<PostReportDto>> getReportsByPostId(@PathVariable UUID postId) {
        try {
            List<PostReport> reports = postReportService.findByReportedPostId(postId);
            List<PostReportDto> dtoList = reports.stream()
                .map(PostReportDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success("获取帖子举报记录成功", dtoList);
            
        } catch (Exception e) {
            logger.error("获取帖子举报记录异常", e);
            return ApiResponse.error("获取帖子举报记录失败");
        }
    }
    
    /**
     * 根据帖子ID分页获取举报记录
     * GET /api/post-reports/post/{postId}/page
     */
    @GetMapping("/post/{postId}/page")
    public ApiResponse<Page<PostReportDto>> getReportsByPostIdPage(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<PostReport> reports = postReportService.findByReportedPostId(postId, pageable);
            Page<PostReportDto> dtoPage = reports.map(PostReportDto::new);
            
            return ApiResponse.success("获取帖子举报记录成功", dtoPage);
            
        } catch (Exception e) {
            logger.error("获取帖子举报记录异常", e);
            return ApiResponse.error("获取帖子举报记录失败");
        }
    }
    
    /**
     * 根据举报人ID获取举报记录
     * GET /api/post-reports/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<PostReportDto>> getReportsByUserId(@PathVariable UUID userId) {
        try {
            List<PostReport> reports = postReportService.findByReporterUserId(userId);
            List<PostReportDto> dtoList = reports.stream()
                .map(PostReportDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success("获取用户举报记录成功", dtoList);
            
        } catch (Exception e) {
            logger.error("获取用户举报记录异常", e);
            return ApiResponse.error("获取用户举报记录失败");
        }
    }
    
    /**
     * 根据举报人ID分页获取举报记录
     * GET /api/post-reports/user/{userId}/page
     */
    @GetMapping("/user/{userId}/page")
    public ApiResponse<Page<PostReportDto>> getReportsByUserIdPage(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<PostReport> reports = postReportService.findByReporterUserId(userId, pageable);
            Page<PostReportDto> dtoPage = reports.map(PostReportDto::new);
            
            return ApiResponse.success("获取用户举报记录成功", dtoPage);
            
        } catch (Exception e) {
            logger.error("获取用户举报记录异常", e);
            return ApiResponse.error("获取用户举报记录失败");
        }
    }
    
    /**
     * 根据举报原因获取举报记录
     * GET /api/post-reports/reason
     */
    @GetMapping("/reason")
    public ApiResponse<List<PostReportDto>> getReportsByReason(@RequestParam String reason) {
        try {
            List<PostReport> reports = postReportService.findByReportReason(reason);
            List<PostReportDto> dtoList = reports.stream()
                .map(PostReportDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success("获取举报记录成功", dtoList);
            
        } catch (Exception e) {
            logger.error("获取举报记录异常", e);
            return ApiResponse.error("获取举报记录失败");
        }
    }
    
    /**
     * 统计某个帖子被举报的次数
     * GET /api/post-reports/count/post/{postId}
     */
    @GetMapping("/count/post/{postId}")
    public ApiResponse<Long> countReportsByPostId(@PathVariable UUID postId) {
        try {
            long count = postReportService.countByReportedPostId(postId);
            return ApiResponse.success("统计成功", count);
        } catch (Exception e) {
            logger.error("统计失败", e);
            return ApiResponse.error("统计失败");
        }
    }
    
    /**
     * 统计某个用户举报的次数
     * GET /api/post-reports/count/user/{userId}
     */
    @GetMapping("/count/user/{userId}")
    public ApiResponse<Long> countReportsByUserId(@PathVariable UUID userId) {
        try {
            long count = postReportService.countByReporterUserId(userId);
            return ApiResponse.success("统计成功", count);
        } catch (Exception e) {
            logger.error("统计失败", e);
            return ApiResponse.error("统计失败");
        }
    }
    
    /**
     * 根据时间范围获取举报记录
     * GET /api/post-reports/time-range
     */
    @GetMapping("/time-range")
    public ApiResponse<List<PostReportDto>> getReportsByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            
            List<PostReport> reports = postReportService.findByTimeRange(start, end);
            List<PostReportDto> dtoList = reports.stream()
                .map(PostReportDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success("获取举报记录成功", dtoList);
            
        } catch (Exception e) {
            logger.error("获取举报记录异常", e);
            return ApiResponse.error("获取举报记录失败");
        }
    }
    
    /**
     * 检查用户是否已经举报过该帖子
     * GET /api/post-reports/check
     */
    @GetMapping("/check")
    public ApiResponse<Boolean> checkUserReported(
            @RequestParam UUID postId,
            @RequestParam UUID userId) {
        try {
            boolean hasReported = postReportService.hasUserReportedPost(postId, userId);
            return ApiResponse.success("查询成功", hasReported);
        } catch (Exception e) {
            logger.error("查询失败", e);
            return ApiResponse.error("查询失败");
        }
    }
    
    /**
     * 删除举报记录
     * DELETE /api/post-reports/{reportId}
     */
    @DeleteMapping("/{reportId}")
    public ApiResponse<String> deleteReport(@PathVariable UUID reportId) {
        try {
            postReportService.deleteReport(reportId);
            return ApiResponse.success("删除举报记录成功", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除举报记录异常", e);
            return ApiResponse.error("删除举报记录失败");
        }
    }
    
    /**
     * 获取被举报最多的帖子列表
     * GET /api/post-reports/most-reported
     */
    @GetMapping("/most-reported")
    public ApiResponse<List<Map<String, Object>>> getMostReportedPosts() {
        try {
            List<Object[]> results = postReportService.findMostReportedPosts();
            List<Map<String, Object>> data = results.stream().map(result -> {
                Map<String, Object> map = new HashMap<>();
                map.put("post_id", result[0]);
                map.put("report_count", result[1]);
                return map;
            }).collect(Collectors.toList());
            
            return ApiResponse.success("获取被举报最多的帖子列表成功", data);
            
        } catch (Exception e) {
            logger.error("获取被举报最多的帖子列表异常", e);
            return ApiResponse.error("获取被举报最多的帖子列表失败");
        }
    }
    
    /**
     * 分页获取被举报最多的帖子列表
     * GET /api/post-reports/most-reported/page
     */
    @GetMapping("/most-reported/page")
    public ApiResponse<Page<Map<String, Object>>> getMostReportedPostsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Object[]> results = postReportService.findMostReportedPosts(pageable);
            
            Page<Map<String, Object>> data = results.map(result -> {
                Map<String, Object> map = new HashMap<>();
                map.put("post_id", result[0]);
                map.put("report_count", result[1]);
                return map;
            });
            
            return ApiResponse.success("获取被举报最多的帖子列表成功", data);
            
        } catch (Exception e) {
            logger.error("获取被举报最多的帖子列表异常", e);
            return ApiResponse.error("获取被举报最多的帖子列表失败");
        }
    }
}

