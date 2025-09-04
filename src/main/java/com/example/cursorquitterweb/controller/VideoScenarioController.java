package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateVideoScenarioRequest;
import com.example.cursorquitterweb.dto.UpdateVideoScenarioRequest;
import com.example.cursorquitterweb.dto.VideoScenarioDto;
import com.example.cursorquitterweb.entity.VideoScenario;
import com.example.cursorquitterweb.service.VideoScenarioService;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 视频场景控制器
 * 提供视频场景的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/video-scenarios")
@Validated
public class VideoScenarioController {
    
    private static final Logger logger = LogUtil.getLogger(VideoScenarioController.class);
    
    @Autowired
    private VideoScenarioService videoScenarioService;
    
    /**
     * 创建新的视频场景
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<VideoScenarioDto>> createVideoScenario(@Valid @RequestBody CreateVideoScenarioRequest request) {
        try {
            logger.info("创建视频场景，请求参数: {}", request);
            
            VideoScenario videoScenario = videoScenarioService.createVideoScenario(
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
            
            VideoScenarioDto dto = videoScenarioService.convertToDto(videoScenario);
            logger.info("视频场景创建成功，ID: {}", videoScenario.getVideoId());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("视频场景创建成功", dto));
                    
        } catch (Exception e) {
            logger.error("创建视频场景失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取视频场景
     */
    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<VideoScenarioDto>> getVideoScenario(@PathVariable UUID videoId) {
        try {
            logger.info("获取视频场景，ID: {}", videoId);
            
            Optional<VideoScenario> optionalVideoScenario = videoScenarioService.findById(videoId);
            if (optionalVideoScenario.isPresent()) {
                VideoScenarioDto dto = videoScenarioService.convertToDto(optionalVideoScenario.get());
                return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("视频场景不存在"));
            }
            
        } catch (Exception e) {
            logger.error("获取视频场景失败，ID: {}, 错误: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新视频场景
     */
    @PutMapping("/{videoId}")
    public ResponseEntity<ApiResponse<VideoScenarioDto>> updateVideoScenario(
            @PathVariable UUID videoId,
            @Valid @RequestBody UpdateVideoScenarioRequest request) {
        try {
            logger.info("更新视频场景，ID: {}, 请求参数: {}", videoId, request);
            
            VideoScenario videoScenario = videoScenarioService.updateVideoScenario(
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
            
            VideoScenarioDto dto = videoScenarioService.convertToDto(videoScenario);
            logger.info("视频场景更新成功，ID: {}", videoId);
            
            return ResponseEntity.ok(ApiResponse.success("视频场景更新成功", dto));
            
        } catch (RuntimeException e) {
            logger.warn("更新视频场景失败，ID: {}, 错误: {}", videoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("更新视频场景失败，ID: {}, 错误: {}", videoId, e.getMessage(), e);
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
            logger.info("删除视频场景，ID: {}", videoId);
            
            videoScenarioService.deleteVideoScenario(videoId);
            logger.info("视频场景删除成功，ID: {}", videoId);
            
            return ResponseEntity.ok(ApiResponse.success("视频场景删除成功", null));
            
        } catch (RuntimeException e) {
            logger.warn("删除视频场景失败，ID: {}, 错误: {}", videoId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("删除视频场景失败，ID: {}, 错误: {}", videoId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有视频场景（分页）
     */
    @GetMapping("/getAllVideoScenarios")
    public ResponseEntity<ApiResponse<Page<VideoScenarioDto>>> getAllVideoScenarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "createAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            logger.info("获取所有视频场景，页码: {}, 大小: {}, 排序: {} {}", page, size, sortBy, sortDir);
            
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            
            Page<VideoScenario> videoScenarios = videoScenarioService.getAllVideoScenarios(pageable);
            Page<VideoScenarioDto> dtoPage = videoScenarios.map(videoScenarioService::convertToDto);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景列表成功", dtoPage));
            
        } catch (Exception e) {
            logger.error("获取视频场景列表失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据类型获取视频场景（按创建时间正序排列）
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<VideoScenarioDto>>> getVideoScenariosByType(@PathVariable String type) {
        try {
            logger.info("根据类型获取视频场景，类型: {}", type);
            
            List<VideoScenario> videoScenarios = videoScenarioService.findByType(type);
            List<VideoScenarioDto> dtoList = videoScenarioService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据类型获取视频场景失败，类型: {}, 错误: {}", type, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据标题搜索视频场景
     */
    @GetMapping("/search/title")
    public ResponseEntity<ApiResponse<List<VideoScenarioDto>>> searchVideoScenariosByTitle(@RequestParam String title) {
        try {
            logger.info("根据标题搜索视频场景，标题: {}", title);
            
            List<VideoScenario> videoScenarios = videoScenarioService.searchByTitle(title);
            List<VideoScenarioDto> dtoList = videoScenarioService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("搜索视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据标题搜索视频场景失败，标题: {}, 错误: {}", title, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据副标题搜索视频场景
     */
    @GetMapping("/search/subtitle")
    public ResponseEntity<ApiResponse<List<VideoScenarioDto>>> searchVideoScenariosBySubtitle(@RequestParam String subtitle) {
        try {
            logger.info("根据副标题搜索视频场景，副标题: {}", subtitle);
            
            List<VideoScenario> videoScenarios = videoScenarioService.searchBySubtitle(subtitle);
            List<VideoScenarioDto> dtoList = videoScenarioService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("搜索视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据副标题搜索视频场景失败，副标题: {}, 错误: {}", subtitle, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据颜色获取视频场景
     */
    @GetMapping("/color/{color}")
    public ResponseEntity<ApiResponse<List<VideoScenarioDto>>> getVideoScenariosByColor(@PathVariable String color) {
        try {
            logger.info("根据颜色获取视频场景，颜色: {}", color);
            
            List<VideoScenario> videoScenarios = videoScenarioService.findByColor(color);
            List<VideoScenarioDto> dtoList = videoScenarioService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据颜色获取视频场景失败，颜色: {}, 错误: {}", color, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据作者获取视频场景
     */
    @GetMapping("/author/{author}")
    public ResponseEntity<ApiResponse<List<VideoScenarioDto>>> getVideoScenariosByAuthor(@PathVariable String author) {
        try {
            logger.info("根据作者获取视频场景，作者: {}", author);
            
            List<VideoScenario> videoScenarios = videoScenarioService.findByAuthor(author);
            List<VideoScenarioDto> dtoList = videoScenarioService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据作者获取视频场景失败，作者: {}, 错误: {}", author, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有音频URL的视频场景
     */
    @GetMapping("/with-audio")
    public ResponseEntity<ApiResponse<List<VideoScenarioDto>>> getVideoScenariosWithAudio() {
        try {
            logger.info("获取有音频URL的视频场景");
            
            List<VideoScenario> videoScenarios = videoScenarioService.getVideoScenariosWithAudio();
            List<VideoScenarioDto> dtoList = videoScenarioService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有音频URL的视频场景失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有视频URL的视频场景
     */
    @GetMapping("/with-video")
    public ResponseEntity<ApiResponse<List<VideoScenarioDto>>> getVideoScenariosWithVideo() {
        try {
            logger.info("获取有视频URL的视频场景");
            
            List<VideoScenario> videoScenarios = videoScenarioService.getVideoScenariosWithVideo();
            List<VideoScenarioDto> dtoList = videoScenarioService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有视频URL的视频场景失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取视频场景失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有封面图片的视频场景
     */
    @GetMapping("/with-image")
    public ResponseEntity<ApiResponse<List<VideoScenarioDto>>> getVideoScenariosWithImage() {
        try {
            logger.info("获取有封面图片的视频场景");
            
            List<VideoScenario> videoScenarios = videoScenarioService.getVideoScenariosWithImage();
            List<VideoScenarioDto> dtoList = videoScenarioService.convertToDtoList(videoScenarios);
            
            return ResponseEntity.ok(ApiResponse.success("获取视频场景成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有封面图片的视频场景失败，错误: {}", e.getMessage(), e);
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
            logger.info("统计指定类型的视频场景数量，类型: {}", type);
            
            long count = videoScenarioService.countByType(type);
            
            return ResponseEntity.ok(ApiResponse.success("统计成功", count));
            
        } catch (Exception e) {
            logger.error("统计视频场景数量失败，类型: {}, 错误: {}", type, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("统计失败: " + e.getMessage()));
        }
    }
}
