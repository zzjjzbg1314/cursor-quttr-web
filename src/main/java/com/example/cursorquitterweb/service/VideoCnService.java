package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.VideoCnDto;
import com.example.cursorquitterweb.dto.VideoCnPageResult;
import com.example.cursorquitterweb.entity.VideoCn;
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
 * 视频服务实现类（中文版）
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class VideoCnService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    /**
     * 根据ID查找视频
     */
    public Optional<VideoCn> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("video_cn", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToVideo(row)) : Optional.empty();
    }
    
    /**
     * 创建新视频
     * 清除缓存
     */
    @CacheEvict(value = "videos", allEntries = true)
    public VideoCn createVideo(String title, String playurl, String posturl) {
        VideoCn video = new VideoCn(title, playurl, posturl);
        return saveVideo(video);
    }
    
    /**
     * 更新视频信息
     * 清除缓存
     */
    @CacheEvict(value = "videos", allEntries = true)
    public VideoCn updateVideo(UUID id, String title, String playurl, String posturl) {
        VideoCn video = findById(id)
                .orElseThrow(() -> new RuntimeException("视频不存在，ID: " + id));
        
        video.setTitle(title);
        video.setPlayurl(playurl);
        video.setPosturl(posturl);
        
        return saveVideo(video);
    }
    
    /**
     * 删除视频
     * 清除缓存
     */
    @CacheEvict(value = "videos", allEntries = true)
    public void deleteVideo(UUID id) {
        if (!d1Util.exists("video_cn", "id = ?", EntityMapper.uuidToString(id))) {
            throw new RuntimeException("视频不存在，ID: " + id);
        }
        d1Util.deleteById("video_cn", "id", EntityMapper.uuidToString(id));
    }
    
    /**
     * 根据标题搜索视频
     */
    public List<VideoCn> searchByTitle(String title) {
        String sql = "SELECT * FROM video_cn WHERE LOWER(title) LIKE LOWER(?) ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%");
        return rows.stream().map(this::mapToVideo).collect(Collectors.toList());
    }
    
    /**
     * 获取所有视频（分页）
     * 注意：返回的是 List，不再使用 Spring Data 的 Page 对象
     */
    public List<VideoCn> getAllVideos(int page, int size) {
        String sql = "SELECT * FROM video_cn ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToVideo)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有视频（分页，使用窗口函数一次性获取数据和总数）
     * 性能优化：使用窗口函数 COUNT(*) OVER() 在单次查询中同时获取数据和总数，避免2次数据库查询
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 包含视频列表和总数的分页结果
     */
    public VideoCnPageResult getAllVideosWithCount(int page, int size) {
        // 使用窗口函数 COUNT(*) OVER() 在单次查询中获取总数
        String sql = "SELECT *, COUNT(*) OVER() as total_count FROM video_cn ORDER BY create_at ASC LIMIT ? OFFSET ?";
        
        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, size, offset);
        
        long totalElements = 0;
        List<VideoCn> videos = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            // 从第一行获取总数（所有行的 total_count 都相同）
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }
            // 移除 total_count 字段，避免影响 VideoCn 映射
            Map<String, Object> videoRow = new HashMap<>(row);
            videoRow.remove("total_count");
            videos.add(mapToVideo(videoRow));
        }
        
        // 转换为DTO
        List<VideoCnDto> content = videos.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        
        return new VideoCnPageResult(content, totalElements);
    }
    
    /**
     * 获取所有视频
     */
    public List<VideoCn> getAllVideos() {
        String sql = "SELECT * FROM video_cn ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideo).collect(Collectors.toList());
    }
    
    /**
     * 根据时间范围查找视频
     */
    public List<VideoCn> findByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM video_cn WHERE create_at >= ? AND create_at <= ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToVideo).collect(Collectors.toList());
    }
    
    /**
     * 统计指定时间范围内的视频数量
     */
    public long countByTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM video_cn WHERE create_at >= ? AND create_at <= ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
    }
    
    /**
     * 根据播放链接查找视频
     */
    public Optional<VideoCn> findByPlayurl(String playurl) {
        String sql = "SELECT * FROM video_cn WHERE playurl = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, playurl);
        return row != null ? Optional.of(mapToVideo(row)) : Optional.empty();
    }
    
    /**
     * 根据海报链接查找视频
     */
    public Optional<VideoCn> findByPosturl(String posturl) {
        String sql = "SELECT * FROM video_cn WHERE posturl = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, posturl);
        return row != null ? Optional.of(mapToVideo(row)) : Optional.empty();
    }
    
    /**
     * 获取有播放链接的视频
     */
    public List<VideoCn> getVideosWithPlayurl() {
        String sql = "SELECT * FROM video_cn WHERE playurl IS NOT NULL AND playurl != '' ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideo).collect(Collectors.toList());
    }
    
    /**
     * 获取有海报链接的视频
     */
    public List<VideoCn> getVideosWithPosturl() {
        String sql = "SELECT * FROM video_cn WHERE posturl IS NOT NULL AND posturl != '' ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideo).collect(Collectors.toList());
    }
    
    /**
     * 根据标题精确查询视频
     */
    public Optional<VideoCn> findByTitle(String title) {
        String sql = "SELECT * FROM video_cn WHERE title = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, title);
        return row != null ? Optional.of(mapToVideo(row)) : Optional.empty();
    }
    
    /**
     * 统计视频总数
     */
    public long count() {
        return d1Util.countTable("video_cn");
    }
    
    /**
     * 保存视频
     */
    private VideoCn saveVideo(VideoCn video) {
        if (video.getId() == null) {
            // 插入新记录
            video.setId(UUID.randomUUID());
            video.setCreateAt(OffsetDateTime.now());
            video.setUpdateAt(OffsetDateTime.now());
            Map<String, Object> data = videoToMap(video);
            d1Util.insert("video_cn", data);
            return video;
        } else {
            // 更新记录
            video.preUpdate();
            Map<String, Object> data = videoToMap(video);
            d1Util.updateById("video_cn", data, "id", EntityMapper.uuidToString(video.getId()));
            return video;
        }
    }
    
    /**
     * 将 Map 转换为 VideoCn 实体
     */
    private VideoCn mapToVideo(Map<String, Object> row) {
        VideoCn video = new VideoCn();
        video.setId(EntityMapper.getUUID(row, "id"));
        video.setTitle(EntityMapper.getString(row, "title"));
        video.setPlayurl(EntityMapper.getString(row, "playurl"));
        video.setPosturl(EntityMapper.getString(row, "posturl"));
        video.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        video.setUpdateAt(EntityMapper.getOffsetDateTime(row, "update_at"));
        return video;
    }
    
    /**
     * 将 VideoCn 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> videoToMap(VideoCn video) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", video.getId());
        EntityMapper.putIfNotNull(data, "title", video.getTitle());
        EntityMapper.putIfNotNull(data, "playurl", video.getPlayurl());
        EntityMapper.putIfNotNull(data, "posturl", video.getPosturl());
        EntityMapper.putIfNotNull(data, "create_at", video.getCreateAt());
        EntityMapper.putIfNotNull(data, "update_at", video.getUpdateAt());
        return data;
    }
    
    /**
     * 转换为DTO
     */
    public VideoCnDto convertToDto(VideoCn video) {
        if (video == null) {
            return null;
        }
        return new VideoCnDto(
                video.getId(),
                video.getTitle(),
                video.getPlayurl(),
                video.getPosturl(),
                video.getCreateAt(),
                video.getUpdateAt()
        );
    }
    
    /**
     * 批量转换为DTO
     */
    public List<VideoCnDto> convertToDtoList(List<VideoCn> videos) {
        if (videos == null) {
            return null;
        }
        return videos.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}

