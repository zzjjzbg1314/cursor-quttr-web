package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 纯音乐内容实体类
 * 对应数据库表: pure_music
 */
public class PureMusic {

    private UUID videoId;

    private String image;

    private String audiourl;

    private String videourl;

    private String videourlLd;

    private String audiourlSg;

    private String videourlSg;

    private String videourlLdSg;

    private String audiourlUs;

    private String videourlUs;

    private String videourlLdUs;

    private String audiourlDe;

    private String videourlDe;

    private String videourlLdDe;

    private String color;

    // 多语言文案的 JSON 字符串，实际结构由 DTO 层解析
    private String contextText;

    private OffsetDateTime createAt;

    private OffsetDateTime updateAt;

    public PureMusic() {
        this.createAt = OffsetDateTime.now();
        this.updateAt = OffsetDateTime.now();
    }

    public PureMusic(String image, String audiourl, String videourl, String videourlLd, String color, String contextText) {
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

    public String getAudiourlSg() {
        return audiourlSg;
    }

    public void setAudiourlSg(String audiourlSg) {
        this.audiourlSg = audiourlSg;
    }

    public String getVideourlSg() {
        return videourlSg;
    }

    public void setVideourlSg(String videourlSg) {
        this.videourlSg = videourlSg;
    }

    public String getVideourlLdSg() {
        return videourlLdSg;
    }

    public void setVideourlLdSg(String videourlLdSg) {
        this.videourlLdSg = videourlLdSg;
    }

    public String getAudiourlUs() {
        return audiourlUs;
    }

    public void setAudiourlUs(String audiourlUs) {
        this.audiourlUs = audiourlUs;
    }

    public String getVideourlUs() {
        return videourlUs;
    }

    public void setVideourlUs(String videourlUs) {
        this.videourlUs = videourlUs;
    }

    public String getVideourlLdUs() {
        return videourlLdUs;
    }

    public void setVideourlLdUs(String videourlLdUs) {
        this.videourlLdUs = videourlLdUs;
    }

    public String getAudiourlDe() {
        return audiourlDe;
    }

    public void setAudiourlDe(String audiourlDe) {
        this.audiourlDe = audiourlDe;
    }

    public String getVideourlDe() {
        return videourlDe;
    }

    public void setVideourlDe(String videourlDe) {
        this.videourlDe = videourlDe;
    }

    public String getVideourlLdDe() {
        return videourlLdDe;
    }

    public void setVideourlLdDe(String videourlLdDe) {
        this.videourlLdDe = videourlLdDe;
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
        return "PureMusic{" +
                "videoId=" + videoId +
                ", image='" + image + '\'' +
                ", audiourl='" + audiourl + '\'' +
                ", videourl='" + videourl + '\'' +
                ", videourlLd='" + videourlLd + '\'' +
                ", audiourlSg='" + audiourlSg + '\'' +
                ", videourlSg='" + videourlSg + '\'' +
                ", videourlLdSg='" + videourlLdSg + '\'' +
                ", audiourlUs='" + audiourlUs + '\'' +
                ", videourlUs='" + videourlUs + '\'' +
                ", videourlLdUs='" + videourlLdUs + '\'' +
                ", audiourlDe='" + audiourlDe + '\'' +
                ", videourlDe='" + videourlDe + '\'' +
                ", videourlLdDe='" + videourlLdDe + '\'' +
                ", color='" + color + '\'' +
                ", contextText='" + contextText + '\'' +
                ", createAt=" + createAt +
                ", updateAt=" + updateAt +
                '}';
    }
}
