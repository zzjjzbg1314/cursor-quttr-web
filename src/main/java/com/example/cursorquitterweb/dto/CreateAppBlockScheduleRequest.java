package com.example.cursorquitterweb.dto;

/**
 * 创建应用屏蔽计划请求DTO
 */
public class CreateAppBlockScheduleRequest {
    
    private String title;
    private String subtitle;
    private String days;
    private String time;
    private String reason;
    private String image;
    
    public CreateAppBlockScheduleRequest() {}
    
    public CreateAppBlockScheduleRequest(String title, String subtitle, String days, String time, String reason, String image) {
        this.title = title;
        this.subtitle = subtitle;
        this.days = days;
        this.time = time;
        this.reason = reason;
        this.image = image;
    }
    
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
    
    public String getDays() {
        return days;
    }
    
    public void setDays(String days) {
        this.days = days;
    }
    
    public String getTime() {
        return time;
    }
    
    public void setTime(String time) {
        this.time = time;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getImage() {
        return image;
    }
    
    public void setImage(String image) {
        this.image = image;
    }
    
    @Override
    public String toString() {
        return "CreateAppBlockScheduleRequest{" +
                "title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                ", days='" + days + '\'' +
                ", time='" + time + '\'' +
                ", reason='" + reason + '\'' +
                ", image='" + image + '\'' +
                '}';
    }
}
