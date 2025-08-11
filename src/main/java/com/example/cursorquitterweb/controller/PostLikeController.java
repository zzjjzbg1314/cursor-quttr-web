package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.entity.PostLike;
import com.example.cursorquitterweb.service.PostLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.UUID;

/**
 * 帖子点赞控制器
 */
@RestController
@RequestMapping("/api/post-likes")
public class PostLikeController {
    
    @Autowired
    private PostLikeService postLikeService;
    
    /**
     * 根据帖子ID获取点赞信息
     */
    @GetMapping("/{postId}")
    public ApiResponse<PostLike> getPostLike(@PathVariable UUID postId) {
        try {
            Optional<PostLike> postLike = postLikeService.findByPostId(postId);
            if (postLike.isPresent()) {
                return ApiResponse.success("获取帖子点赞信息成功", postLike.get());
            } else {
                return ApiResponse.error("帖子点赞信息不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取帖子点赞信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建帖子点赞记录
     */
    @PostMapping("/{postId}")
    public ApiResponse<PostLike> createPostLike(@PathVariable UUID postId) {
        try {
            PostLike postLike = postLikeService.createPostLike(postId);
            return ApiResponse.success("创建帖子点赞记录成功", postLike);
        } catch (Exception e) {
            return ApiResponse.error("创建帖子点赞记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 点赞帖子
     */
    @PostMapping("/{postId}/like")
    public ApiResponse<PostLike> likePost(@PathVariable UUID postId) {
        try {
            PostLike postLike = postLikeService.likePost(postId);
            return ApiResponse.success("点赞成功", postLike);
        } catch (Exception e) {
            return ApiResponse.error("点赞失败: " + e.getMessage());
        }
    }
    
    /**
     * 取消点赞帖子
     */
    @PostMapping("/{postId}/unlike")
    public ApiResponse<PostLike> unlikePost(@PathVariable UUID postId) {
        try {
            PostLike postLike = postLikeService.unlikePost(postId);
            if (postLike != null) {
                return ApiResponse.success("取消点赞成功", postLike);
            } else {
                return ApiResponse.error("帖子点赞记录不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("取消点赞失败: " + e.getMessage());
        }
    }
    
    /**
     * 设置帖子点赞数
     */
    @PutMapping("/{postId}/count")
    public ApiResponse<PostLike> setLikeCount(@PathVariable UUID postId, @RequestParam Integer likeCount) {
        try {
            PostLike postLike = postLikeService.setLikeCount(postId, likeCount);
            return ApiResponse.success("设置点赞数成功", postLike);
        } catch (Exception e) {
            return ApiResponse.error("设置点赞数失败: " + e.getMessage());
        }
    }
    
    /**
     * 重置帖子点赞数
     */
    @PutMapping("/{postId}/reset")
    public ApiResponse<PostLike> resetLikeCount(@PathVariable UUID postId) {
        try {
            PostLike postLike = postLikeService.resetLikeCount(postId);
            return ApiResponse.success("重置点赞数成功", postLike);
        } catch (Exception e) {
            return ApiResponse.error("重置点赞数失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取帖子点赞数
     */
    @GetMapping("/{postId}/count")
    public ApiResponse<Integer> getLikeCount(@PathVariable UUID postId) {
        try {
            Integer likeCount = postLikeService.getLikeCount(postId);
            return ApiResponse.success("获取点赞数成功", likeCount);
        } catch (Exception e) {
            return ApiResponse.error("获取点赞数失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据点赞数范围查找帖子
     */
    @GetMapping("/range")
    public ApiResponse<List<PostLike>> findByLikeCountRange(
            @RequestParam(required = false) Integer minCount,
            @RequestParam(required = false) Integer maxCount) {
        try {
            List<PostLike> postLikes = postLikeService.findByLikeCountRange(minCount, maxCount);
            return ApiResponse.success("查询成功", postLikes);
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 查找点赞数大于指定值的帖子
     */
    @GetMapping("/greater-than")
    public ApiResponse<List<PostLike>> findByLikeCountGreaterThan(@RequestParam Integer minCount) {
        try {
            List<PostLike> postLikes = postLikeService.findByLikeCountGreaterThan(minCount);
            return ApiResponse.success("查询成功", postLikes);
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 查找点赞数小于指定值的帖子
     */
    @GetMapping("/less-than")
    public ApiResponse<List<PostLike>> findByLikeCountLessThan(@RequestParam Integer maxCount) {
        try {
            List<PostLike> postLikes = postLikeService.findByLikeCountLessThan(maxCount);
            return ApiResponse.success("查询成功", postLikes);
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据时间范围查找帖子点赞信息
     */
    @GetMapping("/time-range")
    public ApiResponse<List<PostLike>> findByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            List<PostLike> postLikes = postLikeService.findByTimeRange(start, end);
            return ApiResponse.success("查询成功", postLikes);
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计总点赞数
     */
    @GetMapping("/stats/total")
    public ApiResponse<Long> getTotalLikeCount() {
        try {
            Long totalCount = postLikeService.getTotalLikeCount();
            return ApiResponse.success("统计总点赞数成功", totalCount);
        } catch (Exception e) {
            return ApiResponse.error("统计总点赞数失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计平均点赞数
     */
    @GetMapping("/stats/average")
    public ApiResponse<Double> getAverageLikeCount() {
        try {
            Double averageCount = postLikeService.getAverageLikeCount();
            return ApiResponse.success("统计平均点赞数成功", averageCount);
        } catch (Exception e) {
            return ApiResponse.error("统计平均点赞数失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取点赞数最多的前N个帖子
     */
    @GetMapping("/top")
    public ApiResponse<List<PostLike>> getTopLikedPosts(@RequestParam(defaultValue = "10") int limit) {
        try {
            List<PostLike> topPosts = postLikeService.getTopLikedPosts(limit);
            return ApiResponse.success("获取热门帖子成功", topPosts);
        } catch (Exception e) {
            return ApiResponse.error("获取热门帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量创建帖子点赞记录
     */
    @PostMapping("/batch")
    public ApiResponse<List<PostLike>> createPostLikesForPosts(@RequestBody List<UUID> postIds) {
        try {
            List<PostLike> createdLikes = postLikeService.createPostLikesForPosts(postIds);
            return ApiResponse.success("批量创建帖子点赞记录成功", createdLikes);
        } catch (Exception e) {
            return ApiResponse.error("批量创建帖子点赞记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除帖子点赞记录
     */
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
