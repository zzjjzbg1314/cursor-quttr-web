package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.entity.GlPostLike;
import com.example.cursorquitterweb.service.GlPostLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/gl/post-likes")
public class GlPostLikeController {

    @Autowired
    private GlPostLikeService postLikeService;

    @GetMapping("/{postId}")
    public ApiResponse<GlPostLike> getPostLike(@PathVariable UUID postId) {
        try {
            Optional<GlPostLike> postLike = postLikeService.findByPostId(postId);
            return postLike.map(value -> ApiResponse.success("获取帖子点赞信息成功", value))
                .orElseGet(() -> ApiResponse.error("帖子点赞信息不存在"));
        } catch (Exception e) {
            return ApiResponse.error("获取帖子点赞信息失败: " + e.getMessage());
        }
    }

    @PostMapping("/{postId}")
    public ApiResponse<GlPostLike> createPostLike(@PathVariable UUID postId) {
        try {
            return ApiResponse.success("创建帖子点赞记录成功", postLikeService.createPostLike(postId));
        } catch (Exception e) {
            return ApiResponse.error("创建帖子点赞记录失败: " + e.getMessage());
        }
    }

    @PostMapping("/{postId}/like")
    public ApiResponse<GlPostLike> likePost(@PathVariable String postId) {
        try {
            return ApiResponse.success("点赞成功", postLikeService.likePost(UUID.fromString(postId)));
        } catch (Exception e) {
            return ApiResponse.error("点赞失败: " + e.getMessage());
        }
    }

    @PostMapping("/{postId}/unlike")
    public ApiResponse<GlPostLike> unlikePost(@PathVariable String postId) {
        try {
            GlPostLike postLike = postLikeService.unlikePost(UUID.fromString(postId));
            return postLike != null ? ApiResponse.success("取消点赞成功", postLike) : ApiResponse.error("帖子点赞记录不存在");
        } catch (Exception e) {
            return ApiResponse.error("取消点赞失败: " + e.getMessage());
        }
    }

    @PutMapping("/{postId}/count")
    public ApiResponse<GlPostLike> setLikeCount(@PathVariable UUID postId, @RequestParam Integer likeCount) {
        try {
            return ApiResponse.success("设置点赞数成功", postLikeService.setLikeCount(postId, likeCount));
        } catch (Exception e) {
            return ApiResponse.error("设置点赞数失败: " + e.getMessage());
        }
    }

    @PutMapping("/{postId}/reset")
    public ApiResponse<GlPostLike> resetLikeCount(@PathVariable UUID postId) {
        try {
            return ApiResponse.success("重置点赞数成功", postLikeService.resetLikeCount(postId));
        } catch (Exception e) {
            return ApiResponse.error("重置点赞数失败: " + e.getMessage());
        }
    }

    @GetMapping("/{postId}/count")
    public ApiResponse<Integer> getLikeCount(@PathVariable UUID postId) {
        try {
            return ApiResponse.success("获取点赞数成功", postLikeService.getLikeCount(postId));
        } catch (Exception e) {
            return ApiResponse.error("获取点赞数失败: " + e.getMessage());
        }
    }

    @GetMapping("/range")
    public ApiResponse<List<GlPostLike>> findByLikeCountRange(@RequestParam(required = false) Integer minCount,
            @RequestParam(required = false) Integer maxCount) {
        try {
            return ApiResponse.success("查询成功", postLikeService.findByLikeCountRange(minCount, maxCount));
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/greater-than")
    public ApiResponse<List<GlPostLike>> findByLikeCountGreaterThan(@RequestParam Integer minCount) {
        try {
            return ApiResponse.success("查询成功", postLikeService.findByLikeCountGreaterThan(minCount));
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/less-than")
    public ApiResponse<List<GlPostLike>> findByLikeCountLessThan(@RequestParam Integer maxCount) {
        try {
            return ApiResponse.success("查询成功", postLikeService.findByLikeCountLessThan(maxCount));
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/time-range")
    public ApiResponse<List<GlPostLike>> findByTimeRange(@RequestParam String startTime, @RequestParam String endTime) {
        try {
            return ApiResponse.success("查询成功",
                postLikeService.findByTimeRange(OffsetDateTime.parse(startTime), OffsetDateTime.parse(endTime)));
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats/total")
    public ApiResponse<Long> getTotalLikeCount() {
        try {
            return ApiResponse.success("统计总点赞数成功", postLikeService.getTotalLikeCount());
        } catch (Exception e) {
            return ApiResponse.error("统计总点赞数失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats/average")
    public ApiResponse<Double> getAverageLikeCount() {
        try {
            return ApiResponse.success("统计平均点赞数成功", postLikeService.getAverageLikeCount());
        } catch (Exception e) {
            return ApiResponse.error("统计平均点赞数失败: " + e.getMessage());
        }
    }

    @GetMapping("/top")
    public ApiResponse<List<GlPostLike>> getTopLikedPosts(@RequestParam(defaultValue = "10") int limit) {
        try {
            return ApiResponse.success("获取热门帖子成功", postLikeService.getTopLikedPosts(limit));
        } catch (Exception e) {
            return ApiResponse.error("获取热门帖子失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch")
    public ApiResponse<List<GlPostLike>> createPostLikesForPosts(@RequestBody List<UUID> postIds) {
        try {
            return ApiResponse.success("批量创建帖子点赞记录成功", postLikeService.createPostLikesForPosts(postIds));
        } catch (Exception e) {
            return ApiResponse.error("批量创建帖子点赞记录失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<String> deletePostLike(@PathVariable UUID postId) {
        try {
            postLikeService.deletePostLike(postId);
            return ApiResponse.success("删除帖子点赞记录成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除帖子点赞记录失败: " + e.getMessage());
        }
    }
}
