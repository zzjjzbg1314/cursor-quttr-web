package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;

/**
 * 创建音乐请求DTO
 */
public class CreateMusicRequest {
    
    @NotBlank(message = "音乐标题不能为空")
    private String title;
    
    private String subtitle;
    private String time;
    private String image;
    private String videourl;
    private String audiourl;
    private String quotes;
    
    @NotBlank(message = "音乐作者不能为空")
    private String author;
    
    public CreateMusicRequest() {}
    
    public CreateMusicRequest(String title, String subtitle, String time, String image, 
                             String videourl, String audiourl, String quotes, String author) {
        this.title = title;
        this.subtitle = subtitle;
        this.time = time;
        this.image = image;
        this.videourl = videourl;
        this.audiourl = audiourl;
        this.quotes = quotes;
        this.author = author;
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
    
    public String getTime() {
        return time;
    }
    
    public void setTime(String time) {
        this.time = time;
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
    
    public String getAudiourl() {
        return audiourl;
    }
    
    public void setAudiourl(String audiourl) {
        this.audiourl = audiourl;
    }
    
    public String getQuotes() {
        return quotes;
    }
    
    public void setQuotes(String quotes) {
        this.quotes = quotes;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    @Override
    public String toString() {
        return "CreateMusicRequest{" +
                "title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", time='" + time + '\'' +
                ", image='" + image + '\'' +
                ", videourl='" + videourl + '\'' +
                ", audiourl='" + audiourl + '\'' +
                ", quotes='" + quotes + '\'' +
                ", author='" + author + '\'' +
                '}';
    }
}
