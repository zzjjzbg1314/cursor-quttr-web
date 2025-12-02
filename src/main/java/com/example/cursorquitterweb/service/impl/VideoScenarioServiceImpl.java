package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.VideoScenarioDto;
import com.example.cursorquitterweb.entity.VideoScenario;
import com.example.cursorquitterweb.service.VideoScenarioService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 视频场景服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class VideoScenarioServiceImpl implements VideoScenarioService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public Optional<VideoScenario> findById(UUID videoId) {
        String sql = "SELECT * FROM video_scenario WHERE videoId = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(videoId));
        return row != null ? Optional.of(mapToVideoScenario(row)) : Optional.empty();
    }
    
    @Override
    public VideoScenario createVideoScenario(String type, String title, String subtitle, String image, 
                                           String audiourl, String videourl, String color, String quotes, String author) {
        VideoScenario videoScenario = new VideoScenario(type, title, subtitle, image, audiourl, videourl, color, quotes, author);
        return saveVideoScenario(videoScenario);
    }
    
    @Override
    public VideoScenario updateVideoScenario(UUID videoId, String type, String title, String subtitle, 
                                            String image, String audiourl, String videourl, String color, 
                                            String quotes, String author) {
        Optional<VideoScenario> optionalVideoScenario = findById(videoId);
        if (optionalVideoScenario.isPresent()) {
            VideoScenario videoScenario = optionalVideoScenario.get();
            if (type != null) videoScenario.setType(type);
            if (title != null) videoScenario.setTitle(title);
            if (subtitle != null) videoScenario.setSubtitle(subtitle);
            if (image != null) videoScenario.setImage(image);
            if (audiourl != null) videoScenario.setAudiourl(audiourl);
            if (videourl != null) videoScenario.setVideourl(videourl);
            if (color != null) videoScenario.setColor(color);
            if (quotes != null) videoScenario.setQuotes(quotes);
            if (author != null) videoScenario.setAuthor(author);
            return saveVideoScenario(videoScenario);
        }
        throw new RuntimeException("视频场景不存在");
    }
    
    @Override
    public void deleteVideoScenario(UUID videoId) {
        if (!d1Util.exists("video_scenario", "videoId = ?", EntityMapper.uuidToString(videoId))) {
            throw new RuntimeException("视频场景不存在");
        }
        d1Util.deleteById("video_scenario", "videoId", EntityMapper.uuidToString(videoId));
    }
    
    @Override
    public List<VideoScenario> findByType(String type) {
        String sql = "SELECT * FROM video_scenario WHERE type = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, type);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> findByType(String type, int page, int size) {
        String sql = "SELECT * FROM video_scenario WHERE type = ? ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size, type).stream()
            .map(this::mapToVideoScenario)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> searchByTitle(String title) {
        String sql = "SELECT * FROM video_scenario WHERE LOWER(title) LIKE LOWER(?) ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%");
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> searchBySubtitle(String subtitle) {
        String sql = "SELECT * FROM video_scenario WHERE LOWER(subtitle) LIKE LOWER(?) ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + subtitle + "%");
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> findByColor(String color) {
        String sql = "SELECT * FROM video_scenario WHERE color = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, color);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> findByAuthor(String author) {
        String sql = "SELECT * FROM video_scenario WHERE author = ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, author);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> getAllVideoScenarios(int page, int size) {
        String sql = "SELECT * FROM video_scenario ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToVideoScenario)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> getAllVideoScenarios() {
        String sql = "SELECT * FROM video_scenario ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM video_scenario WHERE create_at >= ? AND create_at <= ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public long countByType(String type) {
        String sql = "SELECT COUNT(*) as count FROM video_scenario WHERE type = ?";
        return d1Util.queryLong(sql, type);
    }
    
    @Override
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM video_scenario WHERE create_at >= ? AND create_at <= ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
    }
    
    @Override
    public List<VideoScenario> findByTypeAndTitle(String type, String title) {
        String sql = "SELECT * FROM video_scenario WHERE type = ? AND LOWER(title) LIKE LOWER(?) ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, type, "%" + title + "%");
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> getVideoScenariosWithAudio() {
        String sql = "SELECT * FROM video_scenario WHERE audiourl IS NOT NULL AND audiourl != '' ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> getVideoScenariosWithVideo() {
        String sql = "SELECT * FROM video_scenario WHERE videourl IS NOT NULL AND videourl != '' ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    @Override
    public List<VideoScenario> getVideoScenariosWithImage() {
        String sql = "SELECT * FROM video_scenario WHERE image IS NOT NULL AND image != '' ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideoScenario).collect(Collectors.toList());
    }
    
    /**
     * 保存视频场景
     */
    private VideoScenario saveVideoScenario(VideoScenario videoScenario) {
        if (videoScenario.getVideoId() == null) {
            // 插入新记录
            videoScenario.setVideoId(UUID.randomUUID());
            videoScenario.setCreateAt(OffsetDateTime.now());
            videoScenario.setUpdateAt(OffsetDateTime.now());
            Map<String, Object> data = videoScenarioToMap(videoScenario);
            d1Util.insert("video_scenario", data);
            return videoScenario;
        } else {
            // 更新记录
            videoScenario.preUpdate();
            Map<String, Object> data = videoScenarioToMap(videoScenario);
            d1Util.updateById("video_scenario", data, "videoId", EntityMapper.uuidToString(videoScenario.getVideoId()));
            return videoScenario;
        }
    }
    
    /**
     * 将 Map 转换为 VideoScenario 实体
     */
    private VideoScenario mapToVideoScenario(Map<String, Object> row) {
        VideoScenario videoScenario = new VideoScenario();
        videoScenario.setVideoId(EntityMapper.getUUID(row, "videoId"));
        videoScenario.setType(EntityMapper.getString(row, "type"));
        videoScenario.setTitle(EntityMapper.getString(row, "title"));
        videoScenario.setSubtitle(EntityMapper.getString(row, "subtitle"));
        videoScenario.setImage(EntityMapper.getString(row, "image"));
        videoScenario.setAudiourl(EntityMapper.getString(row, "audiourl"));
        videoScenario.setVideourl(EntityMapper.getString(row, "videourl"));
        videoScenario.setColor(EntityMapper.getString(row, "color"));
        videoScenario.setQuotes(EntityMapper.getString(row, "quotes"));
        videoScenario.setAuthor(EntityMapper.getString(row, "author"));
        videoScenario.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        videoScenario.setUpdateAt(EntityMapper.getOffsetDateTime(row, "update_at"));
        return videoScenario;
    }
    
    /**
     * 将 VideoScenario 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> videoScenarioToMap(VideoScenario videoScenario) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "videoId", videoScenario.getVideoId());
        EntityMapper.putIfNotNull(data, "type", videoScenario.getType());
        EntityMapper.putIfNotNull(data, "title", videoScenario.getTitle());
        EntityMapper.putIfNotNull(data, "subtitle", videoScenario.getSubtitle());
        EntityMapper.putIfNotNull(data, "image", videoScenario.getImage());
        EntityMapper.putIfNotNull(data, "audiourl", videoScenario.getAudiourl());
        EntityMapper.putIfNotNull(data, "videourl", videoScenario.getVideourl());
        EntityMapper.putIfNotNull(data, "color", videoScenario.getColor());
        EntityMapper.putIfNotNull(data, "quotes", videoScenario.getQuotes());
        EntityMapper.putIfNotNull(data, "author", videoScenario.getAuthor());
        EntityMapper.putIfNotNull(data, "create_at", videoScenario.getCreateAt());
        EntityMapper.putIfNotNull(data, "update_at", videoScenario.getUpdateAt());
        return data;
    }
    
    @Override
    public VideoScenarioDto convertToDto(VideoScenario videoScenario) {
        if (videoScenario == null) {
            return null;
        }
        return new VideoScenarioDto(
            videoScenario.getVideoId(),
            videoScenario.getType(),
            videoScenario.getTitle(),
            videoScenario.getSubtitle(),
            videoScenario.getImage(),
            videoScenario.getAudiourl(),
            videoScenario.getVideourl(),
            videoScenario.getColor(),
            videoScenario.getQuotes(),
            videoScenario.getAuthor(),
            videoScenario.getCreateAt(),
            videoScenario.getUpdateAt()
        );
    }
    
    @Override
    public List<VideoScenarioDto> convertToDtoList(List<VideoScenario> videoScenarios) {
        if (videoScenarios == null) {
            return null;
        }
        return videoScenarios.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}
