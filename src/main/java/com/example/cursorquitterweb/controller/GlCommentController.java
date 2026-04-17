package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CommentPageResult;
import com.example.cursorquitterweb.dto.CommentWithRepliesDTO;
import com.example.cursorquitterweb.dto.CommentWithRepliesPageResult;
import com.example.cursorquitterweb.dto.CreateCommentRequest;
import com.example.cursorquitterweb.dto.CreateReplyRequest;
import com.example.cursorquitterweb.dto.PageResponse;
import com.example.cursorquitterweb.dto.UpdateCommentRequest;
import com.example.cursorquitterweb.entity.GlComment;
import com.example.cursorquitterweb.service.GlCommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/gl/comments")
public class GlCommentController {

    @Autowired
    private GlCommentService commentService;

    @PostMapping("/create")
    public ApiResponse<GlComment> createComment(@RequestBody CreateCommentRequest request) {
        try {
            return ApiResponse.success("评论创建成功", commentService.createComment(
                request.getPostId(), request.getUserId(), request.getUserNickname(),
                request.getUserStage(), request.getAvatarUrl(), request.getContent()));
        } catch (Exception e) {
            return ApiResponse.error("创建评论失败: " + e.getMessage());
        }
    }

    @PostMapping("/reply")
    public ApiResponse<GlComment> createReply(@RequestBody CreateReplyRequest request) {
        try {
            if (request.getParentCommentId() == null || request.getParentCommentId().isEmpty()) {
                return ApiResponse.error("创建回复失败: parentCommentId不能为空");
            }
            if (request.getReplyToCommentId() == null || request.getReplyToCommentId().isEmpty()) {
                return ApiResponse.error("创建回复失败: replyToCommentId不能为空");
            }
            return ApiResponse.success("回复创建成功", commentService.createReplyComment(
                request.getPostId(), request.getUserId(), request.getUserNickname(), request.getUserStage(),
                request.getAvatarUrl(), request.getContent(), request.getParentCommentId(),
                request.getReplyToUserId(), request.getReplyToUserNickname(), request.getReplyToCommentId()));
        } catch (Exception e) {
            return ApiResponse.error("创建回复失败: " + e.getMessage());
        }
    }

    @GetMapping("/{commentId}")
    public ApiResponse<GlComment> getComment(@PathVariable UUID commentId) {
        try {
            Optional<GlComment> comment = commentService.findById(commentId);
            return comment.map(value -> ApiResponse.success("获取评论成功", value))
                .orElseGet(() -> ApiResponse.error("评论不存在"));
        } catch (Exception e) {
            return ApiResponse.error("获取评论失败: " + e.getMessage());
        }
    }

    @PutMapping("/{commentId}/update")
    public ApiResponse<GlComment> updateComment(@PathVariable UUID commentId, @RequestBody UpdateCommentRequest request) {
        try {
            return ApiResponse.success("评论更新成功", commentService.updateComment(commentId, request.getContent(), request.getAvatarUrl()));
        } catch (Exception e) {
            return ApiResponse.error("更新评论失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{commentId}/delete")
    public ApiResponse<String> deleteComment(@PathVariable UUID commentId) {
        try {
            commentService.deleteComment(commentId);
            return ApiResponse.success("评论删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除评论失败: " + e.getMessage());
        }
    }

    @GetMapping("/post/{postId}")
    public ApiResponse<List<GlComment>> getCommentsByPostId(@PathVariable UUID postId) {
        try {
            return ApiResponse.success("获取帖子评论成功", commentService.findByPostId(postId));
        } catch (Exception e) {
            return ApiResponse.error("获取帖子评论失败: " + e.getMessage());
        }
    }

    @GetMapping("/post/{postId}/page")
    public ApiResponse<List<GlComment>> getCommentsByPostIdPage(@PathVariable UUID postId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        try {
            return ApiResponse.success("获取帖子评论成功", commentService.findByPostId(postId, page, size));
        } catch (Exception e) {
            return ApiResponse.error("获取帖子评论失败: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<GlComment>> getCommentsByUserId(@PathVariable UUID userId) {
        try {
            return ApiResponse.success("获取用户评论成功", commentService.findByUserId(userId));
        } catch (Exception e) {
            return ApiResponse.error("获取用户评论失败: " + e.getMessage());
        }
    }

    @GetMapping("/nickname/{userNickname}")
    public ApiResponse<List<GlComment>> getCommentsByUserNickname(@PathVariable String userNickname) {
        try {
            return ApiResponse.success("获取用户评论成功", commentService.findByUserNickname(userNickname));
        } catch (Exception e) {
            return ApiResponse.error("获取用户评论失败: " + e.getMessage());
        }
    }

    @GetMapping("/stage/{userStage}")
    public ApiResponse<List<GlComment>> getCommentsByUserStage(@PathVariable String userStage) {
        try {
            return ApiResponse.success("获取用户阶段评论成功", commentService.findByUserStage(userStage));
        } catch (Exception e) {
            return ApiResponse.error("获取用户阶段评论失败: " + e.getMessage());
        }
    }

    @GetMapping("/search/content")
    public ApiResponse<List<GlComment>> searchCommentsByContent(@RequestParam String content) {
        try {
            return ApiResponse.success("搜索评论成功", commentService.searchByContent(content));
        } catch (Exception e) {
            return ApiResponse.error("搜索评论失败: " + e.getMessage());
        }
    }

    @GetMapping("/getAllComments")
    public ApiResponse<PageResponse<GlComment>> getAllComments(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "created_at") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            CommentPageResult result = commentService.getAllCommentsWithCount(page, size, sortBy, sortDir);
            return ApiResponse.success("获取评论列表成功", new PageResponse<>(
                castComments(result.getContent()), result.getTotalElements(), page, size));
        } catch (Exception e) {
            return ApiResponse.error("获取评论列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/time-range")
    public ApiResponse<List<GlComment>> getCommentsByTimeRange(@RequestParam String startTime, @RequestParam String endTime) {
        try {
            return ApiResponse.success("获取时间范围评论成功",
                commentService.findByTimeRange(OffsetDateTime.parse(startTime), OffsetDateTime.parse(endTime)));
        } catch (Exception e) {
            return ApiResponse.error("获取时间范围评论失败: " + e.getMessage());
        }
    }

    @GetMapping("/count/post/{postId}")
    public ApiResponse<Long> countCommentsByPostId(@PathVariable UUID postId) {
        try {
            return ApiResponse.success("统计帖子评论数量成功", commentService.countByPostId(postId));
        } catch (Exception e) {
            return ApiResponse.error("统计帖子评论数量失败: " + e.getMessage());
        }
    }

    @GetMapping("/count/user/{userId}")
    public ApiResponse<Long> countCommentsByUserId(@PathVariable UUID userId) {
        try {
            return ApiResponse.success("统计用户评论数量成功", commentService.countByUserId(userId));
        } catch (Exception e) {
            return ApiResponse.error("统计用户评论数量失败: " + e.getMessage());
        }
    }

    @GetMapping("/count/time-range")
    public ApiResponse<Long> countCommentsByTimeRange(@RequestParam String startTime, @RequestParam String endTime) {
        try {
            return ApiResponse.success("统计时间范围评论数量成功",
                commentService.countByTimeRange(OffsetDateTime.parse(startTime), OffsetDateTime.parse(endTime)));
        } catch (Exception e) {
            return ApiResponse.error("统计时间范围评论数量失败: " + e.getMessage());
        }
    }

    @GetMapping("/post/{postId}/top-level")
    public ApiResponse<List<GlComment>> getTopLevelComments(@PathVariable UUID postId) {
        try {
            return ApiResponse.success("获取一级评论成功", commentService.findTopLevelCommentsByPostId(postId));
        } catch (Exception e) {
            return ApiResponse.error("获取一级评论失败: " + e.getMessage());
        }
    }

    @GetMapping("/post/{postId}/top-level/page")
    public ApiResponse<List<GlComment>> getTopLevelCommentsPage(@PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        try {
            return ApiResponse.success("获取一级评论成功", commentService.findTopLevelCommentsByPostId(postId, page, size));
        } catch (Exception e) {
            return ApiResponse.error("获取一级评论失败: " + e.getMessage());
        }
    }

    @GetMapping("/{rootCommentId}/replies")
    public ApiResponse<List<GlComment>> getRepliesByRootCommentId(@PathVariable UUID rootCommentId) {
        try {
            return ApiResponse.success("获取回复成功", commentService.findRepliesByRootCommentId(rootCommentId));
        } catch (Exception e) {
            return ApiResponse.error("获取回复失败: " + e.getMessage());
        }
    }

    @GetMapping("/post/{postId}/with-replies")
    public ApiResponse<List<CommentWithRepliesDTO>> getCommentsWithReplies(@PathVariable UUID postId) {
        try {
            return ApiResponse.success("获取评论及回复成功", commentService.findCommentsWithRepliesByPostId(postId));
        } catch (Exception e) {
            return ApiResponse.error("获取评论及回复失败: " + e.getMessage());
        }
    }

    @GetMapping("/post/{postId}/with-replies/page")
    public ApiResponse<PageResponse<CommentWithRepliesDTO>> getCommentsWithRepliesPage(@PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "created_at") String sortBy, @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            CommentWithRepliesPageResult result = commentService.findCommentsWithRepliesByPostIdWithCount(postId, page, size, sortBy, sortDir);
            return ApiResponse.success("获取评论及回复成功", new PageResponse<>(result.getContent(), result.getTotalElements(), page, size));
        } catch (Exception e) {
            return ApiResponse.error("获取评论及回复失败: " + e.getMessage());
        }
    }

    @GetMapping("/{rootCommentId}/replies/count")
    public ApiResponse<Long> countRepliesByRootCommentId(@PathVariable UUID rootCommentId) {
        try {
            return ApiResponse.success("统计回复数量成功", commentService.countRepliesByRootCommentId(rootCommentId));
        } catch (Exception e) {
            return ApiResponse.error("统计回复数量失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{commentId}/delete-with-replies")
    public ApiResponse<String> deleteCommentAndReplies(@PathVariable UUID commentId) {
        try {
            commentService.deleteCommentAndReplies(commentId);
            return ApiResponse.success("评论及其回复删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除评论及其回复失败: " + e.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<GlComment> castComments(List<?> comments) {
        List<GlComment> result = new java.util.ArrayList<>();
        if (comments != null) {
            for (Object comment : comments) {
                result.add((GlComment) comment);
            }
        }
        return result;
    }
}
