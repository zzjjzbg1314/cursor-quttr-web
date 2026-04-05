package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.PureMusicDto;
import com.example.cursorquitterweb.dto.PureMusicLanguageContentDto;
import com.example.cursorquitterweb.entity.PureMusic;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import com.example.cursorquitterweb.util.LogUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 纯音乐内容服务类
 */
@Service
public class PureMusicService {

    private static final Logger logger = LogUtil.getLogger(PureMusicService.class);

    @Autowired
    private CloudflareD1Util d1Util;

    @Autowired
    private ObjectMapper objectMapper;

    public Optional<PureMusic> findById(UUID videoId) {
        String sql = "SELECT * FROM pure_music WHERE videoId = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(videoId));
        return row != null ? Optional.of(mapToPureMusic(row)) : Optional.empty();
    }

    @CacheEvict(value = "pureMusics", allEntries = true)
    public PureMusic createPureMusic(String image, String audiourl, String videourl, String videourlLd, String color, Map<String, PureMusicLanguageContentDto> contextText) {
        PureMusic pureMusic = new PureMusic(image, audiourl, videourl, videourlLd, color, serializeContextText(contextText));
        return savePureMusic(pureMusic);
    }

    @CacheEvict(value = "pureMusics", allEntries = true)
    public PureMusic updatePureMusic(UUID videoId, String image, String audiourl, String videourl, String videourlLd, String color, Map<String, PureMusicLanguageContentDto> contextText) {
        PureMusic pureMusic = findById(videoId)
            .orElseThrow(() -> new RuntimeException("纯音乐内容不存在"));

        if (image != null) {
            pureMusic.setImage(image);
        }
        if (audiourl != null) {
            pureMusic.setAudiourl(audiourl);
        }
        if (videourl != null) {
            pureMusic.setVideourl(videourl);
        }
        if (videourlLd != null) {
            pureMusic.setVideourlLd(videourlLd);
        }
        if (color != null) {
            pureMusic.setColor(color);
        }
        if (contextText != null) {
            pureMusic.setContextText(serializeContextText(contextText));
        }

        return savePureMusic(pureMusic);
    }

    @CacheEvict(value = "pureMusics", allEntries = true)
    public void deletePureMusic(UUID videoId) {
        if (!d1Util.exists("pure_music", "videoId = ?", EntityMapper.uuidToString(videoId))) {
            throw new RuntimeException("纯音乐内容不存在");
        }
        d1Util.deleteById("pure_music", "videoId", EntityMapper.uuidToString(videoId));
    }

    public List<PureMusic> getAllPureMusics() {
        String sql = "SELECT * FROM pure_music ORDER BY createAt DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToPureMusic).collect(Collectors.toList());
    }

    public Optional<PureMusicLanguageContentDto> getContextTextByLang(UUID videoId, String lang) {
        if (lang == null || lang.trim().isEmpty()) {
            return Optional.empty();
        }
        Optional<PureMusic> pureMusicOpt = findById(videoId);
        if (!pureMusicOpt.isPresent()) {
            return Optional.empty();
        }
        Map<String, PureMusicLanguageContentDto> contextText = deserializeContextText(pureMusicOpt.get().getContextText());
        PureMusicLanguageContentDto content = findLanguageContent(contextText, lang);
        return Optional.ofNullable(content);
    }

    private PureMusic savePureMusic(PureMusic pureMusic) {
        if (pureMusic.getVideoId() == null) {
            pureMusic.setVideoId(UUID.randomUUID());
            pureMusic.setCreateAt(OffsetDateTime.now());
            pureMusic.setUpdateAt(OffsetDateTime.now());
            d1Util.insert("pure_music", pureMusicToMap(pureMusic));
            return pureMusic;
        }

        pureMusic.preUpdate();
        d1Util.updateById("pure_music", pureMusicToMap(pureMusic), "videoId", EntityMapper.uuidToString(pureMusic.getVideoId()));
        return pureMusic;
    }

    private PureMusic mapToPureMusic(Map<String, Object> row) {
        PureMusic pureMusic = new PureMusic();
        pureMusic.setVideoId(EntityMapper.getUUID(row, "videoId"));
        pureMusic.setImage(EntityMapper.getString(row, "image"));
        pureMusic.setAudiourl(EntityMapper.getString(row, "audiourl"));
        pureMusic.setVideourl(EntityMapper.getString(row, "videourl"));
        pureMusic.setVideourlLd(getCompatibleVideourlLd(row));
        pureMusic.setColor(EntityMapper.getString(row, "color"));
        pureMusic.setContextText(getCompatibleContextText(row));
        pureMusic.setCreateAt(EntityMapper.getOffsetDateTime(row, "createAt"));
        pureMusic.setUpdateAt(EntityMapper.getOffsetDateTime(row, "updateAt"));
        return pureMusic;
    }

    private Map<String, Object> pureMusicToMap(PureMusic pureMusic) {
        Map<String, Object> data = new java.util.HashMap<>();
        EntityMapper.putIfNotNull(data, "videoId", pureMusic.getVideoId());
        EntityMapper.putIfNotNull(data, "image", pureMusic.getImage());
        EntityMapper.putIfNotNull(data, "audiourl", pureMusic.getAudiourl());
        EntityMapper.putIfNotNull(data, "videourl", pureMusic.getVideourl());
        EntityMapper.putIfNotNull(data, "videourlld", pureMusic.getVideourlLd());
        EntityMapper.putIfNotNull(data, "color", pureMusic.getColor());
        EntityMapper.putIfNotNull(data, "contexttext", pureMusic.getContextText());
        EntityMapper.putIfNotNull(data, "createAt", pureMusic.getCreateAt());
        EntityMapper.putIfNotNull(data, "updateAt", pureMusic.getUpdateAt());
        return data;
    }

    private String getCompatibleVideourlLd(Map<String, Object> row) {
        String value = EntityMapper.getString(row, "videourlld");
        return value != null ? value : EntityMapper.getString(row, "videourlLd");
    }

    private String getCompatibleContextText(Map<String, Object> row) {
        String value = EntityMapper.getString(row, "contexttext");
        return value != null ? value : EntityMapper.getString(row, "contextText");
    }

    public PureMusicDto convertToDto(PureMusic pureMusic) {
        if (pureMusic == null) {
            return null;
        }
        return new PureMusicDto(
            pureMusic.getVideoId(),
            pureMusic.getImage(),
            pureMusic.getAudiourl(),
            pureMusic.getVideourl(),
            pureMusic.getVideourlLd(),
            pureMusic.getColor(),
            deserializeContextText(pureMusic.getContextText()),
            pureMusic.getCreateAt(),
            pureMusic.getUpdateAt()
        );
    }

    public List<PureMusicDto> convertToDtoList(List<PureMusic> pureMusics) {
        if (pureMusics == null) {
            return new ArrayList<>();
        }
        return pureMusics.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    private String serializeContextText(Map<String, PureMusicLanguageContentDto> contextText) {
        if (contextText == null || contextText.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(contextText);
        } catch (Exception e) {
            throw new RuntimeException("纯音乐多语言文案序列化失败", e);
        }
    }

    private Map<String, PureMusicLanguageContentDto> deserializeContextText(String contextText) {
        if (contextText == null || contextText.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(
                contextText,
                new TypeReference<Map<String, PureMusicLanguageContentDto>>() {}
            );
        } catch (Exception e) {
            logger.warn("纯音乐多语言文案解析失败，已返回空对象: {}", contextText, e);
            return new LinkedHashMap<>();
        }
    }

    private PureMusicLanguageContentDto findLanguageContent(Map<String, PureMusicLanguageContentDto> contextText, String lang) {
        if (contextText == null || contextText.isEmpty()) {
            return null;
        }
        String normalized = lang.trim();
        PureMusicLanguageContentDto content = contextText.get(normalized);
        if (content != null) {
            return content;
        }
        String lowerCase = normalized.toLowerCase();
        content = contextText.get(lowerCase);
        if (content != null) {
            return content;
        }
        for (Map.Entry<String, PureMusicLanguageContentDto> entry : contextText.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
