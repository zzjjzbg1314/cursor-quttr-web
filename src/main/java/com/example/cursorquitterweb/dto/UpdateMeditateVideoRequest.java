package com.example.cursorquitterweb.dto;

import javax.validation.constraints.Size;
import java.util.List;

/**
 * 更新冥想视频请求DTO
 */
public class UpdateMeditateVideoRequest {
    
    @Size(max = 255, message = "冥想视频标题长度不能超过255个字符")
    private String title;
    
    @Size(max = 255, message = "副标题长度不能超过255个字符")
    private String subtitle;
    
    @Size(max = 1000, message = "图片链接长度不能超过1000个字符")
    private String image;
    
    @Size(max = 1000, message = "视频链接长度不能超过1000个字符")
    private String videoUrl;
    
    @Size(max = 1000, message = "音频链接长度不能超过1000个字符")
    private String audioUrl;
    
    private List<String> meditateQuotes;
    
    @Size(max = 50, message = "颜色长度不能超过50个字符")
    private String color;
    
    public UpdateMeditateVideoRequest() {}
    
    public UpdateMeditateVideoRequest(String title, String subtitle, String image, String videoUrl, 
                                    String audioUrl, List<String> meditateQuotes, String color) {
        this.title = title;
        this.subtitle = subtitle;
        this.image = image;
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.meditateQuotes = meditateQuotes;
        this.color = color;
    }
    
    // Getters and Setters
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getSubtitle() {
        return subtitle;
    }
    
    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }
    
    public String getImage() {
        return image;
    }
    
    public void setImage(String image) {
        this.image = image;
    }
    
    public String getVideoUrl() {
        return videoUrl;
    }
    
    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
    
    public String getAudioUrl() {
        return audioUrl;
    }
    
    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }
    
    public List<String> getMeditateQuotes() {
        return meditateQuotes;
    }
    
    public void setMeditateQuotes(List<String> meditateQuotes) {
        this.meditateQuotes = meditateQuotes;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
    }
    
    @Override
    public String toString() {
        return "UpdateMeditateVideoRequest{" +
                "title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", image='" + image + '\'' +
                ", videoUrl='" + videoUrl + '\'' +
                ", audioUrl='" + audioUrl + '\'' +
                ", meditateQuotes=" + meditateQuotes +
                ", color='" + color + '\'' +
                '}';
    }
}
