package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.PageResponse;
import com.example.cursorquitterweb.dto.PostPageResult;
import com.example.cursorquitterweb.dto.PostWithUpvotesDto;
import com.example.cursorquitterweb.dto.UpdatePostRequest;
import com.example.cursorquitterweb.dto.CreatePostRequest;
import com.example.cursorquitterweb.entity.GlPost;
import com.example.cursorquitterweb.service.GlPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/gl/posts")
public class GlPostController {

    @Autowired
    private GlPostService postService;

    @PostMapping("/create")
    public ApiResponse<GlPost> createPost(@RequestBody CreatePostRequest request) {
        try {
            GlPost post = postService.createPost(
                request.getUserId(),
                request.getUserNickname(),
                request.getUserStage(),
                request.getAvatarUrl(),
                request.getContent()
            );
            return ApiResponse.success("帖子创建成功", post);
        } catch (Exception e) {
            return ApiResponse.error("创建帖子失败: " + e.getMessage());
        }
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostWithUpvotesDto> getPost(@PathVariable UUID postId) {
        try {
            Optional<PostWithUpvotesDto> post = postService.findByIdWithUpvotes(postId);
            return post.map(value -> ApiResponse.success("获取帖子成功", value))
                .orElseGet(() -> ApiResponse.error("帖子不存在"));
        } catch (Exception e) {
            return ApiResponse.error("获取帖子失败: " + e.getMessage());
        }
    }

    @PutMapping("/{postId}/update")
    public ApiResponse<GlPost> updatePost(@PathVariable UUID postId, @RequestBody UpdatePostRequest request) {
        try {
            return ApiResponse.success("帖子更新成功", postService.updatePost(postId, request.getContent()));
        } catch (Exception e) {
            return ApiResponse.error("更新帖子失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{postId}/delete")
    public ApiResponse<String> deletePost(@PathVariable UUID postId) {
        try {
            postService.deletePost(postId);
            return ApiResponse.success("帖子删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除帖子失败: " + e.getMessage());
        }
    }

    @GetMapping("/getAllPosts")
    public ApiResponse<PageResponse<PostWithUpvotesDto>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "created_at") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            PostPageResult result = postService.getAllPostsWithUpvotesAndCount(page, size, sortBy, sortDir);
            return ApiResponse.success("获取帖子列表成功", new PageResponse<>(
                result.getContent(), result.getTotalElements(), page, size));
        } catch (Exception e) {
            return ApiResponse.error("获取帖子列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/getAllPostsList")
    public ApiResponse<List<PostWithUpvotesDto>> getAllPostsList() {
        try {
            return ApiResponse.success("获取帖子列表成功", postService.getAllPostsWithUpvotes());
        } catch (Exception e) {
            return ApiResponse.error("获取帖子列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<GlPost>> getPostsByUserId(@PathVariable UUID userId) {
        try {
            return ApiResponse.success("获取用户帖子成功", postService.findByUserId(userId));
        } catch (Exception e) {
            return ApiResponse.error("获取用户帖子失败: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}/page")
    public ApiResponse<List<PostWithUpvotesDto>> getPostsByUserIdPage(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            return ApiResponse.success("获取用户帖子成功", postService.findByUserIdWithUpvotes(userId, page, size));
        } catch (Exception e) {
            return ApiResponse.error("获取用户帖子失败: " + e.getMessage());
        }
    }

    @GetMapping("/nickname/{userNickname}")
    public ApiResponse<List<GlPost>> getPostsByUserNickname(@PathVariable String userNickname) {
        try {
            return ApiResponse.success("获取用户帖子成功", postService.findByUserNickname(userNickname));
        } catch (Exception e) {
            return ApiResponse.error("获取用户帖子失败: " + e.getMessage());
        }
    }

    @GetMapping("/stage/{userStage}")
    public ApiResponse<List<GlPost>> getPostsByUserStage(@PathVariable String userStage) {
        try {
            return ApiResponse.success("获取用户阶段帖子成功", postService.findByUserStage(userStage));
        } catch (Exception e) {
            return ApiResponse.error("获取用户阶段帖子失败: " + e.getMessage());
        }
    }

    @GetMapping("/search/content")
    public ApiResponse<List<GlPost>> searchPostsByContent(@RequestParam String content) {
        try {
            return ApiResponse.success("搜索帖子成功", postService.searchByContent(content));
        } catch (Exception e) {
            return ApiResponse.error("搜索帖子失败: " + e.getMessage());
        }
    }

    @GetMapping("/time-range")
    public ApiResponse<List<GlPost>> getPostsByTimeRange(@RequestParam String startTime, @RequestParam String endTime) {
        try {
            return ApiResponse.success("获取时间范围帖子成功", postService.findByTimeRange(OffsetDateTime.parse(startTime), OffsetDateTime.parse(endTime)));
        } catch (Exception e) {
            return ApiResponse.error("获取时间范围帖子失败: " + e.getMessage());
        }
    }

    @GetMapping("/count/user/{userId}")
    public ApiResponse<Long> countPostsByUserId(@PathVariable UUID userId) {
        try {
            return ApiResponse.success("统计用户帖子数量成功", postService.countByUserId(userId));
        } catch (Exception e) {
            return ApiResponse.error("统计用户帖子数量失败: " + e.getMessage());
        }
    }

    @GetMapping("/count/time-range")
    public ApiResponse<Long> countPostsByTimeRange(@RequestParam String startTime, @RequestParam String endTime) {
        try {
            return ApiResponse.success("统计时间范围帖子数量成功",
                postService.countByTimeRange(OffsetDateTime.parse(startTime), OffsetDateTime.parse(endTime)));
        } catch (Exception e) {
            return ApiResponse.error("统计时间范围帖子数量失败: " + e.getMessage());
        }
    }
}
