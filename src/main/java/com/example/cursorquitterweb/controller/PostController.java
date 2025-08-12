package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreatePostRequest;
import com.example.cursorquitterweb.dto.PostWithUpvotesDto;
import com.example.cursorquitterweb.dto.UpdatePostRequest;
import com.example.cursorquitterweb.entity.Post;
import com.example.cursorquitterweb.service.PostService;
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
 * 帖子控制器
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {
    
    @Autowired
    private PostService postService;
    
    /**
     * 创建新帖子
     */
    @PostMapping("/create")
    public ApiResponse<Post> createPost(@RequestBody CreatePostRequest request) {
        try {
            Post post = postService.createPost(
                request.getUserId(),
                request.getUserNickname(),
                request.getUserStage(),
                request.getTitle(),
                request.getContent()
            );
            return ApiResponse.success("帖子创建成功", post);
        } catch (Exception e) {
            return ApiResponse.error("创建帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取帖子
     */
    @GetMapping("/{postId}")
    public ApiResponse<Post> getPost(@PathVariable UUID postId) {
        try {
            Optional<Post> post = postService.findById(postId);
            if (post.isPresent()) {
                return ApiResponse.success("获取帖子成功", post.get());
            } else {
                return ApiResponse.error("帖子不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新帖子
     */
    @PutMapping("/{postId}/update")
    public ApiResponse<Post> updatePost(@PathVariable UUID postId, @RequestBody UpdatePostRequest request) {
        try {
            Post post = postService.updatePost(postId, request.getTitle(), request.getContent());
            return ApiResponse.success("帖子更新成功", post);
        } catch (Exception e) {
            return ApiResponse.error("更新帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除帖子
     */
    @DeleteMapping("/{postId}/delete")
    public ApiResponse<String> deletePost(@PathVariable UUID postId) {
        try {
            postService.deletePost(postId);
            return ApiResponse.success("帖子删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有帖子（分页，包含点赞数）
     */
    @GetMapping("/getAllPosts")
    public ApiResponse<Page<PostWithUpvotesDto>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<PostWithUpvotesDto> posts = postService.getAllPostsWithUpvotes(pageable);
            return ApiResponse.success("获取帖子列表成功", posts);
        } catch (Exception e) {
            return ApiResponse.error("获取帖子列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有帖子（包含点赞数，不分页）
     */
    @GetMapping("/getAllPostsList")
    public ApiResponse<List<PostWithUpvotesDto>> getAllPostsList() {
        try {
            List<PostWithUpvotesDto> posts = postService.getAllPostsWithUpvotes();
            return ApiResponse.success("获取帖子列表成功", posts);
        } catch (Exception e) {
            return ApiResponse.error("获取帖子列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户ID获取帖子
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<Post>> getPostsByUserId(@PathVariable UUID userId) {
        try {
            List<Post> posts = postService.findByUserId(userId);
            return ApiResponse.success("获取用户帖子成功", posts);
        } catch (Exception e) {
            return ApiResponse.error("获取用户帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户昵称获取帖子
     */
    @GetMapping("/nickname/{userNickname}")
    public ApiResponse<List<Post>> getPostsByUserNickname(@PathVariable String userNickname) {
        try {
            List<Post> posts = postService.findByUserNickname(userNickname);
            return ApiResponse.success("获取用户帖子成功", posts);
        } catch (Exception e) {
            return ApiResponse.error("获取用户帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户阶段获取帖子
     */
    @GetMapping("/stage/{userStage}")
    public ApiResponse<List<Post>> getPostsByUserStage(@PathVariable String userStage) {
        try {
            List<Post> posts = postService.findByUserStage(userStage);
            return ApiResponse.success("获取用户阶段帖子成功", posts);
        } catch (Exception e) {
            return ApiResponse.error("获取用户阶段帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 搜索帖子（按标题）
     */
    @GetMapping("/search/title")
    public ApiResponse<List<Post>> searchPostsByTitle(@RequestParam String title) {
        try {
            List<Post> posts = postService.searchByTitle(title);
            return ApiResponse.success("搜索帖子成功", posts);
        } catch (Exception e) {
            return ApiResponse.error("搜索帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 搜索帖子（按内容）
     */
    @GetMapping("/search/content")
    public ApiResponse<List<Post>> searchPostsByContent(@RequestParam String content) {
        try {
            List<Post> posts = postService.searchByContent(content);
            return ApiResponse.success("搜索帖子成功", posts);
        } catch (Exception e) {
            return ApiResponse.error("搜索帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据时间范围获取帖子
     */
    @GetMapping("/time-range")
    public ApiResponse<List<Post>> getPostsByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            List<Post> posts = postService.findByTimeRange(start, end);
            return ApiResponse.success("获取时间范围帖子成功", posts);
        } catch (Exception e) {
            return ApiResponse.error("获取时间范围帖子失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计用户的帖子数量
     */
    @GetMapping("/count/user/{userId}")
    public ApiResponse<Long> countPostsByUserId(@PathVariable UUID userId) {
        try {
            long count = postService.countByUserId(userId);
            return ApiResponse.success("统计用户帖子数量成功", count);
        } catch (Exception e) {
            return ApiResponse.error("统计用户帖子数量失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计时间范围内的帖子数量
     */
    @GetMapping("/count/time-range")
    public ApiResponse<Long> countPostsByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startTime);
            OffsetDateTime end = OffsetDateTime.parse(endTime);
            long count = postService.countByTimeRange(start, end);
            return ApiResponse.success("统计时间范围帖子数量成功", count);
        } catch (Exception e) {
            return ApiResponse.error("统计时间范围帖子数量失败: " + e.getMessage());
        }
    }
}
