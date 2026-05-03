package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * 创建白噪音内容请求DTO
 */
public class CreateWhiteNoiseRequest {

    @Size(max = 500, message = "图片URL长度不能超过500个字符")
    private String image;

    @Size(max = 500, message = "音频URL长度不能超过500个字符")
    private String audiourl;

    @Size(max = 500, message = "视频URL长度不能超过500个字符")
    private String videourl;

    @Size(max = 500, message = "视频高清URL长度不能超过500个字符")
    private String videourlLd;

    @Size(max = 500, message = "新加坡音频URL长度不能超过500个字符")
    private String audiourlSg;

    @Size(max = 500, message = "新加坡视频原画质URL长度不能超过500个字符")
    private String videourlSg;

    @Size(max = 500, message = "新加坡视频标清URL长度不能超过500个字符")
    private String videourlLdSg;

    @Size(max = 500, message = "美国音频URL长度不能超过500个字符")
    private String audiourlUs;

    @Size(max = 500, message = "美国视频原画质URL长度不能超过500个字符")
    private String videourlUs;

    @Size(max = 500, message = "美国视频标清URL长度不能超过500个字符")
    private String videourlLdUs;

    @Size(max = 500, message = "德国音频URL长度不能超过500个字符")
    private String audiourlDe;

    @Size(max = 500, message = "德国视频原画质URL长度不能超过500个字符")
    private String videourlDe;

    @Size(max = 500, message = "德国视频标清URL长度不能超过500个字符")
    private String videourlLdDe;

    @Size(max = 20, message = "颜色长度不能超过20个字符")
    private String color;

    @NotEmpty(message = "多语言文案不能为空")
    @javax.validation.Valid
    private Map<String, WhiteNoiseLanguageContentDto> contextText;

    public CreateWhiteNoiseRequest() {
    }

    public CreateWhiteNoiseRequest(String image, String audiourl, String videourl, String videourlLd,
                                  String audiourlSg, String videourlSg, String videourlLdSg,
                                  String audiourlUs, String videourlUs, String videourlLdUs,
                                  String audiourlDe, String videourlDe, String videourlLdDe,
                                  String color, Map<String, WhiteNoiseLanguageContentDto> contextText) {
        this.image = image;
        this.audiourl = audiourl;
        this.videourl = videourl;
        this.videourlLd = videourlLd;
        this.audiourlSg = audiourlSg;
        this.videourlSg = videourlSg;
        this.videourlLdSg = videourlLdSg;
        this.audiourlUs = audiourlUs;
        this.videourlUs = videourlUs;
        this.videourlLdUs = videourlLdUs;
        this.audiourlDe = audiourlDe;
        this.videourlDe = videourlDe;
        this.videourlLdDe = videourlLdDe;
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

    public Map<String, WhiteNoiseLanguageContentDto> getContextText() {
        return contextText;
    }

    public void setContextText(Map<String, WhiteNoiseLanguageContentDto> contextText) {
        this.contextText = contextText;
    }

    @Override
    public String toString() {
        return "CreateWhiteNoiseRequest{" +
                "image='" + image + '\'' +
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
                '}';
    }
}
