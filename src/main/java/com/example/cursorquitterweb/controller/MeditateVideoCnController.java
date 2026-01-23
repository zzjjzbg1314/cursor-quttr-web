package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateMeditateVideoRequest;
import com.example.cursorquitterweb.dto.UpdateMeditateVideoRequest;
import com.example.cursorquitterweb.dto.MeditateVideoCnDto;
import com.example.cursorquitterweb.dto.MeditateVideoCnPageResult;
import com.example.cursorquitterweb.dto.PageResponse;
import com.example.cursorquitterweb.entity.MeditateVideoCn;
import com.example.cursorquitterweb.service.MeditateVideoCnService;
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
 * 冥想视频控制器（中文版）
 * 提供冥想视频的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/meditate-videos/cn")
@Validated
public class MeditateVideoCnController {
    
    private static final Logger logger = LogUtil.getLogger(MeditateVideoCnController.class);
    
    @Autowired
    private MeditateVideoCnService meditateVideoCnService;
    
    /**
     * 创建新的冥想视频
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<MeditateVideoCnDto>> createMeditateVideo(@Valid @RequestBody CreateMeditateVideoRequest request) {
        try {
            logger.info("创建冥想视频（中文版），请求参数: {}", request);
            
            MeditateVideoCn meditateVideo = meditateVideoCnService.createMeditateVideo(
                request.getTitle(),
                request.getSubtitle(),
                request.getImage(),
                request.getVideoUrl(),
                request.getVideourlLd(),
                request.getAudioUrl(),
                request.getMeditateQuotes(),
                request.getColor()
            );
            
            MeditateVideoCnDto dto = meditateVideoCnService.convertToDto(meditateVideo);
            logger.info("冥想视频创建成功（中文版），ID: {}", meditateVideo.getId());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("冥想视频创建成功", dto));
                    
        } catch (Exception e) {
            logger.error("创建冥想视频失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取冥想视频
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MeditateVideoCnDto>> getMeditateVideo(@PathVariable UUID id) {
        try {
            logger.info("获取冥想视频（中文版），ID: {}", id);
            
            Optional<MeditateVideoCn> optionalMeditateVideo = meditateVideoCnService.findById(id);
            if (optionalMeditateVideo.isPresent()) {
                MeditateVideoCnDto dto = meditateVideoCnService.convertToDto(optionalMeditateVideo.get());
                return ResponseEntity.ok(ApiResponse.success("获取冥想视频成功", dto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("冥想视频不存在"));
            }
            
        } catch (Exception e) {
            logger.error("获取冥想视频失败（中文版），ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新冥想视频
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MeditateVideoCnDto>> updateMeditateVideo(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMeditateVideoRequest request) {
        try {
            logger.info("更新冥想视频（中文版），ID: {}, 请求参数: {}", id, request);
            
            MeditateVideoCn meditateVideo = meditateVideoCnService.updateMeditateVideo(
                id,
                request.getTitle(),
                request.getSubtitle(),
                request.getImage(),
                request.getVideoUrl(),
                request.getVideourlLd(),
                request.getAudioUrl(),
                request.getMeditateQuotes(),
                request.getColor()
            );
            
            MeditateVideoCnDto dto = meditateVideoCnService.convertToDto(meditateVideo);
            logger.info("冥想视频更新成功（中文版），ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("冥想视频更新成功", dto));
            
        } catch (RuntimeException e) {
            logger.warn("更新冥想视频失败（中文版），ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("更新冥想视频失败（中文版），ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除冥想视频
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMeditateVideo(@PathVariable UUID id) {
        try {
            logger.info("删除冥想视频（中文版），ID: {}", id);
            
            meditateVideoCnService.deleteMeditateVideo(id);
            logger.info("冥想视频删除成功（中文版），ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("冥想视频删除成功", null));
            
        } catch (RuntimeException e) {
            logger.warn("删除冥想视频失败（中文版），ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("删除冥想视频失败（中文版），ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有冥想视频（分页，支持排序）
     * 返回格式: { "data": { "content": [...], "totalElements": ..., ... } }
     * 优化：使用窗口函数在单次查询中同时获取数据和总数，避免2次数据库查询
     * 使用缓存，缓存键包含 page、size、sortBy 和 sortDir 参数
     */
    @GetMapping("/getAllMeditateVideos")
    @Cacheable(value = "meditateVideos", key = "'cn_' + #page + '_' + #size + '_' + #sortBy + '_' + #sortDir")
    public ResponseEntity<ApiResponse<PageResponse<MeditateVideoCnDto>>> getAllMeditateVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "createAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            logger.info("获取所有冥想视频（中文版），页码: {}, 大小: {}, 排序字段: {}, 排序方向: {}", page, size, sortBy, sortDir);
            
            // 使用单次查询获取数据和总数
            MeditateVideoCnPageResult result = meditateVideoCnService.getAllMeditateVideosWithCount(page, size, sortBy, sortDir);
            
            // 创建分页响应对象
            PageResponse<MeditateVideoCnDto> pageResponse = new PageResponse<>(
                result.getContent(), 
                result.getTotalElements(), 
                page, 
                size
            );
            
            return ResponseEntity.ok(ApiResponse.success("获取冥想视频列表成功", pageResponse));
            
        } catch (Exception e) {
            logger.error("获取冥想视频列表失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据标题搜索冥想视频
     */
    @GetMapping("/search/title")
    public ResponseEntity<ApiResponse<List<MeditateVideoCnDto>>> searchMeditateVideosByTitle(@RequestParam String title) {
        try {
            logger.info("根据标题搜索冥想视频（中文版），标题: {}", title);
            
            List<MeditateVideoCn> meditateVideos = meditateVideoCnService.searchByTitle(title);
            List<MeditateVideoCnDto> dtoList = meditateVideoCnService.convertToDtoList(meditateVideos);
            
            return ResponseEntity.ok(ApiResponse.success("搜索冥想视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据标题搜索冥想视频失败（中文版），标题: {}, 错误: {}", title, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据颜色获取冥想视频
     */
    @GetMapping("/color/{color}")
    public ResponseEntity<ApiResponse<List<MeditateVideoCnDto>>> getMeditateVideosByColor(@PathVariable String color) {
        try {
            logger.info("根据颜色获取冥想视频（中文版），颜色: {}", color);
            
            List<MeditateVideoCn> meditateVideos = meditateVideoCnService.findByColor(color);
            List<MeditateVideoCnDto> dtoList = meditateVideoCnService.convertToDtoList(meditateVideos);
            
            return ResponseEntity.ok(ApiResponse.success("获取冥想视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据颜色获取冥想视频失败（中文版），颜色: {}, 错误: {}", color, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有视频链接的冥想视频
     */
    @GetMapping("/with-video-url")
    public ResponseEntity<ApiResponse<List<MeditateVideoCnDto>>> getMeditateVideosWithVideoUrl() {
        try {
            logger.info("获取有视频链接的冥想视频（中文版）");
            
            List<MeditateVideoCn> meditateVideos = meditateVideoCnService.getMeditateVideosWithVideoUrl();
            List<MeditateVideoCnDto> dtoList = meditateVideoCnService.convertToDtoList(meditateVideos);
            
            return ResponseEntity.ok(ApiResponse.success("获取冥想视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有视频链接的冥想视频失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有音频链接的冥想视频
     */
    @GetMapping("/with-audio-url")
    public ResponseEntity<ApiResponse<List<MeditateVideoCnDto>>> getMeditateVideosWithAudioUrl() {
        try {
            logger.info("获取有音频链接的冥想视频（中文版）");
            
            List<MeditateVideoCn> meditateVideos = meditateVideoCnService.getMeditateVideosWithAudioUrl();
            List<MeditateVideoCnDto> dtoList = meditateVideoCnService.convertToDtoList(meditateVideos);
            
            return ResponseEntity.ok(ApiResponse.success("获取冥想视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有音频链接的冥想视频失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有图片的冥想视频
     */
    @GetMapping("/with-image")
    public ResponseEntity<ApiResponse<List<MeditateVideoCnDto>>> getMeditateVideosWithImage() {
        try {
            logger.info("获取有图片的冥想视频（中文版）");
            
            List<MeditateVideoCn> meditateVideos = meditateVideoCnService.getMeditateVideosWithImage();
            List<MeditateVideoCnDto> dtoList = meditateVideoCnService.convertToDtoList(meditateVideos);
            
            return ResponseEntity.ok(ApiResponse.success("获取冥想视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有图片的冥想视频失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 统计冥想视频总数
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> countMeditateVideos() {
        try {
            logger.info("统计冥想视频总数（中文版）");
            
            long count = meditateVideoCnService.count();
            
            return ResponseEntity.ok(ApiResponse.success("统计成功", count));
            
        } catch (Exception e) {
            logger.error("统计冥想视频总数失败（中文版），错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("统计失败: " + e.getMessage()));
        }
    }
}

