package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreatePostReportRequest;
import com.example.cursorquitterweb.dto.PostReportDto;
import com.example.cursorquitterweb.entity.PostReport;
import com.example.cursorquitterweb.service.GlPostReportService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 海外帖子举报控制器
 */
@RestController
@RequestMapping("/api/gl/post-reports")
public class GlPostReportController {

    private static final Logger logger = LogUtil.getLogger(GlPostReportController.class);

    @Autowired
    private GlPostReportService glPostReportService;

    @PostMapping("/create")
    public ApiResponse<PostReportDto> createReport(@Valid @RequestBody CreatePostReportRequest request) {
        try {
            PostReport report = glPostReportService.createReport(
                request.getReportedPostId(),
                request.getReportReason(),
                request.getReportNotes(),
                request.getReporterUserId()
            );
            return ApiResponse.success("举报提交成功", new PostReportDto(report));
        } catch (RuntimeException e) {
            logger.error("海外帖子举报失败：{}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("海外帖子举报异常", e);
            return ApiResponse.error("举报失败，请稍后重试");
        }
    }

    @GetMapping("/{reportId}")
    public ApiResponse<PostReportDto> getReport(@PathVariable UUID reportId) {
        return glPostReportService.findById(reportId)
            .map(report -> ApiResponse.success("获取举报记录成功", new PostReportDto(report)))
            .orElseGet(() -> ApiResponse.error("举报记录不存在"));
    }

    @GetMapping("/post/{postId}")
    public ApiResponse<List<PostReportDto>> getReportsByPostId(@PathVariable UUID postId) {
        List<PostReportDto> data = glPostReportService.findByReportedPostId(postId).stream()
            .map(PostReportDto::new)
            .collect(Collectors.toList());
        return ApiResponse.success("获取帖子举报记录成功", data);
    }

    @GetMapping("/count/post/{postId}")
    public ApiResponse<Long> countReportsByPostId(@PathVariable UUID postId) {
        return ApiResponse.success("统计成功", glPostReportService.countByReportedPostId(postId));
    }

    @GetMapping("/check")
    public ApiResponse<Boolean> hasUserReportedPost(@RequestParam UUID postId, @RequestParam UUID userId) {
        return ApiResponse.success("检查成功", glPostReportService.hasUserReportedPost(postId, userId));
    }
}
