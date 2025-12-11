package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.VideoScenarioDto;
import com.example.cursorquitterweb.entity.VideoScenario;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 视频场景服务接口
 */
public interface VideoScenarioService {
    
    /**
     * 根据ID查找视频场景
     */
    Optional<VideoScenario> findById(UUID videoId);
    
    /**
     * 创建新视频场景
     */
    VideoScenario createVideoScenario(String type, String title, String subtitle, String image, 
                                     String audiourl, String videourl, String color, String quotes, String author);
    
    /**
     * 更新视频场景信息
     */
    VideoScenario updateVideoScenario(UUID videoId, String type, String title, String subtitle, 
                                     String image, String audiourl, String videourl, String color, 
                                     String quotes, String author);
    
    /**
     * 删除视频场景
     */
    void deleteVideoScenario(UUID videoId);
    
    /**
     * 根据类型查找视频场景（按创建时间正序排列）
     */
    List<VideoScenario> findByType(String type);
    
    /**
     * 根据类型分页查找视频场景（已移除 Spring Data Page，返回 List）
     */
    List<VideoScenario> findByType(String type, int page, int size);
    
    /**
     * 根据标题搜索视频场景
     */
    List<VideoScenario> searchByTitle(String title);
    
    /**
     * 根据副标题搜索视频场景
     */
    List<VideoScenario> searchBySubtitle(String subtitle);
    
    /**
     * 根据颜色查找视频场景
     */
    List<VideoScenario> findByColor(String color);
    
    /**
     * 根据作者查找视频场景
     */
    List<VideoScenario> findByAuthor(String author);
    
    /**
     * 获取所有视频场景（分页，已移除 Spring Data Page，返回 List）
     */
    List<VideoScenario> getAllVideoScenarios(int page, int size);
    
    /**
     * 获取所有视频场景（分页，使用窗口函数一次性获取数据和总数）
     * 性能优化：使用窗口函数在单次查询中同时获取数据和总数，避免2次数据库查询
     */
    com.example.cursorquitterweb.dto.VideoScenarioPageResult getAllVideoScenariosWithCount(int page, int size);
    
    /**
     * 获取所有视频场景
     */
    List<VideoScenario> getAllVideoScenarios();
    
    /**
     * 根据时间范围查找视频场景
     */
    List<VideoScenario> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计指定类型的视频场景数量
     */
    long countByType(String type);
    
    /**
     * 统计指定时间范围内的视频场景数量
     */
    long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据类型和标题模糊查询视频场景
     */
    List<VideoScenario> findByTypeAndTitle(String type, String title);
    
    /**
     * 获取有音频URL的视频场景
     */
    List<VideoScenario> getVideoScenariosWithAudio();
    
    /**
     * 获取有视频URL的视频场景
     */
    List<VideoScenario> getVideoScenariosWithVideo();
    
    /**
     * 获取有封面图片的视频场景
     */
    List<VideoScenario> getVideoScenariosWithImage();
    
    /**
     * 转换为DTO
     */
    VideoScenarioDto convertToDto(VideoScenario videoScenario);
    
    /**
     * 批量转换为DTO
     */
    List<VideoScenarioDto> convertToDtoList(List<VideoScenario> videoScenarios);
}
