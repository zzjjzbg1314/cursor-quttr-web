package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.VideoScenarioCnDto;
import com.example.cursorquitterweb.dto.VideoScenarioCnPageResult;
import com.example.cursorquitterweb.entity.VideoScenarioCn;
import com.example.cursorquitterweb.service.VideoScenarioCnService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 视频场景服务实现类（中文版）
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class VideoScenarioCnServiceImpl implements VideoScenarioCnService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public Optional<VideoScenarioCn> findById(UUID videoId) {
        String sql = "SELECT * FROM video_scenario_cn WHERE videoId = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(videoId));
        return row != null ? Optional.of(mapToVideoScenario(row)) : Optional.empty();
    }
    
    @Override
    @CacheEvict(value = "videoScenarios", allEntries = true)
    public VideoScenarioCn createVideoScenario(String type, String title, String subtitle, String image, 
                                           String audiourl, String videourl, String videourlLd, String color, String quotes, String author) {
        VideoScenarioCn videoScenario = new VideoScenarioCn(type, title, subtitle, image, audiourl, videourl, videourlLd, color, quotes, author);
        return saveVideoScenario(videoScenario);
    }
    
    @Override
    @CacheEvict(value = "videoScenarios", allEntries = true)
    public VideoScenarioCn updateVideoScenario(UUID videoId, String type, String title, String subtitle, 
                                            String image, String audiourl, String videourl, String videourlLd, String color, 
                                            String quotes, String author) {
        Optional<VideoScenarioCn> optionalVideoScenario = findById(videoId);
        if (optionalVideoScenario.isPresent()) {
            VideoScenarioCn videoScenario = optionalVideoScenario.get();
            if (type != null) videoScenario.setType(type);
            if (title != null) videoScenario.setTitle(title);
            if (subtitle != null) videoScenario.setSubtitle(subtitle);
            if (image != null) videoScenario.setImage(image);
            if (audiourl != null) videoScenario.setAudiourl(audiourl);
            if (videourl != null) videoScenario.setVideourl(videourl);
            if (videourlLd != null) videoScenario.setVideourlLd(videourlLd);
            if (color != null) videoScenario.setColor(color);
            if (quotes != null) videoScenario.setQuotes(quotes);
            if (author != null) videoScenario.setAuthor(author);
            return saveVideoScenario(videoScenario);
        }
        throw new RuntimeException("视频场景不存在");
    }
    
    @Override
    @CacheEvict(value = "videoScenarios", allEntries = true)
    public void deleteVideoScenario(UUID videoId) {
        if (!d1Util.exists("video_scenario_cn", "videoId = ?", EntityMapper.uuidToString(videoId))) {
            throw new RuntimeException("视频场景不存在");
        }
        d1Util.deleteById("video_scenario_cn", "videoId", EntityMapper.uuidToString(videoId));
    }
    
    @Override
    public List<VideoScenarioCn> findByType(String type) {
        String sql = "SELECT * FROM video_scenario_cn WHERE type = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, type);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenarioCn> findByType(String type, int page, int size) {
        String sql = "SELECT * FROM video_scenario_cn WHERE type = ? ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size, type).stream()
            .map(this::mapToVideoScenario)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenarioCn> searchByTitle(String title) {
        String sql = "SELECT * FROM video_scenario_cn WHERE LOWER(title) LIKE LOWER(?) ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%");
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenarioCn> searchBySubtitle(String subtitle) {
        String sql = "SELECT * FROM video_scenario_cn WHERE LOWER(subtitle) LIKE LOWER(?) ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + subtitle + "%");
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenarioCn> findByColor(String color) {
        String sql = "SELECT * FROM video_scenario_cn WHERE color = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, color);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenarioCn> findByAuthor(String author) {
        String sql = "SELECT * FROM video_scenario_cn WHERE author = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, author);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenarioCn> getAllVideoScenarios(int page, int size) {
        String sql = "SELECT * FROM video_scenario_cn ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToVideoScenario)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有视频场景（分页，使用窗口函数一次性获取数据和总数）
     * 性能优化：使用窗口函数 COUNT(*) OVER() 在单次查询中同时获取数据和总数，避免2次数据库查询
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 包含视频场景列表和总数的分页结果
     */
    public VideoScenarioCnPageResult getAllVideoScenariosWithCount(int page, int size) {
        // 使用窗口函数 COUNT(*) OVER() 在单次查询中获取总数
        String sql = "SELECT *, COUNT(*) OVER() as total_count FROM video_scenario_cn ORDER BY create_at ASC LIMIT ? OFFSET ?";
        
        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, size, offset);
        
        long totalElements = 0;
        List<VideoScenarioCn> videoScenarios = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            // 从第一行获取总数（所有行的 total_count 都相同）
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }
            // 移除 total_count 字段，避免影响 VideoScenarioCn 映射
            Map<String, Object> videoScenarioRow = new HashMap<>(row);
            videoScenarioRow.remove("total_count");
            videoScenarios.add(mapToVideoScenario(videoScenarioRow));
        }
        
        // 转换为DTO
        List<VideoScenarioCnDto> content = videoScenarios.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        
        return new VideoScenarioCnPageResult(content, totalElements);
    }
    
    @Override
    public List<VideoScenarioCn> getAllVideoScenarios() {
        String sql = "SELECT * FROM video_scenario_cn ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenarioCn> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM video_scenario_cn WHERE create_at >= ? AND create_at <= ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public long countByType(String type) {
        String sql = "SELECT COUNT(*) as count FROM video_scenario_cn WHERE type = ?";
        return d1Util.queryLong(sql, type);
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM video_scenario_cn WHERE create_at >= ? AND create_at <= ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
    }
    
    @Override
    public List<VideoScenarioCn> findByTypeAndTitle(String type, String title) {
        String sql = "SELECT * FROM video_scenario_cn WHERE type = ? AND LOWER(title) LIKE LOWER(?) ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, type, "%" + title + "%");
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenarioCn> getVideoScenariosWithAudio() {
        String sql = "SELECT * FROM video_scenario_cn WHERE audiourl IS NOT NULL AND audiourl != '' ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenarioCn> getVideoScenariosWithVideo() {
        String sql = "SELECT * FROM video_scenario_cn WHERE videourl IS NOT NULL AND videourl != '' ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenarioCn> getVideoScenariosWithImage() {
        String sql = "SELECT * FROM video_scenario_cn WHERE image IS NOT NULL AND image != '' ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    /**
     * 保存视频场景
     */
    private VideoScenarioCn saveVideoScenario(VideoScenarioCn videoScenario) {
        if (videoScenario.getVideoId() == null) {
            // 插入新记录
            videoScenario.setVideoId(UUID.randomUUID());
            videoScenario.setCreateAt(OffsetDateTime.now());
            videoScenario.setUpdateAt(OffsetDateTime.now());
            Map<String, Object> data = videoScenarioToMap(videoScenario);
            d1Util.insert("video_scenario_cn", data);
            return videoScenario;
        } else {
            // 更新记录
            videoScenario.preUpdate();
            Map<String, Object> data = videoScenarioToMap(videoScenario);
            d1Util.updateById("video_scenario_cn", data, "videoId", EntityMapper.uuidToString(videoScenario.getVideoId()));
            return videoScenario;
        }
    }
    
    /**
     * 将 Map 转换为 VideoScenarioCn 实体
     */
    private VideoScenarioCn mapToVideoScenario(Map<String, Object> row) {
        VideoScenarioCn videoScenario = new VideoScenarioCn();
        videoScenario.setVideoId(EntityMapper.getUUID(row, "videoId"));
        videoScenario.setType(EntityMapper.getString(row, "type"));
        videoScenario.setTitle(EntityMapper.getString(row, "title"));
        videoScenario.setSubtitle(EntityMapper.getString(row, "subtitle"));
        videoScenario.setImage(EntityMapper.getString(row, "image"));
        videoScenario.setAudiourl(EntityMapper.getString(row, "audiourl"));
        videoScenario.setVideourl(EntityMapper.getString(row, "videourl"));
        videoScenario.setVideourlLd(EntityMapper.getString(row, "videourlLd"));
        videoScenario.setColor(EntityMapper.getString(row, "color"));
        videoScenario.setQuotes(EntityMapper.getString(row, "quotes"));
        videoScenario.setAuthor(EntityMapper.getString(row, "author"));
        videoScenario.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        videoScenario.setUpdateAt(EntityMapper.getOffsetDateTime(row, "update_at"));
        return videoScenario;
    }
    
    /**
     * 将 VideoScenarioCn 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> videoScenarioToMap(VideoScenarioCn videoScenario) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "videoId", videoScenario.getVideoId());
        EntityMapper.putIfNotNull(data, "type", videoScenario.getType());
        EntityMapper.putIfNotNull(data, "title", videoScenario.getTitle());
        EntityMapper.putIfNotNull(data, "subtitle", videoScenario.getSubtitle());
        EntityMapper.putIfNotNull(data, "image", videoScenario.getImage());
        EntityMapper.putIfNotNull(data, "audiourl", videoScenario.getAudiourl());
        EntityMapper.putIfNotNull(data, "videourl", videoScenario.getVideourl());
        EntityMapper.putIfNotNull(data, "videourlLd", videoScenario.getVideourlLd());
        EntityMapper.putIfNotNull(data, "color", videoScenario.getColor());
        EntityMapper.putIfNotNull(data, "quotes", videoScenario.getQuotes());
        EntityMapper.putIfNotNull(data, "author", videoScenario.getAuthor());
        EntityMapper.putIfNotNull(data, "create_at", videoScenario.getCreateAt());
        EntityMapper.putIfNotNull(data, "update_at", videoScenario.getUpdateAt());
        return data;
    }
    
    @Override
    public VideoScenarioCnDto convertToDto(VideoScenarioCn videoScenario) {
        if (videoScenario == null) {
            return null;
        }
        return new VideoScenarioCnDto(
            videoScenario.getVideoId(),
            videoScenario.getType(),
            videoScenario.getTitle(),
            videoScenario.getSubtitle(),
            videoScenario.getImage(),
            videoScenario.getAudiourl(),
            videoScenario.getVideourl(),
            videoScenario.getVideourlLd(),
            videoScenario.getColor(),
            videoScenario.getQuotes(),
            videoScenario.getAuthor(),
            videoScenario.getCreateAt(),
            videoScenario.getUpdateAt()
        );
    }
    
    @Override
    public List<VideoScenarioCnDto> convertToDtoList(List<VideoScenarioCn> videoScenarios) {
        if (videoScenarios == null) {
            return null;
        }
        return videoScenarios.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}

