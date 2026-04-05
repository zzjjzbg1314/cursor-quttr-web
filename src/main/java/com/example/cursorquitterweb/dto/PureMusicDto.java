package com.example.cursorquitterweb.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 纯音乐内容DTO
 */
public class PureMusicDto {

    private UUID videoId;
    private String image;
    private String audiourl;
    private String videourl;
    private String videourlLd;
    private String color;
    private Map<String, PureMusicLanguageContentDto> contextText;
    private OffsetDateTime createAt;
    private OffsetDateTime updateAt;

    public PureMusicDto() {
    }

    public PureMusicDto(UUID videoId, String image, String audiourl, String videourl, String videourlLd, String color, Map<String, PureMusicLanguageContentDto> contextText, OffsetDateTime createAt, OffsetDateTime updateAt) {
        this.videoId = videoId;
        this.image = image;
        this.audiourl = audiourl;
        this.videourl = videourl;
        this.videourlLd = videourlLd;
        this.color = color;
        this.contextText = contextText;
        this.createAt = createAt;
        this.updateAt = updateAt;
    }

    public UUID getVideoId() {
        return videoId;
    }

    public void setVideoId(UUID videoId) {
        this.videoId = videoId;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getAudiourl() {
        return audiourl;
    }

    public void setAudiourl(String audiourl) {
        this.audiourl = audiourl;
    }

    public String getVideourl() {
        return videourl;
    }

    public void setVideourl(String videourl) {
        this.videourl = videourl;
    }

    public String getVideourlLd() {
        return videourlLd;
    }

    public void setVideourlLd(String videourlLd) {
        this.videourlLd = videourlLd;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Map<String, PureMusicLanguageContentDto> getContextText() {
        return contextText;
    }

    public void setContextText(Map<String, PureMusicLanguageContentDto> contextText) {
        this.contextText = contextText;
    }

    public OffsetDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(OffsetDateTime createAt) {
        this.createAt = createAt;
    }

    public OffsetDateTime getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(OffsetDateTime updateAt) {
        this.updateAt = updateAt;
    }
}
