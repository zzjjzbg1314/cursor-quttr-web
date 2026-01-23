package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.VideoScenarioCnDto;
import com.example.cursorquitterweb.entity.VideoScenarioCn;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 视频场景服务接口（中文版）
 */
public interface VideoScenarioCnService {
    
    /**
     * 根据ID查找视频场景
     */
    Optional<VideoScenarioCn> findById(UUID videoId);
    
    /**
     * 创建新视频场景
     */
    VideoScenarioCn createVideoScenario(String type, String title, String subtitle, String image, 
                                     String audiourl, String videourl, String videourlLd, String color, String quotes, String author);
    
    /**
     * 更新视频场景信息
     */
    VideoScenarioCn updateVideoScenario(UUID videoId, String type, String title, String subtitle, 
                                     String image, String audiourl, String videourl, String videourlLd, String color, 
                                     String quotes, String author);
    
    /**
     * 删除视频场景
     */
    void deleteVideoScenario(UUID videoId);
    
    /**
     * 根据类型查找视频场景（按创建时间正序排列）
     */
    List<VideoScenarioCn> findByType(String type);
    
    /**
     * 根据类型分页查找视频场景（已移除 Spring Data Page，返回 List）
     */
    List<VideoScenarioCn> findByType(String type, int page, int size);
    
    /**
     * 根据标题搜索视频场景
     */
    List<VideoScenarioCn> searchByTitle(String title);
    
    /**
     * 根据副标题搜索视频场景
     */
    List<VideoScenarioCn> searchBySubtitle(String subtitle);
    
    /**
     * 根据颜色查找视频场景
     */
    List<VideoScenarioCn> findByColor(String color);
    
    /**
     * 根据作者查找视频场景
     */
    List<VideoScenarioCn> findByAuthor(String author);
    
    /**
     * 获取所有视频场景（分页，已移除 Spring Data Page，返回 List）
     */
    List<VideoScenarioCn> getAllVideoScenarios(int page, int size);
    
    /**
     * 获取所有视频场景（分页，使用窗口函数一次性获取数据和总数）
     * 性能优化：使用窗口函数在单次查询中同时获取数据和总数，避免2次数据库查询
     */
    com.example.cursorquitterweb.dto.VideoScenarioCnPageResult getAllVideoScenariosWithCount(int page, int size);
    
    /**
     * 获取所有视频场景
     */
    List<VideoScenarioCn> getAllVideoScenarios();
    
    /**
     * 根据时间范围查找视频场景
     */
    List<VideoScenarioCn> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime);
    
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
    List<VideoScenarioCn> findByTypeAndTitle(String type, String title);
    
    /**
     * 获取有音频URL的视频场景
     */
    List<VideoScenarioCn> getVideoScenariosWithAudio();
    
    /**
     * 获取有视频URL的视频场景
     */
    List<VideoScenarioCn> getVideoScenariosWithVideo();
    
    /**
     * 获取有封面图片的视频场景
     */
    List<VideoScenarioCn> getVideoScenariosWithImage();
    
    /**
     * 转换为DTO
     */
    VideoScenarioCnDto convertToDto(VideoScenarioCn videoScenario);
    
    /**
     * 批量转换为DTO
     */
    List<VideoScenarioCnDto> convertToDtoList(List<VideoScenarioCn> videoScenarios);
}

