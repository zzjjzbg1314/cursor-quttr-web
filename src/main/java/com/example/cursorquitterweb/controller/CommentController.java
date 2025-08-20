package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateCommentRequest;
import com.example.cursorquitterweb.dto.UpdateCommentRequest;
import com.example.cursorquitterweb.entity.Comment;
import com.example.cursorquitterweb.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 评论控制器
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;
    
    /**
     * 创建新评论
     */
    @PostMapping("/create")
    public ApiResponse<Comment> createComment(@RequestBody CreateCommentRequest request) {
        try {
            Comment comment = commentService.createComment(
                request.getPostId(),
                request.getUserId(),
                request.getUserNickname(),
                request.getUserStage(),
                request.getAvatarUrl(),
                request.getContent()
            );
            return ApiResponse.success("评论创建成功", comment);
        } catch (Exception e) {
            return ApiResponse.error("创建评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取评论
     */
    @GetMapping("/{commentId}")
    public ApiResponse<Comment> getComment(@PathVariable UUID commentId) {
        try {
            Optional<Comment> comment = commentService.findById(commentId);
            if (comment.isPresent()) {
                return ApiResponse.success("获取评论成功", comment.get());
            } else {
                return ApiResponse.error("评论不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新评论
     */
    @PutMapping("/{commentId}/update")
    public ApiResponse<Comment> updateComment(@PathVariable UUID commentId, @RequestBody UpdateCommentRequest request) {
        try {
            Comment comment = commentService.updateComment(commentId, request.getContent(), request.getAvatarUrl());
            return ApiResponse.success("评论更新成功", comment);
        } catch (Exception e) {
            return ApiResponse.error("更新评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除评论
     */
    @DeleteMapping("/{commentId}/delete")
    public ApiResponse<String> deleteComment(@PathVariable UUID commentId) {
        try {
            commentService.deleteComment(commentId);
            return ApiResponse.success("评论删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据帖子ID获取评论列表
     */
    @GetMapping("/post/{postId}")
    public ApiResponse<List<Comment>> getCommentsByPostId(@PathVariable UUID postId) {
        try {
            List<Comment> comments = commentService.findByPostId(postId);
            return ApiResponse.success("获取帖子评论成功", comments);
        } catch (Exception e) {
            return ApiResponse.error("获取帖子评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据帖子ID分页获取评论
     */
    @GetMapping("/post/{postId}/page")
    public ApiResponse<Page<Comment>> getCommentsByPostIdPage(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<Comment> comments = commentService.findByPostId(postId, pageable);
            return ApiResponse.success("获取帖子评论成功", comments);
        } catch (Exception e) {
            return ApiResponse.error("获取帖子评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户ID获取评论
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<Comment>> getCommentsByUserId(@PathVariable UUID userId) {
        try {
            List<Comment> comments = commentService.findByUserId(userId);
            return ApiResponse.success("获取用户评论成功", comments);
        } catch (Exception e) {
            return ApiResponse.error("获取用户评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户昵称获取评论
     */
    @GetMapping("/nickname/{userNickname}")
    public ApiResponse<List<Comment>> getCommentsByUserNickname(@PathVariable String userNickname) {
        try {
            List<Comment> comments = commentService.findByUserNickname(userNickname);
            return ApiResponse.success("获取用户评论成功", comments);
        } catch (Exception e) {
            return ApiResponse.error("获取用户评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户阶段获取评论
     */
    @GetMapping("/stage/{userStage}")
    public ApiResponse<List<Comment>> getCommentsByUserStage(@PathVariable String userStage) {
        try {
            List<Comment> comments = commentService.findByUserStage(userStage);
            return ApiResponse.success("获取用户阶段评论成功", comments);
        } catch (Exception e) {
            return ApiResponse.error("获取用户阶段评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 搜索评论（按内容）
     */
    @GetMapping("/search/content")
    public ApiResponse<List<Comment>> searchCommentsByContent(@RequestParam String content) {
        try {
            List<Comment> comments = commentService.searchByContent(content);
            return ApiResponse.success("搜索评论成功", comments);
        } catch (Exception e) {
            return ApiResponse.error("搜索评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有评论（分页）
     */
    @GetMapping("/getAllComments")
    public ApiResponse<Page<Comment>> getAllComments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<Comment> comments = commentService.getAllComments(pageable);
            return ApiResponse.success("获取评论列表成功", comments);
        } catch (Exception e) {
            return ApiResponse.error("获取评论列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据时间范围获取评论
     */
    @GetMapping("/time-range")
    public ApiResponse<List<Comment>> getCommentsByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            List<Comment> comments = commentService.findByTimeRange(start, end);
            return ApiResponse.success("获取时间范围评论成功", comments);
        } catch (Exception e) {
            return ApiResponse.error("获取时间范围评论失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计帖子的评论数量
     */
    @GetMapping("/count/post/{postId}")
    public ApiResponse<Long> countCommentsByPostId(@PathVariable UUID postId) {
        try {
            long count = commentService.countByPostId(postId);
            return ApiResponse.success("统计帖子评论数量成功", count);
        } catch (Exception e) {
            return ApiResponse.error("统计帖子评论数量失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计用户的评论数量
     */
    @GetMapping("/count/user/{userId}")
    public ApiResponse<Long> countCommentsByUserId(@PathVariable UUID userId) {
        try {
            long count = commentService.countByUserId(userId);
            return ApiResponse.success("统计用户评论数量成功", count);
        } catch (Exception e) {
            return ApiResponse.error("统计用户评论数量失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计时间范围内的评论数量
     */
    @GetMapping("/count/time-range")
    public ApiResponse<Long> countCommentsByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            long count = commentService.countByTimeRange(start, end);
            return ApiResponse.success("统计时间范围评论数量成功", count);
        } catch (Exception e) {
            return ApiResponse.error("统计时间范围评论数量失败: " + e.getMessage());
        }
    }
}
