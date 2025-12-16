package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateFeedbackRequest;
import com.example.cursorquitterweb.dto.FeedbackDto;
import com.example.cursorquitterweb.entity.Feedback;
import com.example.cursorquitterweb.service.FeedbackService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 反馈控制器
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    
    private static final Logger logger = LogUtil.getLogger(FeedbackController.class);
    
    @Autowired
    private FeedbackService feedbackService;
    
    /**
     * 创建反馈
     * POST /api/feedback/create
     */
    @PostMapping("/create")
    public ApiResponse<FeedbackDto> createFeedback(@Valid @RequestBody CreateFeedbackRequest request) {
        try {
            logger.info("收到反馈请求，user_id: {}, feedback_type: {}", 
                       request.getUserId(), request.getFeedbackType());
            
            Feedback feedback = feedbackService.createFeedback(
                request.getFeedbackType(),
                request.getContent(),
                request.getUserId()
            );
            
            FeedbackDto dto = new FeedbackDto(feedback);
            return ApiResponse.success("反馈提交成功", dto);
            
        } catch (RuntimeException e) {
            logger.error("反馈失败：{}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("反馈异常", e);
            return ApiResponse.error("反馈失败，请稍后重试");
        }
    }
    
    /**
     * 根据ID获取反馈
     * GET /api/feedback/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<FeedbackDto> getFeedback(@PathVariable Long id) {
        try {
            Optional<Feedback> feedback = feedbackService.findById(id);
            if (feedback.isPresent()) {
                FeedbackDto dto = new FeedbackDto(feedback.get());
                return ApiResponse.success("获取反馈成功", dto);
            } else {
                return ApiResponse.error("反馈不存在");
            }
        } catch (Exception e) {
            logger.error("获取反馈失败", e);
            return ApiResponse.error("获取反馈失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户ID获取反馈列表
     * GET /api/feedback/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<FeedbackDto>> getFeedbacksByUserId(@PathVariable UUID userId) {
        try {
            List<Feedback> feedbacks = feedbackService.findByUserId(userId);
            List<FeedbackDto> dtos = feedbacks.stream()
                .map(FeedbackDto::new)
                .collect(Collectors.toList());
            return ApiResponse.success("获取用户反馈列表成功", dtos);
        } catch (Exception e) {
            logger.error("获取用户反馈列表失败", e);
            return ApiResponse.error("获取用户反馈列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户ID分页获取反馈列表
     * GET /api/feedback/user/{userId}/page
     */
    @GetMapping("/user/{userId}/page")
    public ApiResponse<List<FeedbackDto>> getFeedbacksByUserIdPage(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<Feedback> feedbacks = feedbackService.findByUserId(userId, page, size);
            List<FeedbackDto> dtos = feedbacks.stream()
                .map(FeedbackDto::new)
                .collect(Collectors.toList());
            return ApiResponse.success("获取用户反馈列表成功", dtos);
        } catch (Exception e) {
            logger.error("获取用户反馈列表失败", e);
            return ApiResponse.error("获取用户反馈列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据反馈类型获取反馈列表
     * GET /api/feedback/type/{feedbackType}
     */
    @GetMapping("/type/{feedbackType}")
    public ApiResponse<List<FeedbackDto>> getFeedbacksByType(@PathVariable String feedbackType) {
        try {
            List<Feedback> feedbacks = feedbackService.findByFeedbackType(feedbackType);
            List<FeedbackDto> dtos = feedbacks.stream()
                .map(FeedbackDto::new)
                .collect(Collectors.toList());
            return ApiResponse.success("获取反馈列表成功", dtos);
        } catch (Exception e) {
            logger.error("获取反馈列表失败", e);
            return ApiResponse.error("获取反馈列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据反馈类型分页获取反馈列表
     * GET /api/feedback/type/{feedbackType}/page
     */
    @GetMapping("/type/{feedbackType}/page")
    public ApiResponse<List<FeedbackDto>> getFeedbacksByTypePage(
            @PathVariable String feedbackType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<Feedback> feedbacks = feedbackService.findByFeedbackType(feedbackType, page, size);
            List<FeedbackDto> dtos = feedbacks.stream()
                .map(FeedbackDto::new)
                .collect(Collectors.toList());
            return ApiResponse.success("获取反馈列表成功", dtos);
        } catch (Exception e) {
            logger.error("获取反馈列表失败", e);
            return ApiResponse.error("获取反馈列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有反馈（分页）
     * GET /api/feedback/all
     */
    @GetMapping("/all")
    public ApiResponse<List<FeedbackDto>> getAllFeedbacks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<Feedback> feedbacks = feedbackService.getAllFeedbacks(page, size);
            List<FeedbackDto> dtos = feedbacks.stream()
                .map(FeedbackDto::new)
                .collect(Collectors.toList());
            return ApiResponse.success("获取反馈列表成功", dtos);
        } catch (Exception e) {
            logger.error("获取反馈列表失败", e);
            return ApiResponse.error("获取反馈列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据时间范围获取反馈列表
     * GET /api/feedback/time-range
     */
    @GetMapping("/time-range")
    public ApiResponse<List<FeedbackDto>> getFeedbacksByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            List<Feedback> feedbacks = feedbackService.findByTimeRange(start, end);
            List<FeedbackDto> dtos = feedbacks.stream()
                .map(FeedbackDto::new)
                .collect(Collectors.toList());
            return ApiResponse.success("获取反馈列表成功", dtos);
        } catch (Exception e) {
            logger.error("获取反馈列表失败", e);
            return ApiResponse.error("获取反馈列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计用户的反馈数量
     * GET /api/feedback/count/user/{userId}
     */
    @GetMapping("/count/user/{userId}")
    public ApiResponse<Long> countFeedbacksByUserId(@PathVariable UUID userId) {
        try {
            long count = feedbackService.countByUserId(userId);
            return ApiResponse.success("统计用户反馈数量成功", count);
        } catch (Exception e) {
            logger.error("统计用户反馈数量失败", e);
            return ApiResponse.error("统计用户反馈数量失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计反馈类型的数量
     * GET /api/feedback/count/type/{feedbackType}
     */
    @GetMapping("/count/type/{feedbackType}")
    public ApiResponse<Long> countFeedbacksByType(@PathVariable String feedbackType) {
        try {
            long count = feedbackService.countByFeedbackType(feedbackType);
            return ApiResponse.success("统计反馈类型数量成功", count);
        } catch (Exception e) {
            logger.error("统计反馈类型数量失败", e);
            return ApiResponse.error("统计反馈类型数量失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计时间范围内的反馈数量
     * GET /api/feedback/count/time-range
     */
    @GetMapping("/count/time-range")
    public ApiResponse<Long> countFeedbacksByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            long count = feedbackService.countByTimeRange(start, end);
            return ApiResponse.success("统计时间范围反馈数量成功", count);
        } catch (Exception e) {
            logger.error("统计时间范围反馈数量失败", e);
            return ApiResponse.error("统计时间范围反馈数量失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计总反馈数量
     * GET /api/feedback/count
     */
    @GetMapping("/count")
    public ApiResponse<Long> countFeedbacks() {
        try {
            long count = feedbackService.count();
            return ApiResponse.success("统计总反馈数量成功", count);
        } catch (Exception e) {
            logger.error("统计总反馈数量失败", e);
            return ApiResponse.error("统计总反馈数量失败: " + e.getMessage());
        }
    }
}

