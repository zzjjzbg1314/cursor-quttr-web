package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.CreateSisterMotivationVideoRequest;
import com.example.cursorquitterweb.dto.SisterMotivationVideoDto;
import com.example.cursorquitterweb.dto.SisterMotivationVideoPageResult;
import com.example.cursorquitterweb.dto.UpdateSisterMotivationVideoRequest;
import com.example.cursorquitterweb.entity.SisterMotivationVideo;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 姐姐激励视频服务
 * 使用 CloudflareD1Util 操作 sister_motivation_videos 表
 */
@Service
public class SisterMotivationVideoService {

    private static final String TABLE_NAME = "sister_motivation_videos";
    private static final String ID_COLUMN = "videoId";
    private static final DateTimeFormatter SQLITE_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private CloudflareD1Util d1Util;

    @Autowired
    private ObjectMapper objectMapper;

    public Optional<SisterMotivationVideo> findById(String videoId) {
        Map<String, Object> row = d1Util.findById(TABLE_NAME, ID_COLUMN, videoId);
        return row != null ? Optional.of(mapToVideo(row)) : Optional.empty();
    }

    @CacheEvict(value = "sisterMotivationVideos", allEntries = true)
    public SisterMotivationVideo createVideo(CreateSisterMotivationVideoRequest request) {
        SisterMotivationVideo video = new SisterMotivationVideo();
        video.setTitle(serializeTitle(request.getTitle()));
        video.setImage(request.getImage());
        video.setVideourl(request.getVideourl());
        video.setVideourlld(request.getVideourlld());
        video.setVideourlSg(request.getVideourlSg());
        video.setVideourlldSg(request.getVideourlldSg());
        video.setVideourlUs(request.getVideourlUs());
        video.setVideourlldUs(request.getVideourlldUs());
        video.setVideourlDe(request.getVideourlDe());
        video.setVideourlldDe(request.getVideourlldDe());
        return saveVideo(video);
    }

    @CacheEvict(value = "sisterMotivationVideos", allEntries = true)
    public SisterMotivationVideo updateVideo(String videoId, UpdateSisterMotivationVideoRequest request) {
        SisterMotivationVideo video = findById(videoId)
                .orElseThrow(() -> new RuntimeException("姐姐激励视频不存在，ID: " + videoId));

        applyNonNullFields(video, request);
        return saveVideo(video);
    }

    @CacheEvict(value = "sisterMotivationVideos", allEntries = true)
    public void deleteVideo(String videoId) {
        if (!d1Util.exists(TABLE_NAME, ID_COLUMN + " = ?", videoId)) {
            throw new RuntimeException("姐姐激励视频不存在，ID: " + videoId);
        }
        d1Util.deleteById(TABLE_NAME, ID_COLUMN, videoId);
    }

    public List<SisterMotivationVideo> searchByTitle(String title) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE LOWER(title) LIKE LOWER(?) ORDER BY updateAt ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%");
        return rows.stream().map(this::mapToVideo).collect(Collectors.toList());
    }

    public SisterMotivationVideoPageResult getAllVideosWithCount(int page, int size) {
        String sql = "SELECT *, COUNT(*) OVER() as total_count FROM " + TABLE_NAME
                + " ORDER BY updateAt ASC LIMIT ? OFFSET ?";

        int offset = page * size;
        List<Map<String, Object>> rows = d1Util.queryList(sql, size, offset);

        long totalElements = 0;
        List<SisterMotivationVideo> videos = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            if (totalElements == 0 && row.get("total_count") != null) {
                totalElements = ((Number) row.get("total_count")).longValue();
            }

            Map<String, Object> videoRow = new HashMap<>(row);
            videoRow.remove("total_count");
            videos.add(mapToVideo(videoRow));
        }

        List<SisterMotivationVideoDto> content = videos.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return new SisterMotivationVideoPageResult(content, totalElements);
    }

    public List<SisterMotivationVideo> getAllVideos() {
        String sql = "SELECT * FROM " + TABLE_NAME + " ORDER BY updateAt ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToVideo).collect(Collectors.toList());
    }

    public long count() {
        return d1Util.countTable(TABLE_NAME);
    }

    public SisterMotivationVideoDto convertToDto(SisterMotivationVideo video) {
        if (video == null) {
            return null;
        }

        return new SisterMotivationVideoDto(
                video.getVideoId(),
                deserializeTitle(video.getTitle()),
                video.getImage(),
                video.getVideourl(),
                video.getVideourlld(),
                video.getVideourlSg(),
                video.getVideourlldSg(),
                video.getVideourlUs(),
                video.getVideourlldUs(),
                video.getVideourlDe(),
                video.getVideourlldDe(),
                video.getUpdateAt()
        );
    }

    public List<SisterMotivationVideoDto> convertToDtoList(List<SisterMotivationVideo> videos) {
        if (videos == null) {
            return null;
        }
        return videos.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private SisterMotivationVideo saveVideo(SisterMotivationVideo video) {
        if (video.getVideoId() == null || video.getVideoId().trim().isEmpty()) {
            video.setVideoId(generateVideoId());
            video.setUpdateAt(OffsetDateTime.now());
            d1Util.insert(TABLE_NAME, videoToMap(video));
            return video;
        }

        video.preUpdate();
        d1Util.updateById(TABLE_NAME, videoToMap(video), ID_COLUMN, video.getVideoId());
        return video;
    }

    private SisterMotivationVideo mapToVideo(Map<String, Object> row) {
        SisterMotivationVideo video = new SisterMotivationVideo();
        video.setVideoId(EntityMapper.getString(row, "videoId"));
        video.setTitle(EntityMapper.getString(row, "title"));
        video.setImage(EntityMapper.getString(row, "image"));
        video.setVideourl(EntityMapper.getString(row, "videourl"));
        video.setVideourlld(EntityMapper.getString(row, "videourlld"));
        video.setVideourlSg(EntityMapper.getString(row, "videourl_sg"));
        video.setVideourlldSg(EntityMapper.getString(row, "videourlld_sg"));
        video.setVideourlUs(EntityMapper.getString(row, "videourl_us"));
        video.setVideourlldUs(EntityMapper.getString(row, "videourlld_us"));
        video.setVideourlDe(EntityMapper.getString(row, "videourl_de"));
        video.setVideourlldDe(EntityMapper.getString(row, "videourlld_de"));
        video.setUpdateAt(getOffsetDateTime(row, "updateAt"));
        return video;
    }

    private Map<String, Object> videoToMap(SisterMotivationVideo video) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "videoId", video.getVideoId());
        EntityMapper.putIfNotNull(data, "title", video.getTitle());
        EntityMapper.putIfNotNull(data, "image", video.getImage());
        EntityMapper.putIfNotNull(data, "videourl", video.getVideourl());
        EntityMapper.putIfNotNull(data, "videourlld", video.getVideourlld());
        EntityMapper.putIfNotNull(data, "videourl_sg", video.getVideourlSg());
        EntityMapper.putIfNotNull(data, "videourlld_sg", video.getVideourlldSg());
        EntityMapper.putIfNotNull(data, "videourl_us", video.getVideourlUs());
        EntityMapper.putIfNotNull(data, "videourlld_us", video.getVideourlldUs());
        EntityMapper.putIfNotNull(data, "videourl_de", video.getVideourlDe());
        EntityMapper.putIfNotNull(data, "videourlld_de", video.getVideourlldDe());
        EntityMapper.putIfNotNull(data, "updateAt", video.getUpdateAt());
        return data;
    }

    private void applyNonNullFields(SisterMotivationVideo video, UpdateSisterMotivationVideoRequest request) {
        if (request.getTitle() != null) {
            video.setTitle(serializeTitle(request.getTitle()));
        }
        if (request.getImage() != null) {
            video.setImage(request.getImage());
        }
        if (request.getVideourl() != null) {
            video.setVideourl(request.getVideourl());
        }
        if (request.getVideourlld() != null) {
            video.setVideourlld(request.getVideourlld());
        }
        if (request.getVideourlSg() != null) {
            video.setVideourlSg(request.getVideourlSg());
        }
        if (request.getVideourlldSg() != null) {
            video.setVideourlldSg(request.getVideourlldSg());
        }
        if (request.getVideourlUs() != null) {
            video.setVideourlUs(request.getVideourlUs());
        }
        if (request.getVideourlldUs() != null) {
            video.setVideourlldUs(request.getVideourlldUs());
        }
        if (request.getVideourlDe() != null) {
            video.setVideourlDe(request.getVideourlDe());
        }
        if (request.getVideourlldDe() != null) {
            video.setVideourlldDe(request.getVideourlldDe());
        }
    }

    private String generateVideoId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String serializeTitle(Map<String, String> title) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(title);
        } catch (Exception e) {
            throw new RuntimeException("姐姐激励视频多语言标题序列化失败", e);
        }
    }

    private Map<String, String> deserializeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(title, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new RuntimeException("姐姐激励视频多语言标题解析失败", e);
        }
    }

    private OffsetDateTime getOffsetDateTime(Map<String, Object> row, String key) {
        OffsetDateTime parsed = EntityMapper.getOffsetDateTime(row, key);
        if (parsed != null) {
            return parsed;
        }

        String value = EntityMapper.getString(row, key);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(value.trim(), SQLITE_DATETIME_FORMATTER);
            return localDateTime.atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }
}
