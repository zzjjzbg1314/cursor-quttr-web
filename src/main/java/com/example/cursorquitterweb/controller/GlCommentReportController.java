package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CommentReportDto;
import com.example.cursorquitterweb.dto.CreateCommentReportRequest;
import com.example.cursorquitterweb.entity.CommentReport;
import com.example.cursorquitterweb.service.GlCommentReportService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 海外评论举报控制器
 */
@RestController
@RequestMapping("/api/gl/comment-reports")
public class GlCommentReportController {

    private static final Logger logger = LogUtil.getLogger(GlCommentReportController.class);

    @Autowired
    private GlCommentReportService glCommentReportService;

    @PostMapping("/create")
    public ApiResponse<CommentReportDto> createReport(@Valid @RequestBody CreateCommentReportRequest request) {
        try {
            CommentReport report = glCommentReportService.createReport(
                request.getReportedCommentId(),
                request.getReportReason(),
                request.getReportNotes(),
                request.getReporterUserId()
            );
            return ApiResponse.success("举报提交成功", new CommentReportDto(report));
        } catch (RuntimeException e) {
            logger.error("海外评论举报失败：{}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("海外评论举报异常", e);
            return ApiResponse.error("举报失败，请稍后重试");
        }
    }

    @GetMapping("/{reportId}")
    public ApiResponse<CommentReportDto> getReport(@PathVariable UUID reportId) {
        return glCommentReportService.findById(reportId)
            .map(report -> ApiResponse.success("获取举报记录成功", new CommentReportDto(report)))
            .orElseGet(() -> ApiResponse.error("举报记录不存在"));
    }

    @GetMapping("/comment/{commentId}")
    public ApiResponse<List<CommentReportDto>> getReportsByCommentId(@PathVariable UUID commentId) {
        List<CommentReportDto> data = glCommentReportService.findByReportedCommentId(commentId).stream()
            .map(CommentReportDto::new)
            .collect(Collectors.toList());
        return ApiResponse.success("获取评论举报记录成功", data);
    }

    @GetMapping("/count/comment/{commentId}")
    public ApiResponse<Long> countReportsByCommentId(@PathVariable UUID commentId) {
        return ApiResponse.success("统计成功", glCommentReportService.countByReportedCommentId(commentId));
    }

    @GetMapping("/check")
    public ApiResponse<Boolean> hasUserReportedComment(@RequestParam UUID commentId, @RequestParam UUID userId) {
        return ApiResponse.success("检查成功", glCommentReportService.hasUserReportedComment(commentId, userId));
    }
}
