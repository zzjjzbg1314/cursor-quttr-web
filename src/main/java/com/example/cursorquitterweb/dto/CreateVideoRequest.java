package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 创建视频请求DTO
 */
public class CreateVideoRequest {
    
    @NotBlank(message = "视频标题不能为空")
    @Size(max = 255, message = "视频标题长度不能超过255个字符")
    private String title;
    
    @NotBlank(message = "播放链接不能为空")
    @Size(max = 1000, message = "播放链接长度不能超过1000个字符")
    private String playurl;
    
    @NotBlank(message = "海报链接不能为空")
    @Size(max = 1000, message = "海报链接长度不能超过1000个字符")
    private String posturl;
    
    public CreateVideoRequest() {}
    
    public CreateVideoRequest(String title, String playurl, String posturl) {
        this.title = title;
        this.playurl = playurl;
        this.posturl = posturl;
    }
    
    // Getters and Setters
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getPlayurl() {
        return playurl;
    }
    
    public void setPlayurl(String playurl) {
        this.playurl = playurl;
    }
    
    public String getPosturl() {
        return posturl;
    }
    
    public void setPosturl(String posturl) {
        this.posturl = posturl;
    }
    
    @Override
    public String toString() {
        return "CreateVideoRequest{" +
                "title='" + title + '\'' +
                ", playurl='" + playurl + '\'' +
                ", posturl='" + posturl + '\'' +
                '}';
    }
}
