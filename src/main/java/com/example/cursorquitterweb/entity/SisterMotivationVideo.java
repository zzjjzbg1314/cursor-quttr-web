package com.example.cursorquitterweb.entity;

import java.time.OffsetDateTime;

/**
 * 姐姐激励视频实体类
 * 对应数据库表: sister_motivation_videos
 */
public class SisterMotivationVideo {

    private String videoId;
    private String title;
    private String image;
    private String videourl;
    private String videourlld;
    private String videourlSg;
    private String videourlldSg;
    private String videourlUs;
    private String videourlldUs;
    private String videourlDe;
    private String videourlldDe;
    private OffsetDateTime updateAt;

    public SisterMotivationVideo() {
        this.updateAt = OffsetDateTime.now();
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getVideourl() {
        return videourl;
    }

    public void setVideourl(String videourl) {
        this.videourl = videourl;
    }

    public String getVideourlld() {
        return videourlld;
    }

    public void setVideourlld(String videourlld) {
        this.videourlld = videourlld;
    }

    public String getVideourlSg() {
        return videourlSg;
    }

    public void setVideourlSg(String videourlSg) {
        this.videourlSg = videourlSg;
    }

    public String getVideourlldSg() {
        return videourlldSg;
    }

    public void setVideourlldSg(String videourlldSg) {
        this.videourlldSg = videourlldSg;
    }

    public String getVideourlUs() {
        return videourlUs;
    }

    public void setVideourlUs(String videourlUs) {
        this.videourlUs = videourlUs;
    }

    public String getVideourlldUs() {
        return videourlldUs;
    }

    public void setVideourlldUs(String videourlldUs) {
        this.videourlldUs = videourlldUs;
    }

    public String getVideourlDe() {
        return videourlDe;
    }

    public void setVideourlDe(String videourlDe) {
        this.videourlDe = videourlDe;
    }

    public String getVideourlldDe() {
        return videourlldDe;
    }

    public void setVideourlldDe(String videourlldDe) {
        this.videourlldDe = videourlldDe;
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
}
