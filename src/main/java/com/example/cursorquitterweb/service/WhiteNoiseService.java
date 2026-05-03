package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.WhiteNoiseDto;
import com.example.cursorquitterweb.dto.WhiteNoiseLanguageContentDto;
import com.example.cursorquitterweb.dto.CreateWhiteNoiseRequest;
import com.example.cursorquitterweb.dto.UpdateWhiteNoiseRequest;
import com.example.cursorquitterweb.entity.WhiteNoise;
import com.example.cursorquitterweb.util.LogUtil;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 白噪音内容服务类
 */
@Service
public class WhiteNoiseService {

    private static final Logger logger = LogUtil.getLogger(WhiteNoiseService.class);

    @Autowired
    private CloudflareD1Util d1Util;

    @Autowired
    private ObjectMapper objectMapper;

    public Optional<WhiteNoise> findById(UUID videoId) {
        String sql = "SELECT * FROM white_noise WHERE videoId = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(videoId));
        return row != null ? Optional.of(mapToWhiteNoise(row)) : Optional.empty();
    }

    @CacheEvict(value = "whiteNoises", allEntries = true)
    public WhiteNoise createWhiteNoise(CreateWhiteNoiseRequest request) {
        WhiteNoise whiteNoise = new WhiteNoise();
        applyRequestFields(whiteNoise, request.getImage(), request.getAudiourl(), request.getVideourl(), request.getVideourlLd(),
                request.getAudiourlSg(), request.getVideourlSg(), request.getVideourlLdSg(),
                request.getAudiourlUs(), request.getVideourlUs(), request.getVideourlLdUs(),
                request.getAudiourlDe(), request.getVideourlDe(), request.getVideourlLdDe(),
                request.getColor(), request.getContextText());
        return saveWhiteNoise(whiteNoise);
    }

    @CacheEvict(value = "whiteNoises", allEntries = true)
    public WhiteNoise updateWhiteNoise(UUID videoId, UpdateWhiteNoiseRequest request) {
        WhiteNoise whiteNoise = findById(videoId)
            .orElseThrow(() -> new RuntimeException("白噪音内容不存在"));

        applyRequestFields(whiteNoise, request.getImage(), request.getAudiourl(), request.getVideourl(), request.getVideourlLd(),
                request.getAudiourlSg(), request.getVideourlSg(), request.getVideourlLdSg(),
                request.getAudiourlUs(), request.getVideourlUs(), request.getVideourlLdUs(),
                request.getAudiourlDe(), request.getVideourlDe(), request.getVideourlLdDe(),
                request.getColor(), request.getContextText());

        return saveWhiteNoise(whiteNoise);
    }

    @CacheEvict(value = "whiteNoises", allEntries = true)
    public void deleteWhiteNoise(UUID videoId) {
        if (!d1Util.exists("white_noise", "videoId = ?", EntityMapper.uuidToString(videoId))) {
            throw new RuntimeException("白噪音内容不存在");
        }
        d1Util.deleteById("white_noise", "videoId", EntityMapper.uuidToString(videoId));
    }

    public List<WhiteNoise> getAllWhiteNoises() {
        String sql = "SELECT * FROM white_noise ORDER BY createAt DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToWhiteNoise).collect(Collectors.toList());
    }

    private WhiteNoise saveWhiteNoise(WhiteNoise whiteNoise) {
        if (whiteNoise.getVideoId() == null) {
            whiteNoise.setVideoId(UUID.randomUUID());
            whiteNoise.setCreateAt(OffsetDateTime.now());
            whiteNoise.setUpdateAt(OffsetDateTime.now());
            d1Util.insert("white_noise", whiteNoiseToMap(whiteNoise));
            return whiteNoise;
        }

        whiteNoise.preUpdate();
        d1Util.updateById("white_noise", whiteNoiseToMap(whiteNoise), "videoId", EntityMapper.uuidToString(whiteNoise.getVideoId()));
        return whiteNoise;
    }

    private WhiteNoise mapToWhiteNoise(Map<String, Object> row) {
        WhiteNoise whiteNoise = new WhiteNoise();
        whiteNoise.setVideoId(EntityMapper.getUUID(row, "videoId"));
        whiteNoise.setImage(EntityMapper.getString(row, "image"));
        whiteNoise.setAudiourl(EntityMapper.getString(row, "audiourl"));
        whiteNoise.setVideourl(EntityMapper.getString(row, "videourl"));
        whiteNoise.setVideourlLd(getCompatibleVideourlLd(row));
        whiteNoise.setAudiourlSg(getCompatibleString(row, "audiourl_sg", "audiourlSg"));
        whiteNoise.setVideourlSg(getCompatibleString(row, "videourl_sg", "videourlSg"));
        whiteNoise.setVideourlLdSg(getCompatibleString(row, "videourlld_sg", "videourlLdSg"));
        whiteNoise.setAudiourlUs(getCompatibleString(row, "audiourl_us", "audiourlUs"));
        whiteNoise.setVideourlUs(getCompatibleString(row, "videourl_us", "videourlUs"));
        whiteNoise.setVideourlLdUs(getCompatibleString(row, "videourlld_us", "videourlLdUs"));
        whiteNoise.setAudiourlDe(getCompatibleString(row, "audiourl_de", "audiourlDe"));
        whiteNoise.setVideourlDe(getCompatibleString(row, "videourl_de", "videourlDe"));
        whiteNoise.setVideourlLdDe(getCompatibleString(row, "videourlld_de", "videourlLdDe"));
        whiteNoise.setColor(EntityMapper.getString(row, "color"));
        whiteNoise.setContextText(getCompatibleContextText(row));
        whiteNoise.setCreateAt(EntityMapper.getOffsetDateTime(row, "createAt"));
        whiteNoise.setUpdateAt(EntityMapper.getOffsetDateTime(row, "updateAt"));
        return whiteNoise;
    }

    private Map<String, Object> whiteNoiseToMap(WhiteNoise whiteNoise) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "videoId", whiteNoise.getVideoId());
        EntityMapper.putIfNotNull(data, "image", whiteNoise.getImage());
        EntityMapper.putIfNotNull(data, "audiourl", whiteNoise.getAudiourl());
        EntityMapper.putIfNotNull(data, "videourl", whiteNoise.getVideourl());
        EntityMapper.putIfNotNull(data, "videourlld", whiteNoise.getVideourlLd());
        EntityMapper.putIfNotNull(data, "audiourl_sg", whiteNoise.getAudiourlSg());
        EntityMapper.putIfNotNull(data, "videourl_sg", whiteNoise.getVideourlSg());
        EntityMapper.putIfNotNull(data, "videourlld_sg", whiteNoise.getVideourlLdSg());
        EntityMapper.putIfNotNull(data, "audiourl_us", whiteNoise.getAudiourlUs());
        EntityMapper.putIfNotNull(data, "videourl_us", whiteNoise.getVideourlUs());
        EntityMapper.putIfNotNull(data, "videourlld_us", whiteNoise.getVideourlLdUs());
        EntityMapper.putIfNotNull(data, "audiourl_de", whiteNoise.getAudiourlDe());
        EntityMapper.putIfNotNull(data, "videourl_de", whiteNoise.getVideourlDe());
        EntityMapper.putIfNotNull(data, "videourlld_de", whiteNoise.getVideourlLdDe());
        EntityMapper.putIfNotNull(data, "color", whiteNoise.getColor());
        EntityMapper.putIfNotNull(data, "contexttext", whiteNoise.getContextText());
        EntityMapper.putIfNotNull(data, "createAt", whiteNoise.getCreateAt());
        EntityMapper.putIfNotNull(data, "updateAt", whiteNoise.getUpdateAt());
        return data;
    }

    private String getCompatibleVideourlLd(Map<String, Object> row) {
        String value = EntityMapper.getString(row, "videourlld");
        return value != null ? value : EntityMapper.getString(row, "videourlLd");
    }

    private String getCompatibleString(Map<String, Object> row, String primaryKey, String legacyKey) {
        String value = EntityMapper.getString(row, primaryKey);
        return value != null ? value : EntityMapper.getString(row, legacyKey);
    }

    private String getCompatibleContextText(Map<String, Object> row) {
        String value = EntityMapper.getString(row, "contexttext");
        return value != null ? value : EntityMapper.getString(row, "contextText");
    }

    public WhiteNoiseDto convertToDto(WhiteNoise whiteNoise) {
        if (whiteNoise == null) {
            return null;
        }
        return new WhiteNoiseDto(
            whiteNoise.getVideoId(),
            whiteNoise.getImage(),
            whiteNoise.getAudiourl(),
            whiteNoise.getVideourl(),
            whiteNoise.getVideourlLd(),
            whiteNoise.getAudiourlSg(),
            whiteNoise.getVideourlSg(),
            whiteNoise.getVideourlLdSg(),
            whiteNoise.getAudiourlUs(),
            whiteNoise.getVideourlUs(),
            whiteNoise.getVideourlLdUs(),
            whiteNoise.getAudiourlDe(),
            whiteNoise.getVideourlDe(),
            whiteNoise.getVideourlLdDe(),
            whiteNoise.getColor(),
            deserializeContextText(whiteNoise.getContextText()),
            whiteNoise.getCreateAt(),
            whiteNoise.getUpdateAt()
        );
    }

    public List<WhiteNoiseDto> convertToDtoList(List<WhiteNoise> whiteNoises) {
        if (whiteNoises == null) {
            return new ArrayList<>();
        }
        return whiteNoises.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    private String serializeContextText(Map<String, WhiteNoiseLanguageContentDto> contextText) {
        if (contextText == null || contextText.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(contextText);
        } catch (Exception e) {
            throw new RuntimeException("白噪音多语言文案序列化失败", e);
        }
    }

    private Map<String, WhiteNoiseLanguageContentDto> deserializeContextText(String contextText) {
        if (contextText == null || contextText.trim().isEmpty()) {
            return new java.util.LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(
                contextText,
                new TypeReference<Map<String, WhiteNoiseLanguageContentDto>>() {}
            );
        } catch (Exception e) {
            logger.warn("白噪音多语言文案解析失败，已返回空对象: {}", contextText, e);
            return new java.util.LinkedHashMap<>();
        }
    }

    private void applyRequestFields(WhiteNoise whiteNoise,
                                    String image,
                                    String audiourl,
                                    String videourl,
                                    String videourlLd,
                                    String audiourlSg,
                                    String videourlSg,
                                    String videourlLdSg,
                                    String audiourlUs,
                                    String videourlUs,
                                    String videourlLdUs,
                                    String audiourlDe,
                                    String videourlDe,
                                    String videourlLdDe,
                                    String color,
                                    Map<String, WhiteNoiseLanguageContentDto> contextText) {
        if (image != null) whiteNoise.setImage(image);
        if (audiourl != null) whiteNoise.setAudiourl(audiourl);
        if (videourl != null) whiteNoise.setVideourl(videourl);
        if (videourlLd != null) whiteNoise.setVideourlLd(videourlLd);
        if (audiourlSg != null) whiteNoise.setAudiourlSg(audiourlSg);
        if (videourlSg != null) whiteNoise.setVideourlSg(videourlSg);
        if (videourlLdSg != null) whiteNoise.setVideourlLdSg(videourlLdSg);
        if (audiourlUs != null) whiteNoise.setAudiourlUs(audiourlUs);
        if (videourlUs != null) whiteNoise.setVideourlUs(videourlUs);
        if (videourlLdUs != null) whiteNoise.setVideourlLdUs(videourlLdUs);
        if (audiourlDe != null) whiteNoise.setAudiourlDe(audiourlDe);
        if (videourlDe != null) whiteNoise.setVideourlDe(videourlDe);
        if (videourlLdDe != null) whiteNoise.setVideourlLdDe(videourlLdDe);
        if (color != null) whiteNoise.setColor(color);
        if (contextText != null) whiteNoise.setContextText(serializeContextText(contextText));
    }
}
