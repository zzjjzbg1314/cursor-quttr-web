package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.MeditateVideoCnDto;
import com.example.cursorquitterweb.dto.MeditateVideoCnPageResult;
import com.example.cursorquitterweb.entity.MeditateVideoCn;
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
 * 冥想视频服务实现类（中文版）
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class MeditateVideoCnService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    /**
     * 根据ID查找冥想视频
     */
    public Optional<MeditateVideoCn> findById(UUID id) {
        String sql = "SELECT * FROM meditate_video_cn WHERE id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(id));
        if (row == null) {
            return Optional.empty();
        }
        MeditateVideoCn video = mapToMeditateVideo(row);
        // 加载关联的 quotes
        loadMeditateQuotes(video);
        return Optional.of(video);
    }
    
    /**
     * 创建新冥想视频
     * 清除缓存
     */
    @CacheEvict(value = "meditateVideos", allEntries = true)
    public MeditateVideoCn createMeditateVideo(String title, String subtitle, String image, String videoUrl, 
                                            String audioUrl, List<String> meditateQuotes, String color) {
        MeditateVideoCn meditateVideo = new MeditateVideoCn(title, subtitle, image, videoUrl, audioUrl, meditateQuotes, color);
        return saveMeditateVideo(meditateVideo);
    }
    
    /**
     * 更新冥想视频信息
     * 清除缓存
     */
    @CacheEvict(value = "meditateVideos", allEntries = true)
    public MeditateVideoCn updateMeditateVideo(UUID id, String title, String subtitle, String image, String videoUrl, 
                                           String audioUrl, List<String> meditateQuotes, String color) {
        MeditateVideoCn meditateVideo = findById(id)
                .orElseThrow(() -> new RuntimeException("冥想视频不存在，ID: " + id));
        
        if (title != null) meditateVideo.setTitle(title);
        if (subtitle != null) meditateVideo.setSubtitle(subtitle);
        if (image != null) meditateVideo.setImage(image);
        if (videoUrl != null) meditateVideo.setVideoUrl(videoUrl);
        if (audioUrl != null) meditateVideo.setAudioUrl(audioUrl);
        if (meditateQuotes != null) meditateVideo.setMeditateQuotes(meditateQuotes);
        if (color != null) meditateVideo.setColor(color);
        
        return saveMeditateVideo(meditateVideo);
    }
    
    /**
     * 删除冥想视频
     * 清除缓存
     */
    @CacheEvict(value = "meditateVideos", allEntries = true)
    public void deleteMeditateVideo(UUID id) {
        if (!d1Util.exists("meditate_video_cn", "id = ?", EntityMapper.uuidToString(id))) {
            throw new RuntimeException("冥想视频不存在，ID: " + id);
        }
        // 先删除关联的 quotes
        String deleteQuotesSql = "DELETE FROM meditate_video_quotes_cn WHERE meditate_video_id = ?";
        d1Util.execute(deleteQuotesSql, EntityMapper.uuidToString(id));
        // 再删除视频
        d1Util.deleteById("meditate_video_cn", "id", EntityMapper.uuidToString(id));
    }
    
    /**
     * 根据标题搜索冥想视频
     */
    public List<MeditateVideoCn> searchByTitle(String title) {
        String sql = "SELECT * FROM meditate_video_cn WHERE title LIKE ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%");
        return rows.stream().map(row -> {
            MeditateVideoCn video = mapToMeditateVideo(row);
            loadMeditateQuotes(video);
            return video;
        }).collect(Collectors.toList());
    }
    
    /**
     * 获取所有冥想视频（分页）
     */
    public List<MeditateVideoCn> getAllMeditateVideos(int page, int size) {
        String sql = "SELECT * FROM meditate_video_cn ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(row -> {
                MeditateVideoCn video = mapToMeditateVideo(row);
                loadMeditateQuotes(video);
                return video;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有冥想视频（分页，支持排序，一次性返回数据和总数）
     * 使用窗口函数 COUNT(*) OVER() 在单次查询中同时获取数据和总数，避免2次数据库查询
     */
    public MeditateVideoCnPageResult getAllMeditateVideosWithCount(int page, int size, String sortBy, String sortDir) {
        // 验证排序字段，防止SQL注入
        String validSortBy = validateSortField(sortBy);
        String validSortDir = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";
        
        // 使用窗口函数 COUNT(*) OVER() 在单次查询中获取总数
        String sql = String.format(
            "SELECT *, COUNT(*) OVER() as total_count FROM meditate_video_cn ORDER BY %s %s LIMIT ? OFFSET ?",
            validSortBy, validSortDir
        );
        
        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, size, offset);
        
        long totalElements = 0;
        List<MeditateVideoCn> meditateVideos = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            // 从第一行获取总数（所有行的 total_count 都相同）
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }
            // 移除 total_count 字段，避免影响 MeditateVideoCn 映射
            Map<String, Object> videoRow = new HashMap<>(row);
            videoRow.remove("total_count");
            MeditateVideoCn video = mapToMeditateVideo(videoRow);
            loadMeditateQuotes(video);
            meditateVideos.add(video);
        }
        
        List<MeditateVideoCnDto> content = meditateVideos.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        
        return new MeditateVideoCnPageResult(content, totalElements);
    }
    
    /**
     * 验证排序字段，防止SQL注入
     */
    private String validateSortField(String sortBy) {
        // 允许的排序字段列表（数据库字段名）
        String[] allowedFields = {"create_at", "update_at", "title", "subtitle"};
        
        // 处理前端传入的驼峰命名转换为数据库下划线命名
        String dbFieldName = convertToDbFieldName(sortBy);
        
        for (String field : allowedFields) {
            if (field.equalsIgnoreCase(dbFieldName)) {
                return field;
            }
        }
        // 默认返回 create_at
        return "create_at";
    }
    
    /**
     * 将前端驼峰命名转换为数据库下划线命名
     */
    private String convertToDbFieldName(String fieldName) {
        if (fieldName == null) {
            return "create_at";
        }
        // 处理常见的字段名转换
        switch (fieldName.toLowerCase()) {
            case "createat":
            case "create_at":
                return "create_at";
            case "updateat":
            case "update_at":
                return "update_at";
            case "title":
                return "title";
            case "subtitle":
                return "subtitle";
            default:
                return fieldName;
        }
    }
    
    /**
     * 获取所有冥想视频
     */
    public List<MeditateVideoCn> getAllMeditateVideos() {
        String sql = "SELECT * FROM meditate_video_cn ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(row -> {
            MeditateVideoCn video = mapToMeditateVideo(row);
            loadMeditateQuotes(video);
            return video;
        }).collect(Collectors.toList());
    }
    
    /**
     * 根据时间范围查找冥想视频
     */
    public List<MeditateVideoCn> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM meditate_video_cn WHERE create_at >= ? AND create_at <= ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(row -> {
            MeditateVideoCn video = mapToMeditateVideo(row);
            loadMeditateQuotes(video);
            return video;
        }).collect(Collectors.toList());
    }
    
    /**
     * 统计指定时间范围内的冥想视频数量
     */
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM meditate_video_cn WHERE create_at >= ? AND create_at <= ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
    }
    
    /**
     * 根据视频链接查找冥想视频
     */
    public Optional<MeditateVideoCn> findByVideoUrl(String videoUrl) {
        String sql = "SELECT * FROM meditate_video_cn WHERE video_url = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, videoUrl);
        if (row == null) {
            return Optional.empty();
        }
        MeditateVideoCn video = mapToMeditateVideo(row);
        loadMeditateQuotes(video);
        return Optional.of(video);
    }
    
    /**
     * 根据音频链接查找冥想视频
     */
    public Optional<MeditateVideoCn> findByAudioUrl(String audioUrl) {
        String sql = "SELECT * FROM meditate_video_cn WHERE audio_url = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, audioUrl);
        if (row == null) {
            return Optional.empty();
        }
        MeditateVideoCn video = mapToMeditateVideo(row);
        loadMeditateQuotes(video);
        return Optional.of(video);
    }
    
    /**
     * 根据图片链接查找冥想视频
     */
    public Optional<MeditateVideoCn> findByImage(String image) {
        String sql = "SELECT * FROM meditate_video_cn WHERE image = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, image);
        if (row == null) {
            return Optional.empty();
        }
        MeditateVideoCn video = mapToMeditateVideo(row);
        loadMeditateQuotes(video);
        return Optional.of(video);
    }
    
    /**
     * 获取有视频链接的冥想视频
     */
    public List<MeditateVideoCn> getMeditateVideosWithVideoUrl() {
        String sql = "SELECT * FROM meditate_video_cn WHERE video_url IS NOT NULL AND video_url != '' ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(row -> {
            MeditateVideoCn video = mapToMeditateVideo(row);
            loadMeditateQuotes(video);
            return video;
        }).collect(Collectors.toList());
    }
    
    /**
     * 获取有音频链接的冥想视频
     */
    public List<MeditateVideoCn> getMeditateVideosWithAudioUrl() {
        String sql = "SELECT * FROM meditate_video_cn WHERE audio_url IS NOT NULL AND audio_url != '' ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(row -> {
            MeditateVideoCn video = mapToMeditateVideo(row);
            loadMeditateQuotes(video);
            return video;
        }).collect(Collectors.toList());
    }
    
    /**
     * 获取有图片的冥想视频
     */
    public List<MeditateVideoCn> getMeditateVideosWithImage() {
        String sql = "SELECT * FROM meditate_video_cn WHERE image IS NOT NULL AND image != '' ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(row -> {
            MeditateVideoCn video = mapToMeditateVideo(row);
            loadMeditateQuotes(video);
            return video;
        }).collect(Collectors.toList());
    }
    
    /**
     * 根据颜色查找冥想视频
     */
    public List<MeditateVideoCn> findByColor(String color) {
        String sql = "SELECT * FROM meditate_video_cn WHERE color = ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, color);
        return rows.stream().map(row -> {
            MeditateVideoCn video = mapToMeditateVideo(row);
            loadMeditateQuotes(video);
            return video;
        }).collect(Collectors.toList());
    }
    
    /**
     * 根据标题精确查询冥想视频
     */
    public Optional<MeditateVideoCn> findByTitle(String title) {
        String sql = "SELECT * FROM meditate_video_cn WHERE title = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, title);
        if (row == null) {
            return Optional.empty();
        }
        MeditateVideoCn video = mapToMeditateVideo(row);
        loadMeditateQuotes(video);
        return Optional.of(video);
    }
    
    /**
     * 统计冥想视频总数
     */
    public long count() {
        return d1Util.countTable("meditate_video_cn");
    }
    
    /**
     * 保存冥想视频（包括关联的 quotes）
     */
    private MeditateVideoCn saveMeditateVideo(MeditateVideoCn meditateVideo) {
        if (meditateVideo.getId() == null) {
            // 插入新记录
            meditateVideo.setId(UUID.randomUUID());
            meditateVideo.setCreateAt(OffsetDateTime.now());
            meditateVideo.setUpdateAt(OffsetDateTime.now());
            Map<String, Object> data = meditateVideoToMap(meditateVideo);
            d1Util.insert("meditate_video_cn", data);
            // 保存关联的 quotes
            saveMeditateQuotes(meditateVideo);
            return meditateVideo;
        } else {
            // 更新记录
            meditateVideo.preUpdate();
            Map<String, Object> data = meditateVideoToMap(meditateVideo);
            d1Util.updateById("meditate_video_cn", data, "id", EntityMapper.uuidToString(meditateVideo.getId()));
            // 更新关联的 quotes（先删除旧的，再插入新的）
            String deleteQuotesSql = "DELETE FROM meditate_video_quotes_cn WHERE meditate_video_id = ?";
            d1Util.execute(deleteQuotesSql, EntityMapper.uuidToString(meditateVideo.getId()));
            saveMeditateQuotes(meditateVideo);
            return meditateVideo;
        }
    }
    
    /**
     * 保存关联的 quotes
     */
    private void saveMeditateQuotes(MeditateVideoCn meditateVideo) {
        if (meditateVideo.getMeditateQuotes() != null && !meditateVideo.getMeditateQuotes().isEmpty()) {
            for (String quote : meditateVideo.getMeditateQuotes()) {
                String sql = "INSERT INTO meditate_video_quotes_cn (meditate_video_id, quote) VALUES (?, ?)";
                d1Util.execute(sql, EntityMapper.uuidToString(meditateVideo.getId()), quote);
            }
        }
    }
    
    /**
     * 加载关联的 quotes
     */
    private void loadMeditateQuotes(MeditateVideoCn meditateVideo) {
        if (meditateVideo == null) {
            return;
        }
        
        if (meditateVideo.getId() == null) {
            meditateVideo.setMeditateQuotes(java.util.Collections.emptyList());
            return;
        }
        
        try {
            // 使用 UUID 字符串格式查询关联表
            String videoId = EntityMapper.uuidToString(meditateVideo.getId());
            String sql = "SELECT quote FROM meditate_video_quotes_cn WHERE meditate_video_id = ? ORDER BY quote";
            List<Map<String, Object>> rows = d1Util.queryList(sql, videoId);
            
            if (rows == null || rows.isEmpty()) {
                meditateVideo.setMeditateQuotes(java.util.Collections.emptyList());
                return;
            }
            
            List<String> quotes = rows.stream()
                .map(row -> EntityMapper.getString(row, "quote"))
                .filter(quote -> quote != null && !quote.trim().isEmpty())
                .collect(Collectors.toList());
            
            meditateVideo.setMeditateQuotes(quotes != null ? quotes : java.util.Collections.emptyList());
        } catch (Exception e) {
            // 如果查询失败，设置为空列表
            meditateVideo.setMeditateQuotes(java.util.Collections.emptyList());
        }
    }
    
    /**
     * 将 Map 转换为 MeditateVideoCn 实体
     */
    private MeditateVideoCn mapToMeditateVideo(Map<String, Object> row) {
        MeditateVideoCn meditateVideo = new MeditateVideoCn();
        meditateVideo.setId(EntityMapper.getUUID(row, "id"));
        meditateVideo.setTitle(EntityMapper.getString(row, "title"));
        meditateVideo.setSubtitle(EntityMapper.getString(row, "subtitle"));
        meditateVideo.setImage(EntityMapper.getString(row, "image"));
        // 数据库字段名是 video_url 和 audio_url（下划线命名）
        meditateVideo.setVideoUrl(EntityMapper.getString(row, "video_url"));
        meditateVideo.setAudioUrl(EntityMapper.getString(row, "audio_url"));
        meditateVideo.setColor(EntityMapper.getString(row, "color"));
        meditateVideo.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        meditateVideo.setUpdateAt(EntityMapper.getOffsetDateTime(row, "update_at"));
        return meditateVideo;
    }
    
    /**
     * 将 MeditateVideoCn 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> meditateVideoToMap(MeditateVideoCn meditateVideo) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", meditateVideo.getId());
        EntityMapper.putIfNotNull(data, "title", meditateVideo.getTitle());
        EntityMapper.putIfNotNull(data, "subtitle", meditateVideo.getSubtitle());
        EntityMapper.putIfNotNull(data, "image", meditateVideo.getImage());
        // 数据库字段名是 video_url 和 audio_url（下划线命名）
        EntityMapper.putIfNotNull(data, "video_url", meditateVideo.getVideoUrl());
        EntityMapper.putIfNotNull(data, "audio_url", meditateVideo.getAudioUrl());
        EntityMapper.putIfNotNull(data, "color", meditateVideo.getColor());
        EntityMapper.putIfNotNull(data, "create_at", meditateVideo.getCreateAt());
        EntityMapper.putIfNotNull(data, "update_at", meditateVideo.getUpdateAt());
        return data;
    }
    
    /**
     * 转换为DTO
     */
    public MeditateVideoCnDto convertToDto(MeditateVideoCn meditateVideo) {
        if (meditateVideo == null) {
            return null;
        }
        return new MeditateVideoCnDto(
                meditateVideo.getId(),
                meditateVideo.getTitle(),
                meditateVideo.getSubtitle(),
                meditateVideo.getImage(),
                meditateVideo.getVideoUrl(),
                meditateVideo.getAudioUrl(),
                meditateVideo.getMeditateQuotes(),
                meditateVideo.getColor(),
                meditateVideo.getCreateAt(),
                meditateVideo.getUpdateAt()
        );
    }
    
    /**
     * 批量转换为DTO
     */
    public List<MeditateVideoCnDto> convertToDtoList(List<MeditateVideoCn> meditateVideos) {
        if (meditateVideos == null) {
            return null;
        }
        return meditateVideos.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}

