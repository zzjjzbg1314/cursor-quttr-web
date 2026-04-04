package com.example.cursorquitterweb.dto;

import javax.validation.constraints.Size;
import java.util.Map;

/**
 * 更新白噪音内容请求DTO
 */
public class UpdateWhiteNoiseRequest {

    @Size(max = 500, message = "图片URL长度不能超过500个字符")
    private String image;

    @Size(max = 500, message = "音频URL长度不能超过500个字符")
    private String audiourl;

    @Size(max = 500, message = "视频URL长度不能超过500个字符")
    private String videourl;

    @Size(max = 500, message = "视频高清URL长度不能超过500个字符")
    private String videourlLd;

    @Size(max = 20, message = "颜色长度不能超过20个字符")
    private String color;

    @javax.validation.Valid
    private Map<String, WhiteNoiseLanguageContentDto> contextText;

    public UpdateWhiteNoiseRequest() {
    }

    public UpdateWhiteNoiseRequest(String image, String audiourl, String videourl, String videourlLd, String color, Map<String, WhiteNoiseLanguageContentDto> contextText) {
        this.image = image;
        this.audiourl = audiourl;
        this.videourl = videourl;
        this.videourlLd = videourlLd;
        this.color = color;
        this.contextText = contextText;
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

    public Map<String, WhiteNoiseLanguageContentDto> getContextText() {
        return contextText;
    }

    public void setContextText(Map<String, WhiteNoiseLanguageContentDto> contextText) {
        this.contextText = contextText;
    }

    @Override
    public String toString() {
        return "UpdateWhiteNoiseRequest{" +
                "image='" + image + '\'' +
                ", audiourl='" + audiourl + '\'' +
                ", videourl='" + videourl + '\'' +
                ", videourlLd='" + videourlLd + '\'' +
                ", color='" + color + '\'' +
                ", contextText='" + contextText + '\'' +
                '}';
    }
}
