package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 发送系统消息请求DTO
 */
public class SendSystemMessageRequest {
    
    @NotNull(message = "频道ID不能为空")
    private Integer channelId;
    
    @NotBlank(message = "消息内容不能为空")
    private String message;
    
    @NotBlank(message = "发送者名称不能为空")
    private String senderName;
    
    @NotBlank(message = "发送者头像URL不能为空")
    private String senderAvatarUrl;
    
    private String senderPlanetName;
    
    // 构造函数
    public SendSystemMessageRequest() {}
    
    public SendSystemMessageRequest(Integer channelId, String message, String senderName, String senderAvatarUrl, String senderPlanetName) {
        this.channelId = channelId;
        this.message = message;
        this.senderName = senderName;
        this.senderAvatarUrl = senderAvatarUrl;
        this.senderPlanetName = senderPlanetName;
    }
    
    // Getter和Setter方法
    public Integer getChannelId() {
        return channelId;
    }
    
    public void setChannelId(Integer channelId) {
        this.channelId = channelId;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getSenderName() {
        return senderName;
    }
    
    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }
    
    public String getSenderAvatarUrl() {
        return senderAvatarUrl;
    }
    
    public void setSenderAvatarUrl(String senderAvatarUrl) {
        this.senderAvatarUrl = senderAvatarUrl;
    }
    
    public String getSenderPlanetName() {
        return senderPlanetName;
    }
    
    public void setSenderPlanetName(String senderPlanetName) {
        this.senderPlanetName = senderPlanetName;
    }
    
    @Override
    public String toString() {
        return "SendSystemMessageRequest{" +
                "channelId=" + channelId +
                ", message='" + message + '\'' +
                ", senderName='" + senderName + '\'' +
                ", senderAvatarUrl='" + senderAvatarUrl + '\'' +
                ", senderPlanetName='" + senderPlanetName + '\'' +
                '}';
    }
}

