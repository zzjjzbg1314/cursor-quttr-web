package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateSisterMotivationVideoRequest;
import com.example.cursorquitterweb.dto.PageResponse;
import com.example.cursorquitterweb.dto.SisterMotivationVideoDto;
import com.example.cursorquitterweb.dto.SisterMotivationVideoPageResult;
import com.example.cursorquitterweb.dto.UpdateSisterMotivationVideoRequest;
import com.example.cursorquitterweb.entity.SisterMotivationVideo;
import com.example.cursorquitterweb.service.SisterMotivationVideoService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

/**
 * 姐姐激励视频控制器
 */
@RestController
@RequestMapping("/api/sister-motivation-videos")
@Validated
public class SisterMotivationVideoController {

    private static final Logger logger = LogUtil.getLogger(SisterMotivationVideoController.class);

    @Autowired
    private SisterMotivationVideoService sisterMotivationVideoService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<SisterMotivationVideoDto>> createVideo(
            @Valid @RequestBody CreateSisterMotivationVideoRequest request) {
        try {
            SisterMotivationVideo video = sisterMotivationVideoService.createVideo(request);
            SisterMotivationVideoDto dto = sisterMotivationVideoService.convertToDto(video);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("姐姐激励视频创建成功", dto));
        } catch (Exception e) {
            logger.error("创建姐姐激励视频失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建姐姐激励视频失败: " + e.getMessage()));
        }
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<SisterMotivationVideoDto>> getVideo(@PathVariable String videoId) {
        try {
            Optional<SisterMotivationVideo> optionalVideo = sisterMotivationVideoService.findById(videoId);
            if (optionalVideo.isPresent()) {
                SisterMotivationVideoDto dto = sisterMotivationVideoService.convertToDto(optionalVideo.get());
                return ResponseEntity.ok(ApiResponse.success("获取姐姐激励视频成功", dto));
            }

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("姐姐激励视频不存在"));
        } catch (Exception e) {
            logger.error("获取姐姐激励视频失败，ID: {}, 错误: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取姐姐激励视频失败: " + e.getMessage()));
        }
    }

    @PutMapping("/{videoId}")
    public ResponseEntity<ApiResponse<SisterMotivationVideoDto>> updateVideo(
            @PathVariable String videoId,
            @Valid @RequestBody UpdateSisterMotivationVideoRequest request) {
        try {
            SisterMotivationVideo video = sisterMotivationVideoService.updateVideo(videoId, request);
            SisterMotivationVideoDto dto = sisterMotivationVideoService.convertToDto(video);
            return ResponseEntity.ok(ApiResponse.success("姐姐激励视频更新成功", dto));
        } catch (RuntimeException e) {
            logger.warn("更新姐姐激励视频失败，ID: {}, 错误: {}", videoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("更新姐姐激励视频失败，ID: {}, 错误: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新姐姐激励视频失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{videoId}")
    public ResponseEntity<ApiResponse<Void>> deleteVideo(@PathVariable String videoId) {
        try {
            sisterMotivationVideoService.deleteVideo(videoId);
            return ResponseEntity.ok(ApiResponse.success("姐姐激励视频删除成功", null));
        } catch (RuntimeException e) {
            logger.warn("删除姐姐激励视频失败，ID: {}, 错误: {}", videoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("删除姐姐激励视频失败，ID: {}, 错误: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除姐姐激励视频失败: " + e.getMessage()));
        }
    }

    @GetMapping("/getAllVideos")
    @Cacheable(value = "sisterMotivationVideos", key = "#page + '_' + #size")
    public ResponseEntity<ApiResponse<PageResponse<SisterMotivationVideoDto>>> getAllVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        try {
            SisterMotivationVideoPageResult result = sisterMotivationVideoService.getAllVideosWithCount(page, size);
            PageResponse<SisterMotivationVideoDto> pageResponse = new PageResponse<>(
                    result.getContent(),
                    result.getTotalElements(),
                    page,
                    size
            );

            return ResponseEntity.ok(ApiResponse.success("获取姐姐激励视频列表成功", pageResponse));
        } catch (Exception e) {
            logger.error("获取姐姐激励视频列表失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取姐姐激励视频列表失败: " + e.getMessage()));
        }
    }

    @GetMapping("/list")
    @Cacheable(value = "sisterMotivationVideos", key = "'list'")
    public ResponseEntity<ApiResponse<List<SisterMotivationVideoDto>>> getAllVideosList() {
        try {
            List<SisterMotivationVideo> videos = sisterMotivationVideoService.getAllVideos();
            List<SisterMotivationVideoDto> dtoList = sisterMotivationVideoService.convertToDtoList(videos);
            return ResponseEntity.ok(ApiResponse.success("获取姐姐激励视频列表成功", dtoList));
        } catch (Exception e) {
            logger.error("获取姐姐激励视频列表失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取姐姐激励视频列表失败: " + e.getMessage()));
        }
    }

    @GetMapping("/search/title")
    public ResponseEntity<ApiResponse<List<SisterMotivationVideoDto>>> searchVideosByTitle(@RequestParam String title) {
        try {
            List<SisterMotivationVideo> videos = sisterMotivationVideoService.searchByTitle(title);
            List<SisterMotivationVideoDto> dtoList = sisterMotivationVideoService.convertToDtoList(videos);
            return ResponseEntity.ok(ApiResponse.success("搜索姐姐激励视频成功", dtoList));
        } catch (Exception e) {
            logger.error("搜索姐姐激励视频失败，标题: {}, 错误: {}", title, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索姐姐激励视频失败: " + e.getMessage()));
        }
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> countVideos() {
        try {
            long count = sisterMotivationVideoService.count();
            return ResponseEntity.ok(ApiResponse.success("统计姐姐激励视频成功", count));
        } catch (Exception e) {
            logger.error("统计姐姐激励视频失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("统计姐姐激励视频失败: " + e.getMessage()));
        }
    }
}
