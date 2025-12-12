package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateVideoScenarioRequest;
import com.example.cursorquitterweb.dto.PageResponse;
import com.example.cursorquitterweb.dto.UpdateVideoScenarioRequest;
import com.example.cursorquitterweb.dto.VideoScenarioCnDto;
import com.example.cursorquitterweb.dto.VideoScenarioCnPageResult;
import com.example.cursorquitterweb.entity.VideoScenarioCn;
import com.example.cursorquitterweb.service.VideoScenarioCnService;
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
 * 视频场景控制器（中文版）
 * 提供视频场景的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/video-scenarios/cn")
@Validated
public class VideoScenarioCnController {
    
    private static final Logger logger = LogUtil.getLogger(VideoScenarioCnController.class);
    
    @Autowired
    private VideoScenarioCnService videoScenarioCnService;
    
    /**
     * 创建新的视频场景
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<VideoScenarioCnDto>> createVideoScenario(@Valid @RequestBody CreateVideoScenarioRequest request) {
        try {
            logger.info("创建视频场景（中文版），请求参数: {}", request);
            
            VideoScenarioCn videoScenario = videoScenarioCnService.createVideoScenario(
                request.getType(),
                request.getTitle(),
                request.getSubtitle(),
                request.getImage(),
                request.getAudiourl(),
                request.getVideourl(),
                request.getColor(),
                request.getQuotes(),
                request.getAuthor()
            );
            
            VideoScenarioCnDto dto = videoScenarioCnService.convertToDto(videoScenario);
            logger.info("视频场景创建成功（中文版），ID: {}", videoScenario.getVideoId());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("视频场景创建成功", dto));
                    
        } catch (Exception e) {
            logger.error("创建视频场景失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取视频场景
     */
    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<VideoScenarioCnDto>> getVideoScenario(@PathVariable UUID videoId) {
        try {
            logger.info("获取视频场景（中文版），ID: {}", videoId);
            
            Optional<VideoScenarioCn> optionalVideoScenario = videoScenarioCnService.findById(videoId);
            if (optionalVideoScenario.isPresent()) {
                VideoScenarioCnDto dto = videoScenarioCnService.convertToDto(optionalVideoScenario.get());
                return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("视频场景不存在"));
            }
            
        } catch (Exception e) {
            logger.error("获取视频场景失败（中文版），ID: {}, 错误: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新视频场景
     */
    @PutMapping("/{videoId}")
    public ResponseEntity<ApiResponse<VideoScenarioCnDto>> updateVideoScenario(
            @PathVariable UUID videoId,
            @Valid @RequestBody UpdateVideoScenarioRequest request) {
        try {
            logger.info("更新视频场景（中文版），ID: {}, 请求参数: {}", videoId, request);
            
            VideoScenarioCn videoScenario = videoScenarioCnService.updateVideoScenario(
                videoId,
                request.getType(),
                request.getTitle(),
                request.getSubtitle(),
                request.getImage(),
                request.getAudiourl(),
                request.getVideourl(),
                request.getColor(),
                request.getQuotes(),
                request.getAuthor()
            );
            
            VideoScenarioCnDto dto = videoScenarioCnService.convertToDto(videoScenario);
            logger.info("视频场景更新成功（中文版），ID: {}", videoId);
            
            return ResponseEntity.ok(ApiResponse.success("视频场景更新成功", dto));
            
        } catch (RuntimeException e) {
            logger.warn("更新视频场景失败（中文版），ID: {}, 错误: {}", videoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("更新视频场景失败（中文版），ID: {}, 错误: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除视频场景
     */
    @DeleteMapping("/{videoId}")
    public ResponseEntity<ApiResponse<Void>> deleteVideoScenario(@PathVariable UUID videoId) {
        try {
            logger.info("删除视频场景（中文版），ID: {}", videoId);
            
            videoScenarioCnService.deleteVideoScenario(videoId);
            logger.info("视频场景删除成功（中文版），ID: {}", videoId);
            
            return ResponseEntity.ok(ApiResponse.success("视频场景删除成功", null));
            
        } catch (RuntimeException e) {
            logger.warn("删除视频场景失败（中文版），ID: {}, 错误: {}", videoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("删除视频场景失败（中文版），ID: {}, 错误: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有视频场景（分页）
     * 优化：使用窗口函数在单次查询中同时获取数据和总数，避免2次数据库查询
     * 使用缓存，缓存键包含 page 和 size 参数
     */
    @GetMapping("/getAllVideoScenarios")
    @Cacheable(value = "videoScenarios", key = "'cn_' + #page + '_' + #size")
    public ResponseEntity<ApiResponse<PageResponse<VideoScenarioCnDto>>> getAllVideoScenarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        try {
            logger.info("获取所有视频场景（中文版），页码: {}, 大小: {}", page, size);
            
            // 使用单次查询获取数据和总数
            VideoScenarioCnPageResult result = videoScenarioCnService.getAllVideoScenariosWithCount(page, size);
            
            // 创建分页响应对象
            PageResponse<VideoScenarioCnDto> pageResponse = new PageResponse<>(
                result.getContent(), 
                result.getTotalElements(), 
                page, 
                size
            );
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景列表成功", pageResponse));
            
        } catch (Exception e) {
            logger.error("获取视频场景列表失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据类型获取视频场景（按创建时间正序排列）
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<VideoScenarioCnDto>>> getVideoScenariosByType(@PathVariable String type) {
        try {
            logger.info("根据类型获取视频场景（中文版），类型: {}", type);
            
            List<VideoScenarioCn> videoScenarios = videoScenarioCnService.findByType(type);
            List<VideoScenarioCnDto> dtoList = videoScenarioCnService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据类型获取视频场景失败（中文版），类型: {}, 错误: {}", type, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据标题搜索视频场景
     */
    @GetMapping("/search/title")
    public ResponseEntity<ApiResponse<List<VideoScenarioCnDto>>> searchVideoScenariosByTitle(@RequestParam String title) {
        try {
            logger.info("根据标题搜索视频场景（中文版），标题: {}", title);
            
            List<VideoScenarioCn> videoScenarios = videoScenarioCnService.searchByTitle(title);
            List<VideoScenarioCnDto> dtoList = videoScenarioCnService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("搜索视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据标题搜索视频场景失败（中文版），标题: {}, 错误: {}", title, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据副标题搜索视频场景
     */
    @GetMapping("/search/subtitle")
    public ResponseEntity<ApiResponse<List<VideoScenarioCnDto>>> searchVideoScenariosBySubtitle(@RequestParam String subtitle) {
        try {
            logger.info("根据副标题搜索视频场景（中文版），副标题: {}", subtitle);
            
            List<VideoScenarioCn> videoScenarios = videoScenarioCnService.searchBySubtitle(subtitle);
            List<VideoScenarioCnDto> dtoList = videoScenarioCnService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("搜索视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据副标题搜索视频场景失败（中文版），副标题: {}, 错误: {}", subtitle, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据颜色获取视频场景
     */
    @GetMapping("/color/{color}")
    public ResponseEntity<ApiResponse<List<VideoScenarioCnDto>>> getVideoScenariosByColor(@PathVariable String color) {
        try {
            logger.info("根据颜色获取视频场景（中文版），颜色: {}", color);
            
            List<VideoScenarioCn> videoScenarios = videoScenarioCnService.findByColor(color);
            List<VideoScenarioCnDto> dtoList = videoScenarioCnService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据颜色获取视频场景失败（中文版），颜色: {}, 错误: {}", color, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据作者获取视频场景
     */
    @GetMapping("/author/{author}")
    public ResponseEntity<ApiResponse<List<VideoScenarioCnDto>>> getVideoScenariosByAuthor(@PathVariable String author) {
        try {
            logger.info("根据作者获取视频场景（中文版），作者: {}", author);
            
            List<VideoScenarioCn> videoScenarios = videoScenarioCnService.findByAuthor(author);
            List<VideoScenarioCnDto> dtoList = videoScenarioCnService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据作者获取视频场景失败（中文版），作者: {}, 错误: {}", author, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有音频URL的视频场景
     */
    @GetMapping("/with-audio")
    public ResponseEntity<ApiResponse<List<VideoScenarioCnDto>>> getVideoScenariosWithAudio() {
        try {
            logger.info("获取有音频URL的视频场景（中文版）");
            
            List<VideoScenarioCn> videoScenarios = videoScenarioCnService.getVideoScenariosWithAudio();
            List<VideoScenarioCnDto> dtoList = videoScenarioCnService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有音频URL的视频场景失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有视频URL的视频场景
     */
    @GetMapping("/with-video")
    public ResponseEntity<ApiResponse<List<VideoScenarioCnDto>>> getVideoScenariosWithVideo() {
        try {
            logger.info("获取有视频URL的视频场景（中文版）");
            
            List<VideoScenarioCn> videoScenarios = videoScenarioCnService.getVideoScenariosWithVideo();
            List<VideoScenarioCnDto> dtoList = videoScenarioCnService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有视频URL的视频场景失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有封面图片的视频场景
     */
    @GetMapping("/with-image")
    public ResponseEntity<ApiResponse<List<VideoScenarioCnDto>>> getVideoScenariosWithImage() {
        try {
            logger.info("获取有封面图片的视频场景（中文版）");
            
            List<VideoScenarioCn> videoScenarios = videoScenarioCnService.getVideoScenariosWithImage();
            List<VideoScenarioCnDto> dtoList = videoScenarioCnService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有封面图片的视频场景失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 统计指定类型的视频场景数量
     */
    @GetMapping("/count/type/{type}")
    public ResponseEntity<ApiResponse<Long>> countVideoScenariosByType(@PathVariable String type) {
        try {
            logger.info("统计指定类型的视频场景数量（中文版），类型: {}", type);
            
            long count = videoScenarioCnService.countByType(type);
            
            return ResponseEntity.ok(ApiResponse.success("统计成功", count));
            
        } catch (Exception e) {
            logger.error("统计视频场景数量失败（中文版），类型: {}, 错误: {}", type, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("统计失败: " + e.getMessage()));
        }
    }
}

