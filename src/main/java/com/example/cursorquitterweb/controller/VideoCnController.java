package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateVideoRequest;
import com.example.cursorquitterweb.dto.PageResponse;
import com.example.cursorquitterweb.dto.UpdateVideoRequest;
import com.example.cursorquitterweb.dto.VideoCnDto;
import com.example.cursorquitterweb.dto.VideoCnPageResult;
import com.example.cursorquitterweb.entity.VideoCn;
import com.example.cursorquitterweb.service.VideoCnService;
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
import java.util.UUID;

/**
 * 视频控制器（中文版）
 * 提供视频的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/videos/cn")
@Validated
public class VideoCnController {
    
    private static final Logger logger = LogUtil.getLogger(VideoCnController.class);
    
    @Autowired
    private VideoCnService videoCnService;
    
    /**
     * 创建新的视频
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<VideoCnDto>> createVideo(@Valid @RequestBody CreateVideoRequest request) {
        try {
            logger.info("创建视频（中文版），请求参数: {}", request);
            
            VideoCn video = videoCnService.createVideo(
                request.getTitle(),
                request.getPlayurl(),
                request.getPosturl()
            );
            
            VideoCnDto dto = videoCnService.convertToDto(video);
            logger.info("视频创建成功（中文版），ID: {}", video.getId());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("视频创建成功", dto));
                    
        } catch (Exception e) {
            logger.error("创建视频失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取视频
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoCnDto>> getVideo(@PathVariable UUID id) {
        try {
            logger.info("获取视频（中文版），ID: {}", id);
            
            Optional<VideoCn> optionalVideo = videoCnService.findById(id);
            if (optionalVideo.isPresent()) {
                VideoCnDto dto = videoCnService.convertToDto(optionalVideo.get());
                return ResponseEntity.ok(ApiResponse.success("获取视频成功", dto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("视频不存在"));
            }
            
        } catch (Exception e) {
            logger.error("获取视频失败（中文版），ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新视频
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoCnDto>> updateVideo(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVideoRequest request) {
        try {
            logger.info("更新视频（中文版），ID: {}, 请求参数: {}", id, request);
            
            VideoCn video = videoCnService.updateVideo(
                id,
                request.getTitle(),
                request.getPlayurl(),
                request.getPosturl()
            );
            
            VideoCnDto dto = videoCnService.convertToDto(video);
            logger.info("视频更新成功（中文版），ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("视频更新成功", dto));
            
        } catch (RuntimeException e) {
            logger.warn("更新视频失败（中文版），ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("更新视频失败（中文版），ID: {}, 错误: {}", id, e.getMessage(), e);
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
            logger.info("删除视频（中文版），ID: {}", id);
            
            videoCnService.deleteVideo(id);
            logger.info("视频删除成功（中文版），ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("视频删除成功", null));
            
        } catch (RuntimeException e) {
            logger.warn("删除视频失败（中文版），ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("删除视频失败（中文版），ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有视频（分页）
     * 返回格式: { "data": { "content": [...], ... } }
     * 优化：使用窗口函数在单次查询中同时获取数据和总数，避免2次数据库查询
     * 使用缓存，缓存键包含 page 和 size 参数
     */
    @GetMapping("/getAllVideos")
    @Cacheable(value = "videos", key = "'cn_' + #page + '_' + #size")
    public ResponseEntity<ApiResponse<PageResponse<VideoCnDto>>> getAllVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        try {
            logger.info("获取所有视频（中文版），页码: {}, 大小: {}", page, size);
            
            // 使用单次查询获取数据和总数
            VideoCnPageResult result = videoCnService.getAllVideosWithCount(page, size);
            
            // 创建分页响应对象
            PageResponse<VideoCnDto> pageResponse = new PageResponse<>(
                result.getContent(), 
                result.getTotalElements(), 
                page, 
                size
            );
            
            return ResponseEntity.ok(ApiResponse.success("获取视频列表成功", pageResponse));
            
        } catch (Exception e) {
            logger.error("获取视频列表失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据标题搜索视频
     */
    @GetMapping("/search/title")
    public ResponseEntity<ApiResponse<List<VideoCnDto>>> searchVideosByTitle(@RequestParam String title) {
        try {
            logger.info("根据标题搜索视频（中文版），标题: {}", title);
            
            List<VideoCn> videos = videoCnService.searchByTitle(title);
            List<VideoCnDto> dtoList = videoCnService.convertToDtoList(videos);
            
            return ResponseEntity.ok(ApiResponse.success("搜索视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据标题搜索视频失败（中文版），标题: {}, 错误: {}", title, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据播放链接获取视频
     */
    @GetMapping("/playurl/{playurl}")
    public ResponseEntity<ApiResponse<VideoCnDto>> getVideoByPlayurl(@PathVariable String playurl) {
        try {
            logger.info("根据播放链接获取视频（中文版），链接: {}", playurl);
            
            Optional<VideoCn> optionalVideo = videoCnService.findByPlayurl(playurl);
            if (optionalVideo.isPresent()) {
                VideoCnDto dto = videoCnService.convertToDto(optionalVideo.get());
                return ResponseEntity.ok(ApiResponse.success("获取视频成功", dto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("视频不存在"));
            }
            
        } catch (Exception e) {
            logger.error("根据播放链接获取视频失败（中文版），链接: {}, 错误: {}", playurl, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据海报链接获取视频
     */
    @GetMapping("/posturl/{posturl}")
    public ResponseEntity<ApiResponse<VideoCnDto>> getVideoByPosturl(@PathVariable String posturl) {
        try {
            logger.info("根据海报链接获取视频（中文版），链接: {}", posturl);
            
            Optional<VideoCn> optionalVideo = videoCnService.findByPosturl(posturl);
            if (optionalVideo.isPresent()) {
                VideoCnDto dto = videoCnService.convertToDto(optionalVideo.get());
                return ResponseEntity.ok(ApiResponse.success("获取视频成功", dto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("视频不存在"));
            }
            
        } catch (Exception e) {
            logger.error("根据海报链接获取视频失败（中文版），链接: {}, 错误: {}", posturl, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有播放链接的视频
     */
    @GetMapping("/with-playurl")
    public ResponseEntity<ApiResponse<List<VideoCnDto>>> getVideosWithPlayurl() {
        try {
            logger.info("获取有播放链接的视频（中文版）");
            
            List<VideoCn> videos = videoCnService.getVideosWithPlayurl();
            List<VideoCnDto> dtoList = videoCnService.convertToDtoList(videos);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有播放链接的视频失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有海报链接的视频
     */
    @GetMapping("/with-posturl")
    public ResponseEntity<ApiResponse<List<VideoCnDto>>> getVideosWithPosturl() {
        try {
            logger.info("获取有海报链接的视频（中文版）");
            
            List<VideoCn> videos = videoCnService.getVideosWithPosturl();
            List<VideoCnDto> dtoList = videoCnService.convertToDtoList(videos);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有海报链接的视频失败（中文版），错误: {}", e.getMessage(), e);
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
            logger.info("统计视频总数（中文版）");
            
            long count = videoCnService.count();
            
            return ResponseEntity.ok(ApiResponse.success("统计成功", count));
            
        } catch (Exception e) {
            logger.error("统计视频总数失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("统计失败: " + e.getMessage()));
        }
    }
}

