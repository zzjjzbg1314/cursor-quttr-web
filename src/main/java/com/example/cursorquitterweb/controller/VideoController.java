package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateVideoRequest;
import com.example.cursorquitterweb.dto.UpdateVideoRequest;
import com.example.cursorquitterweb.dto.VideoDto;
import com.example.cursorquitterweb.entity.Video;
import com.example.cursorquitterweb.service.VideoService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 视频控制器
 * 提供视频的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/videos")
@Validated
public class VideoController {
    
    private static final Logger logger = LogUtil.getLogger(VideoController.class);
    
    @Autowired
    private VideoService videoService;
    
    /**
     * 创建新的视频
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<VideoDto>> createVideo(@Valid @RequestBody CreateVideoRequest request) {
        try {
            logger.info("创建视频，请求参数: {}", request);
            
            Video video = videoService.createVideo(
                request.getTitle(),
                request.getPlayurl(),
                request.getPosturl()
            );
            
            VideoDto dto = videoService.convertToDto(video);
            logger.info("视频创建成功，ID: {}", video.getId());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("视频创建成功", dto));
                    
        } catch (Exception e) {
            logger.error("创建视频失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取视频
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoDto>> getVideo(@PathVariable UUID id) {
        try {
            logger.info("获取视频，ID: {}", id);
            
            Optional<Video> optionalVideo = videoService.findById(id);
            if (optionalVideo.isPresent()) {
                VideoDto dto = videoService.convertToDto(optionalVideo.get());
                return ResponseEntity.ok(ApiResponse.success("获取视频成功", dto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("视频不存在"));
            }
            
        } catch (Exception e) {
            logger.error("获取视频失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新视频
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoDto>> updateVideo(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVideoRequest request) {
        try {
            logger.info("更新视频，ID: {}, 请求参数: {}", id, request);
            
            Video video = videoService.updateVideo(
                id,
                request.getTitle(),
                request.getPlayurl(),
                request.getPosturl()
            );
            
            VideoDto dto = videoService.convertToDto(video);
            logger.info("视频更新成功，ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("视频更新成功", dto));
            
        } catch (RuntimeException e) {
            logger.warn("更新视频失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("更新视频失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除视频
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteVideo(@PathVariable UUID id) {
        try {
            logger.info("删除视频，ID: {}", id);
            
            videoService.deleteVideo(id);
            logger.info("视频删除成功，ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("视频删除成功", null));
            
        } catch (RuntimeException e) {
            logger.warn("删除视频失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("删除视频失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有视频（分页）
     */
    @GetMapping("/getAllVideos")
    public ResponseEntity<ApiResponse<Page<VideoDto>>> getAllVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "createAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            logger.info("获取所有视频，页码: {}, 大小: {}, 排序: {} {}", page, size, sortBy, sortDir);
            
            // 验证排序字段名
            String[] validSortFields = {"id", "title", "playurl", "posturl", "createAt", "updateAt"};
            boolean isValidSortField = false;
            for (String field : validSortFields) {
                if (field.equals(sortBy)) {
                    isValidSortField = true;
                    break;
                }
            }
            
            if (!isValidSortField) {
                logger.warn("无效的排序字段: {}, 使用默认字段: createAt", sortBy);
                sortBy = "createAt";
            }
            
            // 验证排序方向
            if (!sortDir.equalsIgnoreCase("asc") && !sortDir.equalsIgnoreCase("desc")) {
                logger.warn("无效的排序方向: {}, 使用默认方向: desc", sortDir);
                sortDir = "desc";
            }
            
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            
            Page<Video> videos = videoService.getAllVideos(pageable);
            Page<VideoDto> dtoPage = videos.map(videoService::convertToDto);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频列表成功", dtoPage));
            
        } catch (Exception e) {
            logger.error("获取视频列表失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据标题搜索视频
     */
    @GetMapping("/search/title")
    public ResponseEntity<ApiResponse<List<VideoDto>>> searchVideosByTitle(@RequestParam String title) {
        try {
            logger.info("根据标题搜索视频，标题: {}", title);
            
            List<Video> videos = videoService.searchByTitle(title);
            List<VideoDto> dtoList = videoService.convertToDtoList(videos);
            
            return ResponseEntity.ok(ApiResponse.success("搜索视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据标题搜索视频失败，标题: {}, 错误: {}", title, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据播放链接获取视频
     */
    @GetMapping("/playurl/{playurl}")
    public ResponseEntity<ApiResponse<VideoDto>> getVideoByPlayurl(@PathVariable String playurl) {
        try {
            logger.info("根据播放链接获取视频，链接: {}", playurl);
            
            Optional<Video> optionalVideo = videoService.findByPlayurl(playurl);
            if (optionalVideo.isPresent()) {
                VideoDto dto = videoService.convertToDto(optionalVideo.get());
                return ResponseEntity.ok(ApiResponse.success("获取视频成功", dto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("视频不存在"));
            }
            
        } catch (Exception e) {
            logger.error("根据播放链接获取视频失败，链接: {}, 错误: {}", playurl, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据海报链接获取视频
     */
    @GetMapping("/posturl/{posturl}")
    public ResponseEntity<ApiResponse<VideoDto>> getVideoByPosturl(@PathVariable String posturl) {
        try {
            logger.info("根据海报链接获取视频，链接: {}", posturl);
            
            Optional<Video> optionalVideo = videoService.findByPosturl(posturl);
            if (optionalVideo.isPresent()) {
                VideoDto dto = videoService.convertToDto(optionalVideo.get());
                return ResponseEntity.ok(ApiResponse.success("获取视频成功", dto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("视频不存在"));
            }
            
        } catch (Exception e) {
            logger.error("根据海报链接获取视频失败，链接: {}, 错误: {}", posturl, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有播放链接的视频
     */
    @GetMapping("/with-playurl")
    public ResponseEntity<ApiResponse<List<VideoDto>>> getVideosWithPlayurl() {
        try {
            logger.info("获取有播放链接的视频");
            
            List<Video> videos = videoService.getVideosWithPlayurl();
            List<VideoDto> dtoList = videoService.convertToDtoList(videos);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有播放链接的视频失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有海报链接的视频
     */
    @GetMapping("/with-posturl")
    public ResponseEntity<ApiResponse<List<VideoDto>>> getVideosWithPosturl() {
        try {
            logger.info("获取有海报链接的视频");
            
            List<Video> videos = videoService.getVideosWithPosturl();
            List<VideoDto> dtoList = videoService.convertToDtoList(videos);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有海报链接的视频失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 统计视频总数
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> countVideos() {
        try {
            logger.info("统计视频总数");
            
            long count = videoService.count();
            
            return ResponseEntity.ok(ApiResponse.success("统计成功", count));
            
        } catch (Exception e) {
            logger.error("统计视频总数失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("统计失败: " + e.getMessage()));
        }
    }
}