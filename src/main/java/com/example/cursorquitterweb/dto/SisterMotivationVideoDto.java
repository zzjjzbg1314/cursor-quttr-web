package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 姐姐激励视频DTO
 */
public class SisterMotivationVideoDto {

    private String videoId;
    private Map<String, String> title;
    private String image;
    private String videourl;
    private String videourlld;
    @JsonProperty("videourl_sg")
    private String videourlSg;
    @JsonProperty("videourlld_sg")
    private String videourlldSg;
    @JsonProperty("videourl_us")
    private String videourlUs;
    @JsonProperty("videourlld_us")
    private String videourlldUs;
    @JsonProperty("videourl_de")
    private String videourlDe;
    @JsonProperty("videourlld_de")
    private String videourlldDe;
    private OffsetDateTime updateAt;

    public SisterMotivationVideoDto() {}

    public SisterMotivationVideoDto(String videoId, Map<String, String> title, String image, String videourl,
                                    String videourlld, String videourlSg, String videourlldSg,
                                    String videourlUs, String videourlldUs, String videourlDe,
                                    String videourlldDe, OffsetDateTime updateAt) {
        this.videoId = videoId;
        this.title = title;
        this.image = image;
        this.videourl = videourl;
        this.videourlld = videourlld;
        this.videourlSg = videourlSg;
        this.videourlldSg = videourlldSg;
        this.videourlUs = videourlUs;
        this.videourlldUs = videourlldUs;
        this.videourlDe = videourlDe;
        this.videourlldDe = videourlldDe;
        this.updateAt = updateAt;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public Map<String, String> getTitle() {
        return title;
    }

    public void setTitle(Map<String, String> title) {
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
}
