package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateMeditateVideoRequest;
import com.example.cursorquitterweb.dto.UpdateMeditateVideoRequest;
import com.example.cursorquitterweb.dto.MeditateVideoDto;
import com.example.cursorquitterweb.entity.MeditateVideo;
import com.example.cursorquitterweb.service.MeditateVideoService;
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
 * 冥想视频控制器
 * 提供冥想视频的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/meditate-videos")
@Validated
public class MeditateVideoController {
    
    private static final Logger logger = LogUtil.getLogger(MeditateVideoController.class);
    
    @Autowired
    private MeditateVideoService meditateVideoService;
    
    /**
     * 创建新的冥想视频
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<MeditateVideoDto>> createMeditateVideo(@Valid @RequestBody CreateMeditateVideoRequest request) {
        try {
            logger.info("创建冥想视频，请求参数: {}", request);
            
            MeditateVideo meditateVideo = meditateVideoService.createMeditateVideo(
                request.getTitle(),
                request.getImage(),
                request.getVideoUrl(),
                request.getAudioUrl(),
                request.getMeditateQuotes(),
                request.getColor()
            );
            
            MeditateVideoDto dto = meditateVideoService.convertToDto(meditateVideo);
            logger.info("冥想视频创建成功，ID: {}", meditateVideo.getId());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("冥想视频创建成功", dto));
                    
        } catch (Exception e) {
            logger.error("创建冥想视频失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取冥想视频
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MeditateVideoDto>> getMeditateVideo(@PathVariable UUID id) {
        try {
            logger.info("获取冥想视频，ID: {}", id);
            
            Optional<MeditateVideo> optionalMeditateVideo = meditateVideoService.findById(id);
            if (optionalMeditateVideo.isPresent()) {
                MeditateVideoDto dto = meditateVideoService.convertToDto(optionalMeditateVideo.get());
                return ResponseEntity.ok(ApiResponse.success("获取冥想视频成功", dto));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("冥想视频不存在"));
            }
            
        } catch (Exception e) {
            logger.error("获取冥想视频失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新冥想视频
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MeditateVideoDto>> updateMeditateVideo(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMeditateVideoRequest request) {
        try {
            logger.info("更新冥想视频，ID: {}, 请求参数: {}", id, request);
            
            MeditateVideo meditateVideo = meditateVideoService.updateMeditateVideo(
                id,
                request.getTitle(),
                request.getImage(),
                request.getVideoUrl(),
                request.getAudioUrl(),
                request.getMeditateQuotes(),
                request.getColor()
            );
            
            MeditateVideoDto dto = meditateVideoService.convertToDto(meditateVideo);
            logger.info("冥想视频更新成功，ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("冥想视频更新成功", dto));
            
        } catch (RuntimeException e) {
            logger.warn("更新冥想视频失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("更新冥想视频失败，ID: {}, 错误: {}", id, e.getMessage(), e);
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
            logger.info("删除冥想视频，ID: {}", id);
            
            meditateVideoService.deleteMeditateVideo(id);
            logger.info("冥想视频删除成功，ID: {}", id);
            
            return ResponseEntity.ok(ApiResponse.success("冥想视频删除成功", null));
            
        } catch (RuntimeException e) {
            logger.warn("删除冥想视频失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("删除冥想视频失败，ID: {}, 错误: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有冥想视频（分页）
     */
    @GetMapping("/getAllMeditateVideos")
    public ResponseEntity<ApiResponse<Page<MeditateVideoDto>>> getAllMeditateVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "createAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            logger.info("获取所有冥想视频，页码: {}, 大小: {}, 排序: {} {}", page, size, sortBy, sortDir);
            
            // 验证排序字段名
            String[] validSortFields = {"id", "title", "image", "videoUrl", "audioUrl", "meditateQuotes", "color", "createAt", "updateAt"};
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
            
            Page<MeditateVideo> meditateVideos = meditateVideoService.getAllMeditateVideos(pageable);
            Page<MeditateVideoDto> dtoPage = meditateVideos.map(meditateVideoService::convertToDto);
            
            return ResponseEntity.ok(ApiResponse.success("获取冥想视频列表成功", dtoPage));
            
        } catch (Exception e) {
            logger.error("获取冥想视频列表失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据标题搜索冥想视频
     */
    @GetMapping("/search/title")
    public ResponseEntity<ApiResponse<List<MeditateVideoDto>>> searchMeditateVideosByTitle(@RequestParam String title) {
        try {
            logger.info("根据标题搜索冥想视频，标题: {}", title);
            
            List<MeditateVideo> meditateVideos = meditateVideoService.searchByTitle(title);
            List<MeditateVideoDto> dtoList = meditateVideoService.convertToDtoList(meditateVideos);
            
            return ResponseEntity.ok(ApiResponse.success("搜索冥想视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据标题搜索冥想视频失败，标题: {}, 错误: {}", title, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据颜色获取冥想视频
     */
    @GetMapping("/color/{color}")
    public ResponseEntity<ApiResponse<List<MeditateVideoDto>>> getMeditateVideosByColor(@PathVariable String color) {
        try {
            logger.info("根据颜色获取冥想视频，颜色: {}", color);
            
            List<MeditateVideo> meditateVideos = meditateVideoService.findByColor(color);
            List<MeditateVideoDto> dtoList = meditateVideoService.convertToDtoList(meditateVideos);
            
            return ResponseEntity.ok(ApiResponse.success("获取冥想视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("根据颜色获取冥想视频失败，颜色: {}, 错误: {}", color, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有视频链接的冥想视频
     */
    @GetMapping("/with-video-url")
    public ResponseEntity<ApiResponse<List<MeditateVideoDto>>> getMeditateVideosWithVideoUrl() {
        try {
            logger.info("获取有视频链接的冥想视频");
            
            List<MeditateVideo> meditateVideos = meditateVideoService.getMeditateVideosWithVideoUrl();
            List<MeditateVideoDto> dtoList = meditateVideoService.convertToDtoList(meditateVideos);
            
            return ResponseEntity.ok(ApiResponse.success("获取冥想视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有视频链接的冥想视频失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有音频链接的冥想视频
     */
    @GetMapping("/with-audio-url")
    public ResponseEntity<ApiResponse<List<MeditateVideoDto>>> getMeditateVideosWithAudioUrl() {
        try {
            logger.info("获取有音频链接的冥想视频");
            
            List<MeditateVideo> meditateVideos = meditateVideoService.getMeditateVideosWithAudioUrl();
            List<MeditateVideoDto> dtoList = meditateVideoService.convertToDtoList(meditateVideos);
            
            return ResponseEntity.ok(ApiResponse.success("获取冥想视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有音频链接的冥想视频失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取冥想视频失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取有图片的冥想视频
     */
    @GetMapping("/with-image")
    public ResponseEntity<ApiResponse<List<MeditateVideoDto>>> getMeditateVideosWithImage() {
        try {
            logger.info("获取有图片的冥想视频");
            
            List<MeditateVideo> meditateVideos = meditateVideoService.getMeditateVideosWithImage();
            List<MeditateVideoDto> dtoList = meditateVideoService.convertToDtoList(meditateVideos);
            
            return ResponseEntity.ok(ApiResponse.success("获取冥想视频成功", dtoList));
            
        } catch (Exception e) {
            logger.error("获取有图片的冥想视频失败，错误: {}", e.getMessage(), e);
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
            logger.info("统计冥想视频总数");
            
            long count = meditateVideoService.count();
            
            return ResponseEntity.ok(ApiResponse.success("统计成功", count));
            
        } catch (Exception e) {
            logger.error("统计冥想视频总数失败，错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("统计失败: " + e.getMessage()));
        }
    }
}
