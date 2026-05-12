package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import javax.validation.constraints.Size;

/**
 * 创建姐姐激励视频请求DTO
 */
public class CreateSisterMotivationVideoRequest {

    private Map<String, String> title;

    @Size(max = 1000, message = "图片链接长度不能超过1000个字符")
    private String image;

    @Size(max = 1000, message = "视频链接长度不能超过1000个字符")
    private String videourl;

    @Size(max = 1000, message = "低清视频链接长度不能超过1000个字符")
    private String videourlld;

    @JsonProperty("videourl_sg")
    @Size(max = 1000, message = "新加坡视频链接长度不能超过1000个字符")
    private String videourlSg;

    @JsonProperty("videourlld_sg")
    @Size(max = 1000, message = "新加坡低清视频链接长度不能超过1000个字符")
    private String videourlldSg;

    @JsonProperty("videourl_us")
    @Size(max = 1000, message = "美国视频链接长度不能超过1000个字符")
    private String videourlUs;

    @JsonProperty("videourlld_us")
    @Size(max = 1000, message = "美国低清视频链接长度不能超过1000个字符")
    private String videourlldUs;

    @JsonProperty("videourl_de")
    @Size(max = 1000, message = "德国视频链接长度不能超过1000个字符")
    private String videourlDe;

    @JsonProperty("videourlld_de")
    @Size(max = 1000, message = "德国低清视频链接长度不能超过1000个字符")
    private String videourlldDe;

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
}
