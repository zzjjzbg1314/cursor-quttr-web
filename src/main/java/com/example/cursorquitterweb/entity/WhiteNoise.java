package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 白噪音内容实体类
 * 对应数据库表: white_noise
 */
public class WhiteNoise {

    private UUID videoId;

    private String image;

    private String audiourl;

    private String videourl;

    private String videourlLd;

    private String color;

    // 多语言文案的 JSON 字符串，实际结构由 DTO 层解析
    private String contextText;

    private OffsetDateTime createAt;

    private OffsetDateTime updateAt;

    public WhiteNoise() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }

    public WhiteNoise(String image, String audiourl, String videourl, String videourlLd, String color, String contextText) {
        this();
        this.image = image;
        this.audiourl = audiourl;
        this.videourl = videourl;
        this.videourlLd = videourlLd;
        this.color = color;
        this.contextText = contextText;
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

    public String getContextText() {
        return contextText;
    }

    public void setContextText(String contextText) {
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

    public void preUpdate() {
        this.updateAt = OffsetDateTime.now();
    }

    @Override
    public String toString() {
        return "WhiteNoise{" +
                "videoId=" + videoId +
                ", image='" + image + '\'' +
                ", audiourl='" + audiourl + '\'' +
                ", videourl='" + videourl + '\'' +
                ", videourlLd='" + videourlLd + '\'' +
                ", color='" + color + '\'' +
                ", contextText='" + contextText + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
